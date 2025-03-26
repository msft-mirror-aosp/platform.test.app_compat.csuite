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

import java.io.IOException;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WebviewAppCrawlTest extends BaseHostJUnit4Test implements IConfigurationReceiver {
    @Rule public TestLogData mLogData = new TestLogData();

    private WebviewUtils mWebviewUtils;
    private WebviewPackage mPreInstalledWebview;
    private ApkInstaller mApkInstaller;
    private AppCrawlTester mCrawler;
    private AppCrawlTester mCrawlerVerify;
    private IConfiguration mConfiguration;

    @Option(name = "webview-version-to-test", description = "Version of Webview to test.")
    private String mWebviewVersionToTest;

    @Option(
            name = "release-channel",
            description = "Release channel to fetch Webview from, i.e. stable.")
    private String mReleaseChannel;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for an app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 20000;

    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);
        Assert.assertTrue(
                "Either the --release-channel or --webview-version-to-test arguments "
                        + "must be used",
                mWebviewVersionToTest != null || mReleaseChannel != null);

        mCrawler = AppCrawlTester.newInstance(getTestInformation(), mLogData, mConfiguration);
        mCrawlerVerify = AppCrawlTester.newInstance(getTestInformation(), mLogData, mConfiguration);

        // Only save apk on the verification run.
        mCrawler.getOptions().setSaveApkWhen(TestUtils.TakeEffectWhen.NEVER);
        mCrawlerVerify.getOptions().setSaveApkWhen(TestUtils.TakeEffectWhen.ON_FAIL);
        // Only record screen on the webview run.
        mCrawler.getOptions().setRecordScreen(true);
        mCrawlerVerify.getOptions().setRecordScreen(false);

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mWebviewUtils = new WebviewUtils(getTestInformation());
        mPreInstalledWebview = mWebviewUtils.getCurrentWebviewPackage();

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

        mCrawler.runTearDown();
        mCrawlerVerify.runTearDown();
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}
