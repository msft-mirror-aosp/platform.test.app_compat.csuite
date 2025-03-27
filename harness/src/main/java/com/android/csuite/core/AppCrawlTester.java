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

import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DropboxEntryCrashDetector.DropboxEntry;
import com.android.csuite.core.TestUtils.RoboscriptSignal;
import com.android.csuite.core.TestUtils.TestUtilsException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A tester that interact with an app crawler during testing. */
public final class AppCrawlTester {
    @VisibleForTesting Path mOutput;
    private final RunUtilProvider mRunUtilProvider;
    private final TestUtils mTestUtils;
    private final ApkInstaller mApkInstaller;
    private final FileSystem mFileSystem;
    private DeviceTimestamp mScreenRecordStartTime;
    private final ExecutionStage mExecutionStage = new ExecutionStage();
    private boolean mNoThrowOnFailure = false;
    private AppCrawlTesterOptions mConfigOptions;

    /**
     * Creates an {@link AppCrawlTester} instance.
     *
     * @param testInformation The TradeFed test information.
     * @param testLogData The TradeFed test output receiver.
     * @return an {@link AppCrawlTester} instance.
     * @throws CrawlerException
     */
    public static AppCrawlTester newInstance(
            TestInformation testInformation, TestLogData testLogData) throws CrawlerException {
        TestUtils testUtils = TestUtils.getInstance(testInformation, testLogData);
        return new AppCrawlTester(
                testUtils,
                () -> new RunUtil(),
                FileSystems.getDefault(),
                ApkInstaller.getInstance(testUtils.getDeviceUtils().getITestDevice()));
    }

    @VisibleForTesting
    AppCrawlTester(
            TestUtils testUtils,
            RunUtilProvider runUtilProvider,
            FileSystem fileSystem,
            ApkInstaller apkInstaller)
            throws CrawlerException {
        mRunUtilProvider = runUtilProvider;
        mTestUtils = testUtils;
        mFileSystem = fileSystem;
        mApkInstaller = apkInstaller;
        loadConfigOptions();
    }

    /** Loads the config options for the app crawl tester. */
    private void loadConfigOptions() throws CrawlerException {
        if (mConfigOptions != null) {
            return;
        }
        mConfigOptions = AppCrawlTesterOptions.load(mTestUtils.getTestInformation(), mFileSystem);
    }

    /** An exception class representing crawler test failures. */
    public static final class CrawlerException extends Exception {
        /**
         * Constructs a new {@link CrawlerException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        CrawlerException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link CrawlerException} with a meaningful error message, and a cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the CrawlerException.
         */
        CrawlerException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link CrawlerException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the CrawlerException.
         */
        CrawlerException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Runs the setup, test, and teardown steps together.
     *
     * <p>Test won't run if setup failed, and teardown will always run.
     *
     * @throws DeviceNotAvailableException when the device is lost.
     * @throws CrawlerException when apk failed to install or unexpected happened.
     */
    public void run() throws DeviceNotAvailableException, CrawlerException {
        try {
            runSetup();
            if (!isSetupComplete()) {
                CLog.i("Skipping test run as setup failed.");
            }
            runTest();
        } finally {
            runTearDown();
        }
    }

