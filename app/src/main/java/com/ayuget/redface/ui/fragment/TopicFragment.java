/*
 * Copyright 2015 Ayuget
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ayuget.redface.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ayuget.redface.R;
import com.ayuget.redface.data.api.MDEndpoints;
import com.ayuget.redface.data.api.model.Topic;
import com.ayuget.redface.ui.UIConstants;
import com.ayuget.redface.ui.activity.MultiPaneActivity;
import com.ayuget.redface.ui.activity.WritePrivateMessageActivity;
import com.ayuget.redface.ui.adapter.TopicPageAdapter;
import com.ayuget.redface.ui.event.GoToPostEvent;
import com.ayuget.redface.ui.event.NewPostEvent;
import com.ayuget.redface.ui.event.PageLoadedEvent;
import com.ayuget.redface.ui.event.PageRefreshRequestEvent;
import com.ayuget.redface.ui.event.PageRefreshedEvent;
import com.ayuget.redface.ui.event.PageSelectedEvent;
import com.ayuget.redface.ui.event.ScrollToPostEvent;
import com.ayuget.redface.ui.event.TopicPageCountUpdatedEvent;
import com.ayuget.redface.ui.event.WritePrivateMessageEvent;
import com.ayuget.redface.ui.misc.PagePosition;
import com.ayuget.redface.ui.misc.SnackbarHelper;
import com.ayuget.redface.ui.misc.TopicPosition;
import com.ayuget.redface.ui.misc.UiUtils;
import com.ayuget.redface.util.JsExecutor;
import com.hannesdorfmann.fragmentargs.annotation.Arg;
import com.rengwuxian.materialedittext.MaterialEditText;
import com.squareup.otto.Subscribe;
import com.squareup.phrase.Phrase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

import butterknife.InjectView;

public class TopicFragment extends ToolbarFragment implements ViewPager.OnPageChangeListener {
    private static final String LOG_TAG = TopicFragment.class.getSimpleName();

    private static final String ARG_TOPIC_POSITIONS_STACK = "topicPositionsStack";

    private static final String ARG_TOPIC_QUOTED_MESSAGES = "topicQuotedMessages";

    private TopicPageAdapter topicPageAdapter;

    private MaterialEditText goToPageEditText;

    private ArrayList<TopicPosition> topicPositionsStack;

    private int previousViewPagerState = ViewPager.SCROLL_STATE_IDLE;

    private boolean userScrolledViewPager = false;

    /**
     * Map of currently quoted messages and their corresponding content,
     * used for multi-quote feature
     */
    private Map quotedMessages;

    private boolean actionModeIsActive = false;

    /**
     * Used to display a contextual action bar when multi-quote mode is enabled
     */
    private ActionMode quoteActionMode;

    @Inject
    MDEndpoints mdEndpoints;


    @InjectView(R.id.pager)
    ViewPager pager;

    @InjectView(R.id.titlestrip)
    PagerTabStrip pagerTitleStrip;

    /**
     * Topic currently displayed
     */
    @Arg
    Topic topic;

    /**
     * Page currently displayed in the viewPager
     */
    @Arg
    int currentPage;

    /**
     * Current page position
     */
    @Arg(required = false)
    PagePosition currentPagePosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (topicPageAdapter == null) {
            topicPageAdapter = new TopicPageAdapter(getChildFragmentManager(), topic, currentPage);
        }

        if (savedInstanceState != null) {
            topicPositionsStack = savedInstanceState.getParcelableArrayList(ARG_TOPIC_POSITIONS_STACK);
            quotedMessages = new LinkedHashMap<>((Map)savedInstanceState.getSerializable(ARG_TOPIC_QUOTED_MESSAGES));
        }

        if (topicPositionsStack == null) {
            topicPositionsStack = new ArrayList<>();
        }

        if (quotedMessages == null) {
            quotedMessages = new LinkedHashMap<>();
        }

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflateRootView(R.layout.fragment_topic, inflater, container);
        pagerTitleStrip.setDrawFullUnderline(false);
        pagerTitleStrip.setTabIndicatorColor(getResources().getColor(R.color.theme_primary));
        pager.setAdapter(topicPageAdapter);
        pager.setCurrentItem(currentPage - 1);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        pager.addOnPageChangeListener(this);

        if (quotedMessages.size() > 0) {
            updateMultiQuoteMode();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        pager.removeOnPageChangeListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelableArrayList(ARG_TOPIC_POSITIONS_STACK, topicPositionsStack);

        // don't know why, but putting directly quotedMessages as parameter leads to a compilation error...
        outState.putSerializable(ARG_TOPIC_QUOTED_MESSAGES, new LinkedHashMap<>(quotedMessages));
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // Ignore event
    }

    @Override
    public void onPageSelected(int position) {
        currentPage = position + 1;
        bus.post(new PageSelectedEvent(topic, currentPage));
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // Keep track of scroll state in order to detect if scrolling has been done
        // manually by the user, or programatically. It allows us to disable some automatic
        // scrolling behavior that can be annoying (and buggy) in some corner cases
        if (previousViewPagerState == ViewPager.SCROLL_STATE_DRAGGING && state == ViewPager.SCROLL_STATE_SETTLING) {
            userScrolledViewPager = true;

            // Reset current page position because user triggered page change
            currentPagePosition = null;
        }
        else if (previousViewPagerState == ViewPager.SCROLL_STATE_SETTLING && state == ViewPager.SCROLL_STATE_IDLE) {
            userScrolledViewPager = false;
        }

        previousViewPagerState = state;
    }

    @Override
    public void onCreateOptionsMenu(Toolbar toolbar) {
        toolbar.inflateMenu(R.menu.menu_topic);
    }

    @Override
    public void onToolbarInitialized(Toolbar toolbar) {
        MultiPaneActivity hostActivity = (MultiPaneActivity) getActivity();

        if (! hostActivity.isTwoPaneMode()) {
            showUpButton();
        }

        toolbar.setTitle(topic.getSubject());
    }

    /**
     * Method to be invoked by child fragments when a batch operation on Posts has started.
     */
    public void onBatchOperation(boolean active) {
        MultiPaneActivity hostActivity = (MultiPaneActivity) getActivity();

        if (! hostActivity.isTwoPaneMode()) {
            if (active) {
                pagerTitleStrip.setBackgroundColor(UiUtils.getActionModeBackgroundColor(getActivity()));
            }
            else {
                pagerTitleStrip.setBackgroundColor(UiUtils.getRegularPagerTitleStripBackgroundColor(getActivity()));
            }
        }
    }

    /**
     * Event fired by the webview contained in the viewpager child fragments once the page has
     * been loaded. It allow us to set the page position only once the DOM is ready, otherwise
     * initial posiion is broken.
     */
    @Subscribe
    public void onTopicPageLoaded(PageLoadedEvent event) {
        Log.d(LOG_TAG, String.format("@%d -> Received topicPageLoaded event (topic='%s', page='%d'), current(topic='%s', page='%d', currentPagePosition='%s')", System.identityHashCode(this), event.getTopic().getSubject(), event.getPage(), topic.getSubject(), currentPage, currentPagePosition));
        if (event.getTopic().equals(topic) && event.getPage() == currentPage) {
            if (currentPagePosition != null && !userScrolledViewPager) {
                event.getTopicPageView().setPagePosition(currentPagePosition);
            }
        }
    }

    /**
     * Event fired by the data layer when we detect that new pages have been added
     * to a topic. It allows us to update the UI properly. This is completely mandatory
     * for "hot" topics, when a lot of content is added in a short period of time.
     */
    @Subscribe
    public void onTopicPageCountUpdated(TopicPageCountUpdatedEvent event) {
        if (event.getTopic() == topic) {
            topic.setPagesCount(event.getNewPageCount());
            topicPageAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe
    public void onWritePrivateMessage(WritePrivateMessageEvent event) {
        MultiPaneActivity hostActivity = (MultiPaneActivity) getActivity();

        if (hostActivity.canLaunchReplyActivity()) {
            hostActivity.setCanLaunchReplyActivity(false);

            Intent intent = new Intent(getActivity(), WritePrivateMessageActivity.class);
            intent.putExtra(UIConstants.ARG_PM_RECIPIENT, event.getRecipient());

            getActivity().startActivityForResult(intent, UIConstants.NEW_PM_REQUEST_CODE);
        }

    }

    @Subscribe
    public void onGoToPost(GoToPostEvent event) {
        topicPositionsStack.add(new TopicPosition(currentPage, currentPagePosition));

        currentPagePosition = event.getPagePosition();

        if (currentPage == event.getPage()) {
            event.getTopicPageView().setPagePosition(currentPagePosition);
        }
        else {
            currentPage = event.getPage();

            if (pager != null) {
                pager.setCurrentItem(currentPage - 1);
            }
        }
    }

    /**
     * Callback called by the activity when the back key has been pressed
     * @return true if event was consumed, false otherwise
     */
    public boolean onBackPressed() {
        if (topicPositionsStack.size() == 0) {
            return false;
        }
        else {
            TopicPosition topicPosition = topicPositionsStack.remove(topicPositionsStack.size() - 1);

            currentPagePosition = topicPosition.getPagePosition();

            if (currentPage == topicPosition.getPage()) {
                bus.post(new ScrollToPostEvent(topic, currentPage, currentPagePosition));
            }
            else {
                currentPage = topicPosition.getPage();
                pager.setCurrentItem(currentPage - 1);
            }

            return true;
        }
    }

    /**
     * Returns current page position
     */
    public PagePosition getCurrentPagePosition() {
        return currentPagePosition;
    }

    /**
     * Updates position of currently displayed topic page
     * @param position new position
     */
    public void setCurrentPagePosition(PagePosition position) {
        currentPagePosition = position;
    }

    /**
     * Returns the currently displayed topic
     */
    public Topic getTopic() {
        return topic;
    }

    /**
     * Returns the initial displayed page
     */
    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_refresh_topic:
                bus.post(new PageRefreshRequestEvent(topic));
                break;
            case R.id.action_go_to_first_page:
                pager.setCurrentItem(0);
                return true;
            case R.id.action_go_to_last_page:
                pager.setCurrentItem(topic.getPagesCount() - 1);
                return true;

            case R.id.action_go_to_specific_page:
                showGoToPageDialog();
                return true;

            case R.id.action_copy_link:
                UiUtils.copyToClipboard(getActivity(), mdEndpoints.topic(topic, currentPage));
                break;
            case R.id.action_share:
                UiUtils.shareText(getActivity(), mdEndpoints.topic(topic));
                break;
        }

        return super.onOptionsItemSelected(item);
    }



    /**
     * Clears internal navigation stack
     */
    @Override
    public void clearInternalStack() {
        topicPositionsStack.clear();
    }

    public void showGoToPageDialog() {
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .customView(R.layout.dialog_go_to_page, true)
                .positiveText(R.string.dialog_go_to_page_positive_text)
                .negativeText(android.R.string.cancel)
                .theme(themeManager.getMaterialDialogTheme())
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        try {
                            int pageNumber = Integer.valueOf(goToPageEditText.getText().toString());
                            pager.setCurrentItem(pageNumber - 1);
                        }
                        catch (NumberFormatException e) {
                            Log.e(LOG_TAG, String.format("Invalid page number entered : %s", goToPageEditText.getText().toString()), e);
                            SnackbarHelper.make(TopicFragment.this, R.string.invalid_page_number).show();
                        }
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                    }
                }).build();


        final View positiveAction = dialog.getActionButton(DialogAction.POSITIVE);
        goToPageEditText = (MaterialEditText) dialog.getCustomView().findViewById(R.id.page_number);

        goToPageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().length() > 0) {
                    try {
                        int pageNumber = Integer.valueOf(s.toString());
                        positiveAction.setEnabled(pageNumber >= 1 && pageNumber <= topic.getPagesCount());
                    }
                    catch (NumberFormatException e) {
                        positiveAction.setEnabled(false);
                    }
                }
                else {
                    positiveAction.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        dialog.show();
        positiveAction.setEnabled(false);
    }

    protected void addQuote(long postId, String quoteBBCode) {
        quotedMessages.put(postId, quoteBBCode);
        updateMultiQuoteMode();
    }

    protected void removeQuote(long postId) {
        quotedMessages.remove(postId);
        updateMultiQuoteMode();
    }

    protected void clearQuotes() {
        quotedMessages.clear();
        updateMultiQuoteMode();
    }

    protected Map<Long, String> getQuotes() {
        return quotedMessages;
    }

    private void updateMultiQuoteMode() {
        if (actionModeIsActive) {
            quoteActionMode.setTitle(Phrase.from(getContext(), R.string.quoted_messages_plural).put("count", quotedMessages.size()).format());
            return;
        }

        quoteActionMode = getActivity().startActionMode(new ActionMode.Callback() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {

                actionModeIsActive = true;

                if (quotedMessages.size() > 1) {
                    mode.setTitle(Phrase.from(getContext(), R.string.quoted_messages_plural).put("count", quotedMessages.size()).format());
                } else {
                    mode.setTitle(R.string.quoted_messages);
                }

                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.menu_multi_quote, menu);

                onBatchOperation(true);

                inflater = null; // Force GC

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_multiquote:
                        bus.post(new NewPostEvent(currentPage, getQuotes()));
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {

                actionModeIsActive = false;

                clearQuotes();
                onBatchOperation(false);
            }
        });
    }

    protected void stopMultiQuoteMode() {
        quoteActionMode.finish();
    }
}
