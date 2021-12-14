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

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.MoreFiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A tester that interact with an app crawler during testing. */
public final class AppCrawlTester {
    @VisibleForTesting Path mOutput;
    private final RunUtilProvider mRunUtilProvider;
    private final TestInformation mTestInfo;
    private final String mPackageName;
    private final Path mApkRoot;
    private static final long COMMAND_TIMEOUT_MILLIS = 4 * 60 * 1000;

    /**
     * Creates an {@link AppCrawlTester} instance.
     *
     * @param testInfo TradeFed test information
     * @param apkRoot The root path for an apk or a directory that contains apk files for a package.
     * @param packageName The package name of the apk files.
     * @return an {@link AppCrawlTester} instance.
     */
    public static AppCrawlTester newInstance(
            Path apkRoot, String packageName, TestInformation testInfo) {
        return new AppCrawlTester(apkRoot, packageName, testInfo, () -> new RunUtil());
    }

    @VisibleForTesting
    AppCrawlTester(
            Path apkRoot,
            String packageName,
            TestInformation testInfo,
            RunUtilProvider runUtilProvider) {
        mRunUtilProvider = runUtilProvider;
        mApkRoot = apkRoot;
        mPackageName = packageName;
        mTestInfo = testInfo;
    }

    /** An exception class representing crawler test failures. */
    public static final class CrawlerException extends Exception {
        /**
         * Constructs a new {@link CrawlerException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        private CrawlerException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link CrawlerException} with a meaningful error message, and a cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the CrawlerException.
         */
        private CrawlerException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link CrawlerException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the CrawlerException.
         */
        private CrawlerException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Starts a crawler run on the configured app.
     *
     * @throws CrawlerException When the crawler was not set up correctly or the crawler run command
     *     failed.
     */
    public void start() throws CrawlerException {
        if (!AppCrawlTesterPreparer.isReady(mTestInfo)) {
            throw new CrawlerException(
                    "The "
                            + AppCrawlTesterPreparer.class.getName()
                            + " is not ready. Please check whether "
                            + AppCrawlTesterPreparer.class.getName()
                            + " was included in the test plan and completed successfully.");
        }

        if (mOutput != null) {
            throw new CrawlerException(
                    "The crawler has already run. Multiple runs in the same "
                            + AppCrawlTester.class.getName()
                            + " instance are not supported.");
        }

        try {
            mOutput = Files.createTempDirectory("crawler");
        } catch (IOException e) {
            throw new CrawlerException("Failed to create temp directory for output.", e);
        }

        List<Path> apks = getApks(mApkRoot);
        String[] command = createCrawlerRunCommand(mTestInfo, apks);

        CLog.d("Launching package: %s.", mPackageName);

        IRunUtil runUtil = mRunUtilProvider.get();

        runUtil.setEnvVariable(
                "GOOGLE_APPLICATION_CREDENTIALS",
                AppCrawlTesterPreparer.getCredentialPath(mTestInfo).toString());
        CommandResult res = runUtil.runTimedCmd(COMMAND_TIMEOUT_MILLIS, command);
        if (!res.getStatus().equals(CommandStatus.SUCCESS)) {
            throw new CrawlerException("Crawler command failed: " + res);
        }

        CLog.i("Completed crawling the package %s. Outputs: %s", mPackageName, res);
    }

    /**
     * Puts the zipped crawler output files into test output.
     *
     * @param baseTest The base test class for adding test artifacts.
     */
    public void collectOutputZip(AbstractCSuiteTest baseTest) {
        if (mOutput == null) {
            CLog.e("Output directory is not created yet. Skipping collecting output.");
            return;
        }

        // Compress the crawler output directory and add it to test outputs.
        try {
            File outputZip = ZipUtil.createZip(mOutput.toFile());
            baseTest.addTestArtifact(mPackageName + "-crawler_output", LogDataType.ZIP, outputZip);
        } catch (IOException e) {
            CLog.e("Failed to zip the output directory: " + e);
        }
    }

    /**
     * Generates a list of APK paths where the base.apk of split apk files are always on the first
     * index if exists.
     *
     * <p>If the apk path is a single apk, then the apk is returned. If the apk path is a directory
     * containing only one non-split apk file, the apk file is returned. If the apk path is a
     * directory containing split apk files for one package, then the list of apks are returned and
     * the base.apk sits on the first index. If the apk path does not contain any apk files, or
     * multiple apk files without base.apk, then an IOException is thrown.
     *
     * @return A list of APK paths.
     * @throws CrawlerException If failed to read the apk path or unexpected number of apk files are
     *     found under the path.
     */
    private static List<Path> getApks(Path root) throws CrawlerException {
        // The apk path points to a non-split apk file.
        if (Files.isRegularFile(root)) {
            if (!root.toString().endsWith(".apk")) {
                throw new CrawlerException(
                        "The file on the given apk path is not an apk file: " + root);
            }
            return List.of(root);
        }

        List<Path> apks;
        CLog.d("APK path = " + root);
        try (Stream<Path> fileTree = Files.walk(root)) {
            apks =
                    fileTree.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".apk"))
                            .collect(Collectors.toList());
        } catch (IOException e) {
            throw new CrawlerException("Failed to list apk files.", e);
        }

        if (apks.isEmpty()) {
            throw new CrawlerException("The apk directory does not contain any apk files");
        }

        // The apk path contains a single non-split apk or the base.apk of a split-apk.
        if (apks.size() == 1) {
            return apks;
        }

        if (apks.stream().map(path -> path.getParent().toString()).distinct().count() != 1) {
            throw new CrawlerException(
                    "Apk files are not all in the same folder: "
                            + Arrays.deepToString(apks.toArray(new Path[apks.size()])));
        }

        if (apks.stream().filter(path -> path.getFileName().toString().equals("base.apk")).count()
                == 0) {
            throw new CrawlerException(
                    "Multiple non-split apk files detected: "
                            + Arrays.deepToString(apks.toArray(new Path[apks.size()])));
        }

        Collections.sort(
                apks,
                (first, second) -> first.getFileName().toString().equals("base.apk") ? -1 : 0);

        return apks;
    }

    @VisibleForTesting
    String[] createCrawlerRunCommand(TestInformation testInfo, List<Path> apks) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.addAll(
                Arrays.asList(
                        "java",
                        "-jar",
                        AppCrawlTesterPreparer.getCrawlerBinPath(testInfo)
                                .resolve("crawl_launcher_deploy.jar")
                                .toString(),
                        "--android-sdk-path",
                        AppCrawlTesterPreparer.getSdkPath(testInfo).toString(),
                        "--device-serial-code",
                        testInfo.getDevice().getSerialNumber(),
                        "--output-dir",
                        mOutput.toString(),
                        "--key-store-file",
                        // Using the publicly known default file name of the debug keystore.
                        AppCrawlTesterPreparer.getCrawlerBinPath(testInfo)
                                .resolve("debug.keystore")
                                .toString(),
                        "--key-store-password",
                        // Using the publicly known default password of the debug keystore.
                        "android",
                        "--apk-file",
                        apks.get(0).toString()));

        for (int i = 1; i < apks.size(); i++) {
            cmd.add("--split-apk-files");
            cmd.add(apks.get(i).toString());
        }

        return cmd.toArray(new String[cmd.size()]);
    }

    /** Cleans up the crawler output directory. */
    public void cleanUp() {
        if (mOutput == null) {
            return;
        }

        try {
            MoreFiles.deleteRecursively(mOutput);
        } catch (IOException e) {
            CLog.e("Failed to clean up the crawler output directory: " + e);
        }
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }
}