    /**
     * Runs only the setup step of the crawl test.
     *
     * @throws DeviceNotAvailableException when the device is lost.
     * @throws CrawlerException when APK installation fails.
     */
    public void runSetup() throws DeviceNotAvailableException, CrawlerException {
        Preconditions.checkNotNull(
                mConfigOptions.getSubjectPackageName(), "Package name cannot be null");
        // For Espresso mode, checks that a path with the location of the apk to repackage was
        // provided
        if (mConfigOptions.isEspressoMode()) {
            Preconditions.checkNotNull(
                    mConfigOptions.getSubjectApkPath(),
                    "Subject apk path is required when not running in UIAutomator mode");
        }

        try {
            mApkInstaller.install(
                    mConfigOptions.getExtraApkPaths().stream()
                            .map(file -> mFileSystem.getPath(file.getPath()))
                            .collect(Collectors.toList()),
                    mConfigOptions.getExtraApkInstallArgs());
            File subjectApkPath = mConfigOptions.getSubjectApkPath();
            if (subjectApkPath != null) {
                mApkInstaller.install(
                        mFileSystem.getPath(subjectApkPath.getPath()),
                        mConfigOptions.getSubjectApkInstallArgs());
            }
        } catch (ApkInstallerException | IOException e) {
            if (!mNoThrowOnFailure) {
                throw new CrawlerException(e);
            }
            mExecutionStage.addFailureMessage(e.getMessage());
            return;
        }

        // Grant external storage permission
        if (mConfigOptions.isGrantExternalStoragePermission()) {
            mTestUtils
                    .getDeviceUtils()
                    .grantExternalStoragePermissions(mConfigOptions.getSubjectPackageName());
        }

        String[] unlockScreenCmd =
                new String[] {
                    "input keyevent KEYCODE_WAKEUP",
                    "input keyevent KEYCODE_WAKEUP",
                    "input keyevent KEYCODE_MENU"
                };
        for (String cmd : unlockScreenCmd) {
            mTestUtils.getDeviceUtils().getITestDevice().executeShellV2Command(cmd);
        }

        mExecutionStage.setSetupComplete(true);
    }

    /** Runs only the teardown step of the crawl test. */
    public void runTearDown() {
        List<File> apksToSave = new ArrayList<>(mConfigOptions.getExtraApkPaths());
        if (mConfigOptions.getSubjectApkPath() != null) {
            apksToSave.add(mConfigOptions.getSubjectApkPath());
        }
        mTestUtils.saveApks(
                mConfigOptions.getSaveApkWhen(),
                mExecutionStage.isTestPassed(),
                mConfigOptions.getSubjectPackageName(),
                apksToSave);

        try {
            mApkInstaller.uninstallAllInstalledPackages();
        } catch (ApkInstallerException e) {
            CLog.e("Uninstallation of installed apps failed during teardown: %s", e.getMessage());
        }
        if (mConfigOptions.isEspressoMode()) {
            try {
                mTestUtils
                        .getDeviceUtils()
                        .getITestDevice()
                        .uninstallPackage(mConfigOptions.getSubjectPackageName());
            } catch (DeviceNotAvailableException e) {
                CLog.e(
                        "Uninstallation of installed apps failed during teardown: %s",
                        e.getMessage());
            }
        }

        cleanUpOutputDir();
    }

    /**
     * Starts crawling the app and throw AssertionError if app crash is detected.
     *
     * @throws DeviceNotAvailableException when the device because unavailable.
     * @throws CrawlerException when unexpected happened during the execution.
     */
    public void runTest() throws DeviceNotAvailableException, CrawlerException {
        if (!mExecutionStage.isSetupComplete()) {
            throw new CrawlerException("Crawler setup has not run successfully.");
        }
        if (mExecutionStage.isTestExecuted()) {
            throw new CrawlerException(
                    "The crawler has already run. Multiple runs in the same "
                            + AppCrawlTester.class.getName()
                            + " instance are not supported.");
        }
        mExecutionStage.setTestExecuted(true);

        DeviceTimestamp startTime = mTestUtils.getDeviceUtils().currentTimeMillis();

        CrawlerException crawlerException = null;
        try {
            startCrawl();
        } catch (CrawlerException e) {
            crawlerException = e;
        }
        DeviceTimestamp endTime = mTestUtils.getDeviceUtils().currentTimeMillis();

        try {
            List<DropboxEntry> crashEntries =
                    mTestUtils
                            .getDeviceUtils()
                            .getCrashEntriesFromDropbox(
                                    mConfigOptions.getSubjectPackageName(), startTime, endTime);
            String dropboxCrashLog =
                    mTestUtils.compileTestFailureMessage(
                            mConfigOptions.getSubjectPackageName(),
                            crashEntries,
                            true,
                            mScreenRecordStartTime);

            if (!dropboxCrashLog.isBlank()) {
                // Put dropbox crash log on the top of the failure messages.
                mExecutionStage.addFailureMessage(dropboxCrashLog);
            }
        } catch (IOException e) {
            mExecutionStage.addFailureMessage(
                    "Error while getting dropbox crash log: " + e.getMessage());
        }

        if (crawlerException != null) {
            mExecutionStage.addFailureMessage(crawlerException.getMessage());
        }

        if (!isTestPassed() && !mNoThrowOnFailure) {
            Assert.fail(getFailureMessage());
        }
    }

