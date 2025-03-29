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

import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A class for receiving and storing option values for the AppCrawlTester class. */
public class AppCrawlTesterOptions implements ITargetPreparer, Serializable {

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

    @Option(name = "subject-package-name", description = "Package name of the app being crawled.")
    private String mSubjectPackageName;

    @Option(
            name = "subject-apk-path",
            description =
                    "The path to the apk files of the subject package being tested. Optional in"
                            + " ui-automator mode and required in Espresso mode")
    private File mSubjectApkPath;

    @Option(name = "subject-apk-install-arg", description = "Adb install arg for the subject apk.")
    private List<String> mSubjectApkInstallArgs = new ArrayList<>();

    @Option(
            name = "extra-apk-path",
            description =
                    "The paths to extra apks to be installed before test. Split apks of a single"
                            + " package should be included in one directory path.")
    private List<File> mExtraApkPaths = new ArrayList<>();

    @Option(name = "extra-apk-install-arg", description = "Adb install arg for extra apka.")
    private List<String> mExtraApkInstallArgs = new ArrayList<>();

    @Option(
            name = "crawl-controller-endpoint",
            description = "The crawl controller endpoint to target.")
    private String mCrawlControllerEndpoint;

    @Option(
            name = "espresso-mode",
            description =
                    "Run the crawler in Espresso mode. Subject APK path is required in this"
                            + " mode. This option is by default false.")
    private boolean mEspressoMode = false;

