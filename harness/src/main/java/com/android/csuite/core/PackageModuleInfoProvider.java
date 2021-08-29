/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.csuite.core.ModuleTemplate.ResourceLoader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A module info provider that accepts package names and files that contains package names. */
public final class PackageModuleInfoProvider implements ModuleInfoProvider {
    @VisibleForTesting static final String PACKAGE = "package";
    @VisibleForTesting static final String PACKAGE_PLACEHOLDER = "{package}";
    @VisibleForTesting static final String TEMPLATE = "template";

    @Option(
            name = TEMPLATE,
            description = "Module config template resource path.",
            importance = Importance.ALWAYS)
    private String mTemplate;

    @Option(name = PACKAGE, description = "App package names.")
    private final Set<String> mPackages = new HashSet<>();

    private final ResourceLoader mResourceLoader;

    public PackageModuleInfoProvider() {
        this(new ModuleTemplate.ClassResourceLoader());
    }

    @VisibleForTesting
    PackageModuleInfoProvider(ResourceLoader resourceLoader) {
        mResourceLoader = resourceLoader;
    }

    @Override
    public Stream<ModuleInfoProvider.ModuleInfo> get() throws IOException {
        ModuleTemplate moduleTemplate = ModuleTemplate.load(mTemplate, mResourceLoader);

        return mPackages.stream()
                .distinct()
                .map(
                        packageName ->
                                new ModuleInfoProvider.ModuleInfo(
                                        packageName,
                                        moduleTemplate.substitute(
                                                Map.of(PACKAGE_PLACEHOLDER, packageName))));
    }
}