    /**
     * Starts a crawler run on the configured app.
     *
     * @throws CrawlerException When the crawler was not set up correctly or the crawler run command
     *     failed.
     * @throws DeviceNotAvailableException When device because unavailable.
     */
    @VisibleForTesting
    void startCrawl() throws CrawlerException, DeviceNotAvailableException {
        if (!AppCrawlTesterHostPreparer.isReady(mTestUtils.getTestInformation())) {
            throw new CrawlerException(
                    "The "
                            + AppCrawlTesterHostPreparer.class.getName()
                            + " is not ready. Please check whether "
                            + AppCrawlTesterHostPreparer.class.getName()
                            + " was included in the test plan and completed successfully.");
        }

        try {
            mOutput = Files.createTempDirectory("crawler");
        } catch (IOException e) {
            throw new CrawlerException("Failed to create temp directory for output.", e);
        }

        IRunUtil runUtil = mRunUtilProvider.get();
        AtomicReference<String[]> command = new AtomicReference<>();
        AtomicReference<CommandResult> commandResult = new AtomicReference<>();

        Path bin =
                mFileSystem.getPath(
                        AppCrawlTesterHostPreparer.getCrawlerBinPath(
                                mTestUtils.getTestInformation()));
        boolean isUtpClient = false;
        if (Files.exists(bin.resolve("utp-cli-android_deploy.jar"))) {
            command.set(createUtpCrawlerRunCommand(mTestUtils.getTestInformation()));
            runUtil.setEnvVariable(
                    "ANDROID_SDK",
                    AppCrawlTesterHostPreparer.getSdkPath(mTestUtils.getTestInformation())
                            .toString());
            isUtpClient = true;
        } else if (Files.exists(bin.resolve("crawl_launcher_deploy.jar"))) {
            command.set(createCrawlerRunCommand(mTestUtils.getTestInformation()));
            runUtil.setEnvVariable(
                    "GOOGLE_APPLICATION_CREDENTIALS",
                    AppCrawlTesterHostPreparer.getCredentialPath(mTestUtils.getTestInformation())
                            .toString());
        } else {
            throw new CrawlerException(
                    "Crawler executable binaries not found in " + bin.toString());
        }

        if (mConfigOptions.isCollectGmsVersion()) {
            mTestUtils.collectGmsVersion(mConfigOptions.getSubjectPackageName());
        }

        // Minimum timeout 3 minutes plus crawl test timeout. In espresso mode, extend the timeout
        // for another 3 minutes for apk recompile and OBB upload
        long commandTimeout =
                3L * 60 * 1000
                        + mConfigOptions.getCrawlDurationSec() * 1000L
                        + (mConfigOptions.isEspressoMode() ? 3L * 60 * 1000 : 0);

        CLog.i(
                "Starting to crawl the package %s with command %s",
                mConfigOptions.getSubjectPackageName(), String.join(" ", command.get()));
        // TODO(yuexima): When the obb_file option is supported in espresso mode, the timeout need
        // to be extended.

        if (mConfigOptions.isRecordScreen()) {
            mTestUtils.collectScreenRecord(
                    () -> {
                        commandResult.set(runUtil.runTimedCmd(commandTimeout, command.get()));
                    },
                    mConfigOptions.getSubjectPackageName(),
                    deviceTime -> mScreenRecordStartTime = deviceTime);
        } else {
            commandResult.set(runUtil.runTimedCmd(commandTimeout, command.get()));
        }

        // Must be done after the crawler run because the app is installed by the crawler.
        if (mConfigOptions.isCollectAppVersion()) {
            mTestUtils.collectAppVersion(mConfigOptions.getSubjectPackageName());
        }

        collectOutputZip();
        collectCrawlStepScreenshots(isUtpClient);
        createCrawlerRoboscriptSignal(isUtpClient);

        if (!commandResult.get().getStatus().equals(CommandStatus.SUCCESS)
                || commandResult.get().getStdout().contains("Unknown options:")) {
            throw new CrawlerException("Crawler command failed: " + commandResult.get());
        }

        CLog.i(
                "Completed crawling the package %s. Outputs: %s",
                mConfigOptions.getSubjectPackageName(), commandResult.get());
    }

