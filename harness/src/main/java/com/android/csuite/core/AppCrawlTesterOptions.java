/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tradefed.config.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A class for receiving and storing option values for the AppCrawlTester class. */
public class AppCrawlTesterOptions {

    public static final String OBJECT_TYPE = "APP_CRAWL_TESTER_OPTIONS";

    @Option(name = "record-screen", description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = "collect-app-version",
            description =
                    "Whether to collect package version information and store the information"
                            + " in test log files.")
    private boolean mCollectAppVersion;

    @Option(
            name = "collect-gms-version",
            description =
                    "Whether to collect GMS core version information and store the information"
                            + " in test log files.")
    private boolean mCollectGmsVersion;

    @Option(
            name = "repack-apk",
            mandatory = false,
            description =
                    "Path to an apk file or a directory containing apk files of a single"
                            + " package to repack and install in Espresso mode")
    private File mRepackApk;

    @Option(
            name = "install-apk",
            mandatory = false,
            description =
                    "The path to an apk file or a directory of apk files to be installed on the"
                            + " device. In Ui-automator mode, this includes both the target apk to"
                            + " install and any dependencies. In Espresso mode this can include"
                            + " additional libraries or dependencies.")
    private List<File> mInstallApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description =
                    "Arguments for the 'adb install-multiple' package installation command for"
                            + " UI-automator mode.")
    private List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "crawl-controller-endpoint",
            mandatory = false,
            description = "The crawl controller endpoint to target.")
    private String mCrawlControllerEndpoint;

    @Option(
            name = "ui-automator-mode",
            mandatory = false,
            description =
                    "Run the crawler with UIAutomator mode. Apk option is not required in this"
                            + " mode.")
    private boolean mUiAutomatorMode = false;

    @Option(
            name = "timeout-sec",
            mandatory = false,
            description = "The timeout for the crawl test.")
    private int mTimeoutSec = 60;

    @Option(
            name = "robo-script-file",
            description = "A Roboscript file to be executed by the crawler.")
    private File mRoboscriptFile;

    // TODO(b/234512223): add support for contextual roboscript files

    @Option(
            name = "crawl-guidance-proto-file",
            description = "A CrawlGuidance file to be executed by the crawler.")
    private File mCrawlGuidanceProtoFile;

    @Option(
            name = "login-config-dir",
            description =
                    "A directory containing Roboscript and CrawlGuidance files with login"
                            + " credentials that are passed to the crawler. There should be one"
                            + " config file per package name. If both Roboscript and CrawlGuidance"
                            + " files are present, only the Roboscript file will be used.")
    private File mLoginConfigDir;

    @Option(
            name = "save-apk-when",
            description = "When to save apk files to the test result artifacts.")
    private TestUtils.TakeEffectWhen mSaveApkWhen = TestUtils.TakeEffectWhen.NEVER;

    @Option(
            name = "grant-external-storage",
            mandatory = false,
            description = "After an apks are installed, grant MANAGE_EXTERNAL_STORAGE permissions.")
    private boolean mGrantExternalStoragePermission = false;

    /** Returns the config value for whether to record the screen. */
    public boolean isRecordScreen() {
        return mRecordScreen;
    }

    /** Sets whether to enable screen recording. */
    public AppCrawlTesterOptions setRecordScreen(boolean recordScreen) {
        this.mRecordScreen = recordScreen;
        return this;
    }

    /** Returns the config value for whether to collect app version information. */
    public boolean isCollectAppVersion() {
        return mCollectAppVersion;
    }

    /** Sets whether to enable app version collection. */
    public AppCrawlTesterOptions setCollectAppVersion(boolean collectAppVersion) {
        this.mCollectAppVersion = collectAppVersion;
        return this;
    }

    /** Returns the config value for whether to collect GMS version information. */
    public boolean isCollectGmsVersion() {
        return mCollectGmsVersion;
    }

    /** Sets whether to enable GMS version collection. */
    public AppCrawlTesterOptions setCollectGmsVersion(boolean collectGmsVersion) {
        this.mCollectGmsVersion = collectGmsVersion;
        return this;
    }

    /** Returns the config value for the repacked APK file path. */
    public File getRepackApk() {
        return mRepackApk;
    }

    /** Sets the repacked APK file path. */
    public AppCrawlTesterOptions setRepackApk(File repackApk) {
        this.mRepackApk = repackApk;
        return this;
    }

    /** Returns the config value for the list of APK paths for installation. */
    public List<File> getInstallApkPaths() {
        return mInstallApkPaths;
    }

    /** Sets the list of APK paths for installation. */
    public AppCrawlTesterOptions setInstallApkPaths(List<File> installApkPaths) {
        this.mInstallApkPaths = installApkPaths;
        return this;
    }

    /** Returns the config value for the list of installation arguments. */
    public List<String> getInstallArgs() {
        return mInstallArgs;
    }

    /** Sets the list of installation arguments. */
    public AppCrawlTesterOptions setInstallArgs(List<String> installArgs) {
        this.mInstallArgs = installArgs;
        return this;
    }

    /** Returns the config value for the crawl controller endpoint URL. */
    public String getCrawlControllerEndpoint() {
        return mCrawlControllerEndpoint;
    }

    /** Sets the crawl controller endpoint URL. */
    public AppCrawlTesterOptions setCrawlControllerEndpoint(String crawlControllerEndpoint) {
        this.mCrawlControllerEndpoint = crawlControllerEndpoint;
        return this;
    }

    /** Returns the config value for whether to enable UiAutomator mode. */
    public boolean isUiAutomatorMode() {
        return mUiAutomatorMode;
    }

    /** Sets whether to enable UiAutomator mode. */
    public AppCrawlTesterOptions setUiAutomatorMode(boolean uiAutomatorMode) {
        this.mUiAutomatorMode = uiAutomatorMode;
        return this;
    }

    /** Returns the config value for the timeout duration in seconds. */
    public int getTimeoutSec() {
        return mTimeoutSec;
    }

    /** Sets the timeout duration in seconds. */
    public AppCrawlTesterOptions setTimeoutSec(int timeoutSec) {
        this.mTimeoutSec = timeoutSec;
        return this;
    }

    /** Returns the config value for the Roboscript file path. */
    public File getRoboscriptFile() {
        return mRoboscriptFile;
    }

    /** Sets the Roboscript file path. */
    public AppCrawlTesterOptions setRoboscriptFile(File roboscriptFile) {
        this.mRoboscriptFile = roboscriptFile;
        return this;
    }

    /** Returns the config value for the crawl guidance proto file path. */
    public File getCrawlGuidanceProtoFile() {
        return mCrawlGuidanceProtoFile;
    }

    /** Sets the crawl guidance proto file path. */
    public AppCrawlTesterOptions setCrawlGuidanceProtoFile(File crawlGuidanceProtoFile) {
        this.mCrawlGuidanceProtoFile = crawlGuidanceProtoFile;
        return this;
    }

    /** Gets the config value of login config directory. */
    public File getLoginConfigDir() {
        return mLoginConfigDir;
    }

    /** Sets the login config directory. */
    public AppCrawlTesterOptions setLoginConfigDir(File loginConfigDir) {
        this.mLoginConfigDir = loginConfigDir;
        return this;
    }

    /** Gets the config value for when to save apks. */
    public TestUtils.TakeEffectWhen getSaveApkWhen() {
        return mSaveApkWhen;
    }

    /** Sets when to save the apks to test artifacts. */
    public AppCrawlTesterOptions setSaveApkWhen(TestUtils.TakeEffectWhen saveApkWhen) {
        this.mSaveApkWhen = saveApkWhen;
        return this;
    }

    /**
     * Gets the config value for whether to grant external storage permission to the subject package
     */
    public boolean isGrantExternalStoragePermission() {
        return mGrantExternalStoragePermission;
    }

    /** Sets whether to grant external storage permission to the subject package. */
    public AppCrawlTesterOptions setGrantExternalStoragePermission(
            boolean grantExternalStoragePermission) {
        this.mGrantExternalStoragePermission = grantExternalStoragePermission;
        return this;
    }
}
