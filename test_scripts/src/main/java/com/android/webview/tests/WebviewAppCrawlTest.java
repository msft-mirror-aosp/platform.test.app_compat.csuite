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

import com.android.csuite.core.ApkInstaller;
import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.AppCrawlTester;
import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.csuite.core.DeviceJUnit4ClassRunner;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
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
import java.util.stream.Collectors;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WebviewAppCrawlTest extends BaseHostJUnit4Test implements IConfigurationReceiver {
    @Rule public TestLogData mLogData = new TestLogData();

    @Deprecated private static final String COLLECT_APP_VERSION = "collect-app-version";
    @Deprecated private static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @Deprecated private static final int DEFAULT_TIMEOUT_SEC = 60;

    private WebviewUtils mWebviewUtils;
    private WebviewPackage mPreInstalledWebview;
    private ApkInstaller mApkInstaller;
    private AppCrawlTester mCrawler;
    private AppCrawlTester mCrawlerVerify;
    private IConfiguration mConfiguration;

    @Deprecated
    @Option(name = "record-screen", description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(name = "webview-version-to-test", description = "Version of Webview to test.")
    private String mWebviewVersionToTest;

    @Option(
            name = "release-channel",
            description = "Release channel to fetch Webview from, i.e. stable.")
    private String mReleaseChannel;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Deprecated
    @Option(
            name = "install-apk",
            description =
                    "The path to an apk file or a directory of apk files of a singe package to be"
                            + " installed on device. Can be repeated.")
    private List<File> mApkPaths = new ArrayList<>();

    @Deprecated
    @Option(
            name = "install-arg",
            description = "Arguments for the 'adb install-multiple' package installation command.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for an app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 20000;

    @Deprecated
    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Deprecated
    @Option(
            name = COLLECT_GMS_VERSION,
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
            name = "robo-script-file",
            description = "A Roboscript file to be executed by the crawler.")
    private File mRoboscriptFile;

    // TODO(b/234512223): add support for contextual roboscript files

    @Deprecated
    @Option(
            name = "crawl-guidance-proto-file",
            description = "A CrawlGuidance file to be executed by the crawler.")
    private File mCrawlGuidanceProtoFile;

    @Deprecated
    @Option(
            name = "timeout-sec",
            mandatory = false,
            description = "The timeout for the crawl test.")
    private int mTimeoutSec = DEFAULT_TIMEOUT_SEC;

    @Deprecated
    @Option(
            name = "save-apk-when",
            description = "When to save apk files to the test result artifacts.")
    private TestUtils.TakeEffectWhen mSaveApkWhen = TestUtils.TakeEffectWhen.NEVER;

    @Deprecated
    @Option(
            name = "login-config-dir",
            description =
                    "A directory containing Roboscript and CrawlGuidance files with login"
                        + " credentials that are passed to the crawler. There should be one config"
                        + " file per package name. If both Roboscript and CrawlGuidance files are"
                        + " present, only the Roboscript file will be used.")
    private File mLoginConfigDir;

    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);
        Assert.assertTrue(
                "Either the --release-channel or --webview-version-to-test arguments "
                        + "must be used",
                mWebviewVersionToTest != null || mReleaseChannel != null);

        mCrawler =
                AppCrawlTester.newInstance(
                        mPackageName, getTestInformation(), mLogData, mConfiguration);
        mCrawlerVerify =
                AppCrawlTester.newInstance(
                        mPackageName, getTestInformation(), mLogData, mConfiguration);

        setCrawlerOptions(mCrawler);
        setCrawlerOptions(mCrawlerVerify);

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mWebviewUtils = new WebviewUtils(getTestInformation());
        mPreInstalledWebview = mWebviewUtils.getCurrentWebviewPackage();

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mApkInstaller.install(
                mCrawler.getOptions().getInstallApkPaths().stream()
                        .map(File::toPath)
                        .collect(Collectors.toList()),
                mCrawler.getOptions().getInstallArgs());

        DeviceUtils.getInstance(getDevice()).freezeRotation();
        mWebviewUtils.printWebviewVersion();

        mCrawler.runSetup();
        mCrawlerVerify.runSetup();
    }

    @Test
    public void testAppCrawl()
            throws DeviceNotAvailableException, IOException, CrawlerException, JSONException {
        AssertionError lastError = null;
        WebviewPackage lastWebviewInstalled =
                mWebviewUtils.installWebview(mWebviewVersionToTest, mReleaseChannel);

        try {
            mCrawler.runTest();
        } catch (AssertionError e) {
            lastError = e;
        } finally {
            mWebviewUtils.uninstallWebview(lastWebviewInstalled, mPreInstalledWebview);
        }

        // If the app doesn't crash, complete the test.
        if (lastError == null) {
            return;
        }

        // If the app crashes, try the app with the original webview version that comes with the
        // device.
        try {
            mCrawlerVerify.runTest();
        } catch (AssertionError newError) {
            CLog.w(
                    "The app %s crashed both with and without the webview installation,"
                            + " ignoring the failure...",
                    mPackageName);
            return;
        }
        throw new AssertionError(
                String.format(
                        "Package %s crashed since webview version %s",
                        mPackageName, lastWebviewInstalled.getVersion()),
                lastError);
    }

    @After
    public void tearDown() throws DeviceNotAvailableException, ApkInstallerException {
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);
        testUtils.collectScreenshot(mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        deviceUtils.stopPackage(mPackageName);
        deviceUtils.unfreezeRotation();

        mApkInstaller.uninstallAllInstalledPackages();
        mWebviewUtils.printWebviewVersion();

        if (!mUiAutomatorMode) {
            getDevice().uninstallPackage(mPackageName);
        }

        mCrawler.runTearDown();
        mCrawlerVerify.runTearDown();
    }

    private void setCrawlerOptions(AppCrawlTester crawler) {
        if (mCrawlControllerEndpoint != null) {
            crawler.getOptions().setCrawlControllerEndpoint(mCrawlControllerEndpoint);
        }
        if (mRecordScreen) {
            crawler.getOptions().setRecordScreen(mRecordScreen);
        }
        if (mCollectGmsVersion) {
            crawler.getOptions().setCollectGmsVersion(mCollectGmsVersion);
        }
        if (mCollectAppVersion) {
            crawler.getOptions().setCollectAppVersion(mCollectAppVersion);
        }
        if (mUiAutomatorMode) {
            crawler.getOptions().setUiAutomatorMode(mUiAutomatorMode);
        }
        if (mRoboscriptFile != null) {
            crawler.getOptions().setRoboscriptFile(mRoboscriptFile);
        }
        if (mCrawlGuidanceProtoFile != null) {
            crawler.getOptions().setCrawlGuidanceProtoFile(mCrawlGuidanceProtoFile);
        }
        if (mLoginConfigDir != null) {
            crawler.getOptions().setLoginConfigDir(mLoginConfigDir);
        }
        if (mTimeoutSec != DEFAULT_TIMEOUT_SEC) {
            crawler.getOptions().setTimeoutSec(mTimeoutSec);
        }
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}
