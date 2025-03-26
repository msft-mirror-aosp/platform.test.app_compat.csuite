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

package com.android.webview.tests;

import com.android.csuite.core.AppCrawlTester;
import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.webview.lib.WebviewPackage;
import com.android.webview.lib.WebviewUtils;

import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WebviewAppCrawlTest extends BaseHostJUnit4Test {
    @Rule public TestLogData mLogData = new TestLogData();

    private WebviewUtils mWebviewUtils;
    private WebviewPackage mPreInstalledWebview;
    private AppCrawlTester mCrawler;
    private AppCrawlTester mCrawlerVerify;

    @Option(name = "webview-version-to-test", description = "Version of Webview to test.")
    private String mWebviewVersionToTest;

    @Option(
            name = "release-channel",
            description = "Release channel to fetch Webview from, i.e. stable.")
    private String mReleaseChannel;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Before
    public void setUp() throws DeviceNotAvailableException, CrawlerException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);
        Assert.assertTrue(
                "Either the --release-channel or --webview-version-to-test arguments "
                        + "must be used",
                mWebviewVersionToTest != null || mReleaseChannel != null);

        // Only save apk on the verification run.
        // Only record screen on the webview run.
        mCrawler =
                AppCrawlTester.newInstance(getTestInformation(), mLogData)
                        .setSaveApkWhen(TestUtils.TakeEffectWhen.NEVER)
                        .setRecordScreen(true)
                        .setNoThrowOnFailure(true);
        mCrawlerVerify =
                AppCrawlTester.newInstance(getTestInformation(), mLogData)
                        .setSaveApkWhen(TestUtils.TakeEffectWhen.ON_PASS)
                        .setRecordScreen(false)
                        .setNoThrowOnFailure(true);

        mWebviewUtils = new WebviewUtils(getTestInformation());
        mPreInstalledWebview = mWebviewUtils.getCurrentWebviewPackage();

        DeviceUtils.getInstance(getDevice()).freezeRotation();
        mWebviewUtils.printWebviewVersion();
    }

    @Test
    public void testAppCrawl()
            throws DeviceNotAvailableException, IOException, CrawlerException, JSONException {
        WebviewPackage lastWebviewInstalled =
                mWebviewUtils.installWebview(mWebviewVersionToTest, mReleaseChannel);
        mCrawler.run();
        mWebviewUtils.uninstallWebview(lastWebviewInstalled, mPreInstalledWebview);

        // If the test doesn't fail, complete the test.
        if (mCrawler.isTestPassed()) {
            return;
        }

        // If the test fails, try the app with the original webview version that comes with the
        // device.
        mCrawlerVerify.run();
        if (!mCrawlerVerify.isTestPassed()) {
            CLog.w(
                    "Test on app %s failed both with and without the webview installation,"
                            + " ignoring the failure...",
                    mPackageName);
            return;
        }
        throw new AssertionError(
                String.format(
                        "Package %s crashed since webview version %s",
                        mPackageName, lastWebviewInstalled.getVersion()));
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        mWebviewUtils.printWebviewVersion();
    }

    @Deprecated
    @Option(name = "record-screen", description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Deprecated
    @Option(
            name = "collect-app-version",
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Deprecated
    @Option(
            name = "collect-gms-version",
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    private boolean mCollectGmsVersion;

    @Deprecated
    @Option(
            name = "repack-apk",
            mandatory = false,
            description =
                    "Path to an apk file or a directory containing apk files of a single package "
                            + "to repack and install in Espresso mode")
    private File mRepackApk;

    @Deprecated
    @Option(
            name = "install-apk",
            mandatory = false,
            description =
                    "The path to an apk file or a directory of apk files to be installed on the"
                            + " device. In Ui-automator mode, this includes both the target apk to"
                            + " install and any dependencies. In Espresso mode this can include"
                            + " additional libraries or dependencies.")
    private final List<File> mInstallApkPaths = new ArrayList<>();

    @Deprecated
    @Option(
            name = "install-arg",
            description =
                    "Arguments for the 'adb install-multiple' package installation command for"
                            + " UI-automator mode.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Deprecated
    @Option(
            name = "crawl-controller-endpoint",
            mandatory = false,
            description = "The crawl controller endpoint to target.")
    private String mCrawlControllerEndpoint;

    @Deprecated
    @Option(
            name = "ui-automator-mode",
            mandatory = false,
            description =
                    "Run the crawler with UIAutomator mode. Apk option is not required in this"
                            + " mode.")
    private boolean mUiAutomatorMode = false;

    @Deprecated
    @Option(
            name = "timeout-sec",
            mandatory = false,
            description = "The timeout for the crawl test.")
    private int mTimeoutSec = 60;

    @Deprecated
    @Option(
            name = "robo-script-file",
            description = "A Roboscript file to be executed by the crawler.")
    private File mRoboscriptFile;

    @Deprecated
    @Option(
            name = "crawl-guidance-proto-file",
            description = "A CrawlGuidance file to be executed by the crawler.")
    private File mCrawlGuidanceProtoFile;

    @Deprecated
    @Option(
            name = "login-config-dir",
            description =
                    "A directory containing Roboscript and CrawlGuidance files with login"
                        + " credentials that are passed to the crawler. There should be one config"
                        + " file per package name. If both Roboscript and CrawlGuidance files are"
                        + " present, only the Roboscript file will be used.")
    private File mLoginConfigDir;

    @Deprecated
    @Option(
            name = "save-apk-when",
            description = "When to save apk files to the test result artifacts.")
    private TestUtils.TakeEffectWhen mSaveApkWhen = TestUtils.TakeEffectWhen.NEVER;

    @Deprecated
    @Option(
            name = "grant-external-storage",
            mandatory = false,
            description = "After an apks are installed, grant MANAGE_EXTERNAL_STORAGE permissions.")
    private boolean mGrantExternalStoragePermission = false;

    /** Convert deprecated options to new options if set. */
    private void processDeprecatedOptions() {
        if (mRecordScreen) {
            mCrawler.setRecordScreen(mRecordScreen);
        }
        if (mCollectAppVersion) {
            mCrawler.setCollectAppVersion(mCollectAppVersion);
        }
        if (mCollectGmsVersion) {
            mCrawler.setCollectGmsVersion(mCollectGmsVersion);
        }
        if (mRepackApk != null) {
            mCrawler.setSubjectApkPath(mRepackApk);
        }
        if (!mInstallApkPaths.isEmpty()) {
            mCrawler.setExtraApkPaths(mInstallApkPaths);
        }
        if (!mInstallArgs.isEmpty()) {
            mCrawler.setExtraApkInstallArgs(mInstallArgs);
        }
        if (!mUiAutomatorMode) {
            mCrawler.setEspressoMode(true);
        }
        if (mTimeoutSec > 0) {
            mCrawler.setCrawlDurationSec(mTimeoutSec);
        }
        if (mRoboscriptFile != null) {
            mCrawler.setRoboscriptFile(mRoboscriptFile);
        }
        if (mCrawlGuidanceProtoFile != null) {
            mCrawler.setCrawlGuidanceProtoFile(mCrawlGuidanceProtoFile);
        }
        if (mLoginConfigDir != null) {
            mCrawler.setLoginConfigDir(mLoginConfigDir);
        }
        if (mSaveApkWhen != TestUtils.TakeEffectWhen.NEVER) {
            mCrawler.setSaveApkWhen(mSaveApkWhen);
        }
        if (mGrantExternalStoragePermission) {
            mCrawler.setGrantExternalStoragePermission(true);
        }
        if (mCrawlControllerEndpoint != null) {
            mCrawler.setCrawlControllerEndpoint(mCrawlControllerEndpoint);
        }
        if (mPackageName != null) {
            mCrawler.setSubjectPackageName(mPackageName);
        }
    }
}
