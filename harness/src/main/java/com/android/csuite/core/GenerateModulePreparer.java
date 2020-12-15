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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A preparer for generating TradeFed test suite modules config files.
 *
 * <p>This preparer generates module config files into TradeFed's test directory at runtime using a
 * template. The entire test directory is delete before every run. As a result, there can only be
 * one instance executing at a given time.
 */
public final class GenerateModulePreparer implements ITargetPreparer {

    @VisibleForTesting static final String MODULE_FILE_EXTENSION = ".config";
    @VisibleForTesting static final String OPTION_TEMPLATE = "template";
    @VisibleForTesting static final String OPTION_PACKAGE = "package";
    private static final String TEMPLATE_PACKAGE_PATTERN = "\\{package\\}";

    @Option(
            name = OPTION_TEMPLATE,
            description = "Module config template resource path.",
            importance = Importance.ALWAYS)
    private String mTemplate;

    @Option(name = OPTION_PACKAGE, description = "App package names.")
    private final Set<String> mPackages = new HashSet<>();

    private final TestDirectoryProvider mTestDirectoryProvider;
    private final ResourceLoader mResourceLoader;
    private final FileSystem mFileSystem;
    private final List<Path> mGeneratedModules = new ArrayList<>();

    public GenerateModulePreparer() {
        this(FileSystems.getDefault());
    }

    private GenerateModulePreparer(FileSystem fileSystem) {
        this(
                fileSystem,
                new CompatibilityTestDirectoryProvider(fileSystem),
                new ClassResourceLoader());
    }

    @VisibleForTesting
    GenerateModulePreparer(
            FileSystem fileSystem,
            TestDirectoryProvider testDirectoryProvider,
            ResourceLoader resourceLoader) {
        mFileSystem = fileSystem;
        mTestDirectoryProvider = testDirectoryProvider;
        mResourceLoader = resourceLoader;
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        try {
            Path testsDir = mTestDirectoryProvider.get(testInfo);
            String templateContent = mResourceLoader.load(mTemplate);

            for (String packageName : mPackages) {
                validatePackageName(packageName);
                Path modulePath = testsDir.resolve(packageName + MODULE_FILE_EXTENSION);
                Files.write(
                        modulePath,
                        templateContent
                                .replaceAll(TEMPLATE_PACKAGE_PATTERN, packageName)
                                .getBytes());
                mGeneratedModules.add(modulePath);
            }
        } catch (IOException e) {
            throw new TargetSetupError("Failed to generate modules", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        mGeneratedModules.forEach(
                modulePath -> {
                    try {
                        Files.delete(modulePath);
                    } catch (IOException ioException) {
                        CLog.e("Failed to delete a generated module: " + modulePath, ioException);
                    }
                });
    }

    private static void validatePackageName(String packageName) throws TargetSetupError {
        if (packageName.isEmpty() || packageName.matches(".*" + TEMPLATE_PACKAGE_PATTERN + ".*")) {
            throw new TargetSetupError(
                    "Package name cannot be empty or contains package placeholder: "
                            + TEMPLATE_PACKAGE_PATTERN);
        }
    }

    @VisibleForTesting
    interface ResourceLoader {
        String load(String resourceName) throws IOException;
    }

    private static final class ClassResourceLoader implements ResourceLoader {
        @Override
        public String load(String resourceName) throws IOException {
            return Resources.toString(
                    getClass().getClassLoader().getResource(resourceName), StandardCharsets.UTF_8);
        }
    }

    @VisibleForTesting
    interface TestDirectoryProvider {
        Path get(TestInformation testInfo) throws IOException;
    }

    private static final class CompatibilityTestDirectoryProvider implements TestDirectoryProvider {
        private final FileSystem mFileSystem;

        private CompatibilityTestDirectoryProvider(FileSystem fileSystem) {
            mFileSystem = fileSystem;
        }

        @Override
        public Path get(TestInformation testInfo) throws IOException {
            return mFileSystem.getPath(
                    new CompatibilityBuildHelper(testInfo.getBuildInfo()).getTestsDir().getPath());
        }
    }
}
