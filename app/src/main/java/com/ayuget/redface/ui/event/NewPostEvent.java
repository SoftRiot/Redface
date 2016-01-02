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

package com.ayuget.redface.ui.event;

import java.util.Map;

/**
 * Created by punky on 30/12/15.
 */
public class NewPostEvent {
    private int page;
    private Map<Long, String> quotes;

    public NewPostEvent(int page, Map<Long, String> quotes) {
        this.page = page;
        this.quotes = quotes;
    }

    public int getPage() {
        return page;
    }

    public Map<Long, String> getQuotes() {
        return quotes;
    }
}