    @Option(
            name = "crawl-duration-sec",
            description = "The max duration timeout for the crawler in seconds.")
    private int mCrawlDurationSec = 60;

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
            description = "After an apks are installed, grant MANAGE_EXTERNAL_STORAGE permissions.")
    private boolean mGrantExternalStoragePermission = false;

    /** Returns the config value for the package name to crawl. */
    String getSubjectPackageName() {
        return mSubjectPackageName;
    }

    /** Sets the package name to crawl. */
    AppCrawlTesterOptions setSubjectPackageName(String subjectPackageName) {
        this.mSubjectPackageName = subjectPackageName;
        return this;
    }

    /** Returns the config value for whether to record the screen. */
    boolean isRecordScreen() {
        return mRecordScreen;
    }

    /** Sets whether to enable screen recording. */
    AppCrawlTesterOptions setRecordScreen(boolean recordScreen) {
        this.mRecordScreen = recordScreen;
        return this;
    }

    /** Returns the config value for whether to collect app version information. */
    boolean isCollectAppVersion() {
        return mCollectAppVersion;
    }

    /** Sets whether to enable app version collection. */
    AppCrawlTesterOptions setCollectAppVersion(boolean collectAppVersion) {
        this.mCollectAppVersion = collectAppVersion;
        return this;
    }

    /** Returns the config value for whether to collect GMS version information. */
    boolean isCollectGmsVersion() {
        return mCollectGmsVersion;
    }

    /** Sets whether to enable GMS version collection. */
    AppCrawlTesterOptions setCollectGmsVersion(boolean collectGmsVersion) {
        this.mCollectGmsVersion = collectGmsVersion;
        return this;
    }

    /** Returns the config value for the subject APK path. */
    File getSubjectApkPath() {
        return mSubjectApkPath;
    }

    /** Sets the subject APK path. */
    AppCrawlTesterOptions setSubjectApkPath(File subjectApkPath) {
        this.mSubjectApkPath = subjectApkPath;
        return this;
    }

    /** Returns the config value for the list of extra APK paths for installation. */
    List<File> getExtraApkPaths() {
        return mExtraApkPaths;
    }

    /** Sets the list of extra APK paths for installation before test. */
    AppCrawlTesterOptions setExtraApkPaths(List<File> extraApkPaths) {
        this.mExtraApkPaths = extraApkPaths;
        return this;
    }

    /** Returns the config value for the list of installation arguments for the subject APK. */
    List<String> getSubjectApkInstallArgs() {
        return mSubjectApkInstallArgs;
    }

    /** Sets the list of installation arguments for the subject APK. */
    AppCrawlTesterOptions setSubjectApkInstallArgs(List<String> subjectApkInstallArgs) {
        this.mSubjectApkInstallArgs = subjectApkInstallArgs;
        return this;
    }

    /** Returns the config value for the list of installation arguments for the extra APKs. */
    List<String> getExtraApkInstallArgs() {
        return mExtraApkInstallArgs;
    }

    /** Sets the list of installation arguments for the extra APKs. */
    AppCrawlTesterOptions setExtraApkInstallArgs(List<String> extraApkInstallArgs) {
        this.mExtraApkInstallArgs = extraApkInstallArgs;
        return this;
    }

    /** Returns the config value for the crawl controller endpoint URL. */
    String getCrawlControllerEndpoint() {
        return mCrawlControllerEndpoint;
    }

    /** Sets the crawl controller endpoint URL. */
    AppCrawlTesterOptions setCrawlControllerEndpoint(String crawlControllerEndpoint) {
        this.mCrawlControllerEndpoint = crawlControllerEndpoint;
        return this;
    }

    /** Returns the config value for whether to enable espresso mode. */
    boolean isEspressoMode() {
        return mEspressoMode;
    }

    /** Sets whether to enable espresso mode. */
    AppCrawlTesterOptions setEspressoMode(boolean espressoMode) {
        this.mEspressoMode = espressoMode;
        return this;
    }

    /** Returns the config value for the crawler duration timeout in seconds. */
    int getCrawlDurationSec() {
        return mCrawlDurationSec;
    }

    /** Sets the crawler duration timeout in seconds. */
    AppCrawlTesterOptions setCrawlDurationSec(int crawlDurationSec) {
        this.mCrawlDurationSec = crawlDurationSec;
        return this;
    }

    /** Returns the config value for the Roboscript file path. */
    File getRoboscriptFile() {
        return mRoboscriptFile;
    }

    /** Sets the Roboscript file path. */
    AppCrawlTesterOptions setRoboscriptFile(File roboscriptFile) {
        this.mRoboscriptFile = roboscriptFile;
        return this;
    }

    /** Returns the config value for the crawl guidance proto file path. */
    File getCrawlGuidanceProtoFile() {
        return mCrawlGuidanceProtoFile;
    }

    /** Sets the crawl guidance proto file path. */
    AppCrawlTesterOptions setCrawlGuidanceProtoFile(File crawlGuidanceProtoFile) {
        this.mCrawlGuidanceProtoFile = crawlGuidanceProtoFile;
        return this;
    }

    /** Gets the config value of login config directory. */
    File getLoginConfigDir() {
        return mLoginConfigDir;
    }

    /** Sets the login config directory. */
    AppCrawlTesterOptions setLoginConfigDir(File loginConfigDir) {
        this.mLoginConfigDir = loginConfigDir;
        return this;
    }

    /** Gets the config value for when to save apks. */
    TestUtils.TakeEffectWhen getSaveApkWhen() {
        return mSaveApkWhen;
    }

    /** Sets when to save the apks to test artifacts. */
    AppCrawlTesterOptions setSaveApkWhen(TestUtils.TakeEffectWhen saveApkWhen) {
        this.mSaveApkWhen = saveApkWhen;
        return this;
    }

    /**
     * Gets the config value for whether to grant external storage permission to the subject package
     */
    boolean isGrantExternalStoragePermission() {
        return mGrantExternalStoragePermission;
    }

    /** Sets whether to grant external storage permission to the subject package. */
    AppCrawlTesterOptions setGrantExternalStoragePermission(
            boolean grantExternalStoragePermission) {
        this.mGrantExternalStoragePermission = grantExternalStoragePermission;
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo) throws TargetSetupError {
        try {
            dump(testInfo, FileSystems.getDefault());
        } catch (IOException e) {
            throw new TargetSetupError(
                    "Failed to dump options", e, testInfo.getDevice().getDeviceDescriptor());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) {
        cleanUpDump(testInfo);
    }

    private void cleanUpDump(TestInformation testInfo) {
        String tmpPathStr = AppCrawlTesterHostPreparer.getTempDirPath(testInfo);
        if (tmpPathStr == null) {
            return;
        }
        Path objFile = Path.of(tmpPathStr).resolve(GetObjFileName(testInfo));
        if (!Files.exists(objFile)) {
            return;
        }
        try {
            Files.delete(objFile);
        } catch (IOException e2) {
            CLog.w("Failed to delete %s: %s", objFile, e2);
        }
    }

    @VisibleForTesting
    void dump(TestInformation testInfo, FileSystem fileSystem) throws IOException {
        String tmpPathStr = AppCrawlTesterHostPreparer.getTempDirPath(testInfo);
        Preconditions.checkNotNull(tmpPathStr, "Temp dir not found.");
        Preconditions.checkArgument(
                Files.exists(fileSystem.getPath(tmpPathStr)), "Temp dir not exist.");
        Path objFile = fileSystem.getPath(tmpPathStr).resolve(GetObjFileName(testInfo));
        Files.createFile(objFile);

        try (OutputStream fos = Files.newOutputStream(objFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(this);
        }
    }

    /** Loads the option object from test information. */
    public static AppCrawlTesterOptions load(TestInformation testInfo, FileSystem fileSystem)
            throws CrawlerException {
        if (!AppCrawlTesterHostPreparer.isReady(testInfo)) {
            throw new CrawlerException("");
        }
        String tmpPathStr = AppCrawlTesterHostPreparer.getTempDirPath(testInfo);
        Preconditions.checkNotNull(
                tmpPathStr, "Temp dir not found, crawl host preparer likely not run.");
        Path dump = fileSystem.getPath(tmpPathStr).resolve(GetObjFileName(testInfo));

        try {
            try (InputStream fis = Files.newInputStream(dump);
                    ObjectInputStream ois = new ObjectInputStream(fis)) {
                return (AppCrawlTesterOptions) ois.readObject();
            }
        } catch (ClassNotFoundException | IOException e) {
            throw new CrawlerException(e);
        }
    }

    private static String GetObjFileName(TestInformation testInfo) {
        return testInfo.hashCode() + ".dump";
    }
}
