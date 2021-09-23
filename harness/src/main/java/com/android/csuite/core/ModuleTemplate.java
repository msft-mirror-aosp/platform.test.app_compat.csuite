/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.csuite.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ModuleTemplate {
    private final String mTemplateContent;

    public static ModuleTemplate load(String template, ResourceLoader resourceLoader)
            throws IOException {
        return new ModuleTemplate(resourceLoader.load(template));
    }

    @VisibleForTesting
    ModuleTemplate(String templateContent) {
        mTemplateContent = templateContent;
    }

    public String substitute(Map<String, String> replacementPairs) {
        return replacementPairs.keySet().stream()
                .reduce(
                        mTemplateContent,
                        (res, placeholder) ->
                                res.replace(placeholder, replacementPairs.get(placeholder)));
    }

    public interface ResourceLoader {
        String load(String resourceName) throws IOException;
    }

    public static final class ClassResourceLoader implements ResourceLoader {
        @Override
        public String load(String resourceName) throws IOException {
            return Resources.toString(
                    getClass().getClassLoader().getResource(resourceName), StandardCharsets.UTF_8);
        }
    }
}