    /** Copies the step screenshots into test outputs for easier access. */
    private void collectCrawlStepScreenshots(boolean isUtpClient) {
        if (mOutput == null) {
            CLog.e("Output directory is not created yet. Skipping collecting step screenshots.");
            return;
        }

        Path subDir = getClientCrawlerOutputSubDir(isUtpClient);
        if (!Files.exists(subDir)) {
            CLog.e(
                    "The crawler output directory is not complete, skipping collecting step"
                            + " screenshots.");
            return;
        }

        try (Stream<Path> files = Files.list(subDir)) {
            files.filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .toLowerCase(Locale.getDefault())
                                            .endsWith(".png"))
                    .forEach(
                            path -> {
                                mTestUtils
                                        .getTestArtifactReceiver()
                                        .addTestArtifact(
                                                mConfigOptions.getSubjectPackageName()
                                                        + "-crawl_step_screenshot_"
                                                        + path.getFileName(),
                                                LogDataType.PNG,
                                                path.toFile());
                            });
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /**
     * Reads the crawler output and creates an artifact with the success signal for a Roboscript
     * that has been executed by the crawler.
     */
    private void createCrawlerRoboscriptSignal(boolean isUtpClient) {
        if (mOutput == null) {
            CLog.e("Output directory is not created yet. Skipping collecting crawler signal.");
            return;
        }

        Path subDir = getClientCrawlerOutputSubDir(isUtpClient);
        if (!Files.exists(subDir)) {
            CLog.e(
                    "The crawler output directory is not complete, skipping collecting crawler"
                            + " signal.");
            return;
        }

        try (Stream<Path> files = Files.list(subDir)) {
            Optional<Path> roboOutputFile =
                    files.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .toLowerCase(Locale.getDefault())
                                                    .endsWith("crawl_outputs.txt"))
                            .findFirst();
            if (roboOutputFile.isPresent()) {
                generateRoboscriptSignalFile(
                        roboOutputFile.get(), mConfigOptions.getSubjectPackageName());
            }
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /**
     * Generates an artifact text file with a name indicating whether the Roboscript was successful.
     *
     * @param roboOutputFile - the file containing the Robo crawler output.
     * @param packageName - the android package name of the app for which the signal file is being
     *     generated.
     */
    private void generateRoboscriptSignalFile(Path roboOutputFile, String packageName) {
        try {
            File signalFile =
                    Files.createTempFile(
                                    packageName
                                            + "_roboscript_"
                                            + getRoboscriptSignal(Optional.of(roboOutputFile))
                                                    .toString()
                                                    .toLowerCase(Locale.getDefault()),
                                    ".txt")
                            .toFile();
            mTestUtils
                    .getTestArtifactReceiver()
                    .addTestArtifact(signalFile.getName(), LogDataType.HOST_LOG, signalFile);
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /**
     * Computes whether the Robosript was successful based on the output file, and returns the
     * success signal.
     *
     * @param roboOutput
     * @return Roboscript success signal
     */
    public RoboscriptSignal getRoboscriptSignal(Optional<Path> roboOutput) {
        if (!roboOutput.isPresent()) {
            return RoboscriptSignal.UNKNOWN;
        }
        Pattern totalActionsPattern =
                Pattern.compile("(?:robo_script_execution(?:.|\\n)*)total_actions.\\s(\\d*)");
        Pattern successfulActionsPattern =
                Pattern.compile("(?:robo_script_execution(?:.|\\n)*)successful_actions.\\s(\\d*)");
        final String outputFile;
        try {
            outputFile =
                    String.join("", Files.readAllLines(roboOutput.get(), Charset.defaultCharset()));
        } catch (IOException e) {
            CLog.e(e);
            return RoboscriptSignal.UNKNOWN;
        }
        int totalActions = 0;
        int successfulActions = 0;
        Matcher mTotal = totalActionsPattern.matcher(outputFile);
        Matcher mSuccessful = successfulActionsPattern.matcher(outputFile);
        if (mTotal.find() && mSuccessful.find()) {
            totalActions = Integer.parseInt(mTotal.group(1));
            successfulActions = Integer.parseInt(mSuccessful.group(1));
            if (totalActions == 0) {
                return RoboscriptSignal.FAIL;
            }
            return successfulActions / totalActions < 1
                    ? RoboscriptSignal.FAIL
                    : RoboscriptSignal.SUCCESS;
        }
        return RoboscriptSignal.UNKNOWN;
    }

    /** Based on the type of Robo client, resolves the Path for its output directory. */
    private Path getClientCrawlerOutputSubDir(boolean isUtpClient) {
        return isUtpClient
                ? mOutput.resolve("output").resolve("artifacts")
                : mOutput.resolve("app_firebase_test_lab");
    }

    /** Puts the zipped crawler output files into test output. */
    private void collectOutputZip() {
        if (mOutput == null) {
            CLog.e("Output directory is not created yet. Skipping collecting output.");
            return;
        }

        // Compress the crawler output directory and add it to test outputs.
        try {
            File outputZip = ZipUtil.createZip(mOutput.toFile());
            mTestUtils
                    .getTestArtifactReceiver()
                    .addTestArtifact(
                            mConfigOptions.getSubjectPackageName() + "-crawler_output",
                            LogDataType.ZIP,
                            outputZip);
        } catch (IOException e) {
            CLog.e("Failed to zip the output directory: " + e);
        }
    }

    @VisibleForTesting
    String[] createUtpCrawlerRunCommand(TestInformation testInfo) throws CrawlerException {

        Path bin =
                mFileSystem.getPath(
                        AppCrawlTesterHostPreparer.getCrawlerBinPath(
                                mTestUtils.getTestInformation()));
        ArrayList<String> cmd = new ArrayList<>();
        cmd.addAll(
                Arrays.asList(
                        "java",
                        "-jar",
                        bin.resolve("utp-cli-android_deploy.jar").toString(),
                        "android",
                        "robo",
                        "--device-id",
                        testInfo.getDevice().getSerialNumber(),
                        "--app-id",
                        mConfigOptions.getSubjectPackageName(),
                        "--controller-endpoint",
                        "PROD",
                        "--utp-binaries-dir",
                        bin.toString(),
                        "--key-file",
                        AppCrawlTesterHostPreparer.getCredentialPath(
                                        mTestUtils.getTestInformation())
                                .toString(),
                        "--base-crawler-apk",
                        bin.resolve("crawler_app.apk").toString(),
                        "--stub-crawler-apk",
                        bin.resolve("crawler_stubapp_androidx.apk").toString(),
                        "--tmp-dir",
                        mOutput.toString()));

        if (mConfigOptions.getCrawlDurationSec() > 0) {
            cmd.add("--crawler-flag");
            cmd.add("crawlDurationSec=" + Integer.toString(mConfigOptions.getCrawlDurationSec()));
        }

        if (!mConfigOptions.isEspressoMode()) {
            cmd.addAll(Arrays.asList("--ui-automator-mode", "--app-installed-on-device"));
        } else {
            Preconditions.checkNotNull(
                    mConfigOptions.getSubjectApkPath(),
                    "Apk file path is required when not running in UIAutomator mode");

            try {
                TestUtils.listApks(
                                mFileSystem.getPath(mConfigOptions.getSubjectApkPath().toString()))
                        .forEach(
                                path -> {
                                    String nameLowercase =
                                            path.getFileName().toString().toLowerCase();
                                    if (nameLowercase
                                            .toLowerCase(Locale.getDefault())
                                            .endsWith(".apk")) {
                                        cmd.add("--apks-to-crawl");
                                        cmd.add(path.toString());
                                    } else if (nameLowercase
                                            .toLowerCase(Locale.getDefault())
                                            .endsWith(".obb")) {
                                        cmd.add("--files-to-push");
                                        cmd.add(
                                                String.format(
                                                        "%s=/sdcard/Android/obb/%s/%s",
                                                        path.toString(),
                                                        mConfigOptions.getSubjectPackageName(),
                                                        path.getFileName().toString()));
                                    } else {
                                        CLog.d("Skipping unrecognized file %s", path.toString());
                                    }
                                });
            } catch (TestUtilsException e) {
                throw new CrawlerException(e);
            }
        }

        if (mConfigOptions.getRoboscriptFile() != null) {
            Assert.assertTrue(
                    "Please provide a valid roboscript file.",
                    Files.isRegularFile(
                            mFileSystem.getPath(mConfigOptions.getRoboscriptFile().toString())));
            cmd.add("--crawler-asset");
            cmd.add("robo.script=" + mConfigOptions.getRoboscriptFile().toString());
        }

        if (mConfigOptions.getCrawlGuidanceProtoFile() != null) {
            Assert.assertTrue(
                    "Please provide a valid CrawlGuidance file.",
                    Files.isRegularFile(
                            mFileSystem.getPath(
                                    mConfigOptions.getCrawlGuidanceProtoFile().toString())));
            cmd.add("--crawl-guidance-proto-path");
            cmd.add(mConfigOptions.getCrawlGuidanceProtoFile().toString());
        }

        if (mConfigOptions.getLoginConfigDir() != null) {
            RoboLoginConfigProvider configProvider =
                    new RoboLoginConfigProvider(
                            mFileSystem.getPath(mConfigOptions.getLoginConfigDir().toString()));
            cmd.addAll(
                    configProvider
                            .findConfigFor(mConfigOptions.getSubjectPackageName(), true)
                            .getLoginArgs());
        }

        return cmd.toArray(new String[cmd.size()]);
    }

    @VisibleForTesting
    String[] createCrawlerRunCommand(TestInformation testInfo) throws CrawlerException {

        Path bin =
                mFileSystem.getPath(
                        AppCrawlTesterHostPreparer.getCrawlerBinPath(
                                mTestUtils.getTestInformation()));
        ArrayList<String> cmd = new ArrayList<>();
        cmd.addAll(
                Arrays.asList(
                        "java",
                        "-jar",
                        bin.resolve("crawl_launcher_deploy.jar").toString(),
                        "--android-sdk-path",
                        AppCrawlTesterHostPreparer.getSdkPath(testInfo).toString(),
                        "--device-serial-code",
                        testInfo.getDevice().getSerialNumber(),
                        "--output-dir",
                        mOutput.toString(),
                        "--key-store-file",
                        // Using the publicly known default file name of the debug keystore.
                        bin.resolve("debug.keystore").toString(),
                        "--key-store-password",
                        // Using the publicly known default password of the debug keystore.
                        "android"));

        if (mConfigOptions.getCrawlControllerEndpoint() != null
                && mConfigOptions.getCrawlControllerEndpoint().length() > 0) {
            cmd.addAll(Arrays.asList("--endpoint", mConfigOptions.getCrawlControllerEndpoint()));
        }

        if (!mConfigOptions.isEspressoMode()) {
            cmd.addAll(
                    Arrays.asList(
                            "--ui-automator-mode",
                            "--app-package-name",
                            mConfigOptions.getSubjectPackageName()));
        } else {
            Preconditions.checkNotNull(
                    mConfigOptions.getSubjectApkPath(),
                    "Apk file path is required when not running in UIAutomator mode");

            List<Path> apks;
            try {
                apks =
                        TestUtils.listApks(
                                        mFileSystem.getPath(
                                                mConfigOptions.getSubjectApkPath().toString()))
                                .stream()
                                .filter(
                                        path ->
                                                path.getFileName()
                                                        .toString()
                                                        .toLowerCase()
                                                        .toLowerCase(Locale.getDefault())
                                                        .endsWith(".apk"))
                                .collect(Collectors.toList());
            } catch (TestUtilsException e) {
                throw new CrawlerException(e);
            }

            cmd.add("--apk-file");
            cmd.add(apks.get(0).toString());

            for (int i = 1; i < apks.size(); i++) {
                cmd.add("--split-apk-files");
                cmd.add(apks.get(i).toString());
            }
        }

        if (mConfigOptions.getCrawlDurationSec() > 0) {
            cmd.add("--timeout-sec");
            cmd.add(Integer.toString(mConfigOptions.getCrawlDurationSec()));
        }

        if (mConfigOptions.getRoboscriptFile() != null) {
            Assert.assertTrue(
                    "Please provide a valid roboscript file.",
                    Files.isRegularFile(
                            mFileSystem.getPath(mConfigOptions.getRoboscriptFile().toString())));
            cmd.addAll(
                    Arrays.asList(
                            "--robo-script-file", mConfigOptions.getRoboscriptFile().toString()));
        }

        if (mConfigOptions.getCrawlGuidanceProtoFile() != null) {
            Assert.assertTrue(
                    "Please provide a valid CrawlGuidance file.",
                    Files.isRegularFile(
                            mFileSystem.getPath(
                                    mConfigOptions.getCrawlGuidanceProtoFile().toString())));
            cmd.addAll(
                    Arrays.asList(
                            "--text-guide-file",
                            mConfigOptions.getCrawlGuidanceProtoFile().toString()));
        }

        if (mConfigOptions.getLoginConfigDir() != null) {
            RoboLoginConfigProvider configProvider =
                    new RoboLoginConfigProvider(
                            mFileSystem.getPath(mConfigOptions.getLoginConfigDir().toString()));
            cmd.addAll(
                    configProvider
                            .findConfigFor(mConfigOptions.getSubjectPackageName(), false)
                            .getLoginArgs());
        }

        return cmd.toArray(new String[cmd.size()]);
    }

    /** Returns whether setup completed successfully. */
    public boolean isSetupComplete() {
        return mExecutionStage.isSetupComplete();
    }

    /** Returns whether the test passed. */
    public boolean isTestPassed() {
        return mExecutionStage.isTestPassed();
    }

    /** Returns the failure message. Will return empty string if no failure. */
    public String getFailureMessage() {
        return mExecutionStage.getFailureMessage();
    }

    /** Sets the crawler to not throw on failure. Failure includes setup and test failures. */
    public AppCrawlTester setNoThrowOnFailure(boolean noThrowOnFailure) {
        mNoThrowOnFailure = noThrowOnFailure;
        return this;
    }

    private void checkOptionSettable() {
        if (mExecutionStage.mIsSetupComplete) {
            throw new IllegalStateException("Changing options after test start is not allowed.");
        }
    }

    /** Sets the package name to crawl. */
    public AppCrawlTester setSubjectPackageName(String subjectPackageName) {
        checkOptionSettable();
        mConfigOptions.setSubjectPackageName(subjectPackageName);
        return this;
    }

    /** Sets whether to enable screen recording. */
    public AppCrawlTester setRecordScreen(boolean recordScreen) {
        checkOptionSettable();
        mConfigOptions.setRecordScreen(recordScreen);
        return this;
    }

    /** Sets whether to enable app version collection. */
    public AppCrawlTester setCollectAppVersion(boolean collectAppVersion) {
        checkOptionSettable();
        mConfigOptions.setCollectAppVersion(collectAppVersion);
        return this;
    }

    /** Sets whether to enable GMS version collection. */
    public AppCrawlTester setCollectGmsVersion(boolean collectGmsVersion) {
        checkOptionSettable();
        mConfigOptions.setCollectGmsVersion(collectGmsVersion);
        return this;
    }

    /** Sets the subject APK file path. */
    public AppCrawlTester setSubjectApkPath(File apkPath) {
        checkOptionSettable();
        mConfigOptions.setSubjectApkPath(apkPath);
        return this;
    }

    /** Sets the list of installation arguments for the subject APK. */
    public AppCrawlTester setSubjectApkInstallArgs(List<String> installArgs) {
        checkOptionSettable();
        mConfigOptions.setSubjectApkInstallArgs(installArgs);
        return this;
    }

    /** Sets the list of extra APK paths for installation before. */
    public AppCrawlTester setExtraApkPaths(List<File> apkPaths) {
        checkOptionSettable();
        mConfigOptions.setExtraApkPaths(apkPaths);
        return this;
    }

    /** Sets the list of installation arguments for extra APKs. */
    public AppCrawlTester setExtraApkInstallArgs(List<String> installArgs) {
        checkOptionSettable();
        mConfigOptions.setExtraApkInstallArgs(installArgs);
        return this;
    }

    /** Sets the crawl controller endpoint URL. */
    public AppCrawlTester setCrawlControllerEndpoint(String crawlControllerEndpoint) {
        checkOptionSettable();
        mConfigOptions.setCrawlControllerEndpoint(crawlControllerEndpoint);
        return this;
    }

    /** Sets whether to enable espresso mode. */
    public AppCrawlTester setEspressoMode(boolean espressoMode) {
        checkOptionSettable();
        mConfigOptions.setEspressoMode(espressoMode);
        return this;
    }

    /** Sets the crawler duration timeout in seconds. */
    public AppCrawlTester setCrawlDurationSec(int timeoutSec) {
        checkOptionSettable();
        mConfigOptions.setCrawlDurationSec(timeoutSec);
        return this;
    }

    /** Sets the Roboscript file path. */
    public AppCrawlTester setRoboscriptFile(File roboscriptFile) {
        checkOptionSettable();
        mConfigOptions.setRoboscriptFile(roboscriptFile);
        return this;
    }

    /** Sets the crawl guidance proto file path. */
    public AppCrawlTester setCrawlGuidanceProtoFile(File crawlGuidanceProtoFile) {
        checkOptionSettable();
        mConfigOptions.setCrawlGuidanceProtoFile(crawlGuidanceProtoFile);
        return this;
    }

    /** Sets the login config directory. */
    public AppCrawlTester setLoginConfigDir(File loginConfigDir) {
        checkOptionSettable();
        mConfigOptions.setLoginConfigDir(loginConfigDir);
        return this;
    }

    /** Sets when to save the apks to test artifacts. */
    public AppCrawlTester setSaveApkWhen(TestUtils.TakeEffectWhen saveApkWhen) {
        checkOptionSettable();
        mConfigOptions.setSaveApkWhen(saveApkWhen);
        return this;
    }

    /** Sets whether to grant external storage permission to the subject package. */
    public AppCrawlTester setGrantExternalStoragePermission(
            boolean grantExternalStoragePermission) {
        checkOptionSettable();
        mConfigOptions.setGrantExternalStoragePermission(grantExternalStoragePermission);
        return this;
    }

    private class ExecutionStage {
        private boolean mIsSetupComplete = false;
        private boolean mIsTestExecuted = false;
        private ArrayList<String> mFailureMessages = new ArrayList<>();

        private boolean isSetupComplete() {
            return mIsSetupComplete;
        }

        private void setSetupComplete(boolean isSetupComplete) {
            mIsSetupComplete = isSetupComplete;
        }

        private boolean isTestExecuted() {
            return mIsTestExecuted;
        }

        private void setTestExecuted(boolean misTestExecuted) {
            mIsTestExecuted = misTestExecuted;
        }

        private boolean isTestPassed() {
            return isTestExecuted() && getFailureMessage().isBlank();
        }

        private void addFailureMessage(String msg) {
            mFailureMessages.add(msg);
        }

        private String getFailureMessage() {
            return String.join(
                    "\n============\n",
                    mFailureMessages.toArray(new String[mFailureMessages.size()]));
        }
    }

    /** Cleans up the crawler output directory. */
    @VisibleForTesting
    void cleanUpOutputDir() {
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
