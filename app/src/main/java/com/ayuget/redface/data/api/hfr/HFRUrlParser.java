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

package com.ayuget.redface.data.api.hfr;

import android.net.Uri;
import android.util.Log;

import com.ayuget.redface.data.api.MDEndpoints;
import com.ayuget.redface.data.api.MDLink;
import com.ayuget.redface.data.api.UrlParser;
import com.ayuget.redface.data.api.model.Category;
import com.ayuget.redface.data.state.CategoriesStore;
import com.ayuget.redface.ui.misc.PagePosition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

public class HFRUrlParser implements UrlParser {
    private static final String LOG_TAG = HFRUrlParser.class.getSimpleName();

    private static final String REWRITTEN_TOPIC_LIST_REGEX = "([^\\/]+)(?:\\/)(?:([^\\/]+)(?:\\/))?(?:liste_sujet-)(\\d+)(?:.*)";

    private static final String REWRITTEN_TOPIC_REGEX = "([^\\/]+)(?:\\/)(?:([^\\/]+)(?:\\/))?(?:[^\\_]+)(?:_)(\\d+)(?:_)(\\d+)(?:\\.htm)";

    private static final String STANDARD_TOPIC_REGEX = "(?:\\/forum2\\.php\\?)(.*)";

    private final Pattern rewrittenTopicListPattern;

    private final Pattern rewrittenTopicPattern;

    private final Pattern standardTopicPattern;

    private final String baseRewrittenUrlRegex;

    private final String baseStandardUrlRegex;

    private final MDEndpoints mdEndpoints;

    private final CategoriesStore categoriesStore;

    @Inject
    public HFRUrlParser(MDEndpoints mdEndpoints, CategoriesStore categoriesStore) {
        this.mdEndpoints = mdEndpoints;
        this.categoriesStore = categoriesStore;

        String baseRewrittenRegex = "(?:" + Pattern.quote(mdEndpoints.baseurl() + "/hfr/") + ")";
        baseRewrittenUrlRegex = Pattern.quote(mdEndpoints.baseurl()) + "/hfr/.*";
        String baseStandardRegex = "(?:" + Pattern.quote(mdEndpoints.baseurl()) + ")";
        baseStandardUrlRegex = Pattern.quote(mdEndpoints.baseurl()) + "/forum.*";

        rewrittenTopicListPattern = Pattern.compile(baseRewrittenRegex + REWRITTEN_TOPIC_LIST_REGEX);
        rewrittenTopicPattern = Pattern.compile(baseRewrittenRegex + REWRITTEN_TOPIC_REGEX);
        standardTopicPattern = Pattern.compile(baseStandardRegex + STANDARD_TOPIC_REGEX);
    }

    @Override
    public MDLink parseUrl(String url) {
        if (url.matches(baseRewrittenUrlRegex)) {
            return parseRewrittenUrl(url);
        }
        else if (url.matches(baseStandardUrlRegex)){
            return parseStandardUrl(url);
        }
        else {
            return MDLink.invalid();
        }
    }

    public MDLink parseRewrittenUrl(String url) {
        Log.d(LOG_TAG, String.format("Parsing rewritten topic URL : %s", url));

        // Split url and anchor
        String[] urlParts = url.split("#");
        url = urlParts[0];
        String anchor = urlParts.length >= 2 ? urlParts[1] : null;

        Matcher rewrittenTopicMatcher = rewrittenTopicPattern.matcher(url);

        if (rewrittenTopicMatcher.matches()) {
            boolean hasSubCat = rewrittenTopicMatcher.groupCount() == 4;
            int subcatOffset = hasSubCat ? 1 : 0;

            String categorySlug = rewrittenTopicMatcher.group(1);
            String subcategorySlug = hasSubCat ? rewrittenTopicMatcher.group(2) : null;
            int topicId = Integer.parseInt(rewrittenTopicMatcher.group(subcatOffset + 2));
            int pageNumber = Integer.parseInt(rewrittenTopicMatcher.group(subcatOffset + 3));

            Log.d(LOG_TAG, String.format("Rewritten topic URL : %s, category : %s, subcategory : %s, topicId : %d, pageNumber : %d", url, categorySlug, subcategorySlug, topicId, pageNumber));

            Category topicCategory = categoriesStore.getCategoryBySlug(categorySlug);

            if (topicCategory == null) {
                Log.e(LOG_TAG, String.format("Category '%s' is unknown", categorySlug));
                return MDLink.invalid();
            }
            else {
                Log.d(LOG_TAG, String.format("Link is for category '%s'", topicCategory.getName()));
                return MDLink.forTopic(topicCategory, topicId)
                        .atPage(pageNumber)
                        .atPost(parseAnchor(anchor))
                        .build();
            }
        }
        else {
            return MDLink.invalid();
        }
    }

    public MDLink parseStandardUrl(String url) {
        Log.d(LOG_TAG, String.format("Parsing standard topic URL : %s", url));

        Uri parsedUri = Uri.parse(url);

        String categoryId = parsedUri.getQueryParameter("cat");
        String pageNumber = parsedUri.getQueryParameter("page");
        String topicId = parsedUri.getQueryParameter("post");
        String anchor = parsedUri.getFragment();

        if (categoryId == null || pageNumber == null || topicId == null) {
            Log.e(LOG_TAG, "Invalid standard URL, category, page number or topic id not found");
            return MDLink.invalid();
        }

        try {
            Category topicCategory = categoriesStore.getCategoryById(Integer.parseInt(categoryId));
            if (topicCategory == null) {
                Log.e(LOG_TAG, String.format("Category with id '%s' is unknown", categoryId));
                return MDLink.invalid();
            } else {
                Log.d(LOG_TAG, String.format("Link is for category '%s'", topicCategory.getName()));
                return MDLink.forTopic(topicCategory, Integer.parseInt(topicId))
                        .atPage(Integer.parseInt(pageNumber))
                        .atPost(parseAnchor(anchor))
                        .build();
            }
        }
        catch (NumberFormatException e) {
            Log.e(LOG_TAG, "Error while parsing standard URL", e);
            return MDLink.invalid();
        }
    }

    public PagePosition parseAnchor(String anchor) {
        if (anchor == null || ! (anchor.length() > 1 && anchor.charAt(0) == 't')) {
            Log.e(LOG_TAG, String.format("Anchor '%s' is invalid", anchor));
            return new PagePosition(PagePosition.TOP);
        }
        else {
            return new PagePosition(Integer.valueOf(anchor.substring(1)));
        }
    }
}
