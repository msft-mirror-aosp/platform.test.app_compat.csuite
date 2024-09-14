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

package com.android.csuite.tests;

import com.android.csuite.core.ApkInstaller;
import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.AppCrawlTester;
import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.csuite.core.DeviceJUnit4ClassRunner;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppCrawlTest extends BaseHostJUnit4Test implements IConfigurationReceiver {
    @Deprecated private static final String COLLECT_APP_VERSION = "collect-app-version";
    @Deprecated private static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @Deprecated private static final String RECORD_SCREEN = "record-screen";
    @Deprecated private static final int DEFAULT_TIMEOUT_SEC = 60;

    @Rule public TestLogData mLogData = new TestLogData();
    private boolean mIsLastTestPass;
    private boolean mIsApkSaved = false;

    private ApkInstaller mApkInstaller;
    private AppCrawlTester mCrawler;
    private IConfiguration mConfiguration;

    @Deprecated
    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

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

    @Option(name = "package-name", mandatory = true, description = "Package name of testing app.")
    private String mPackageName;

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
    private int mTimeoutSec = DEFAULT_TIMEOUT_SEC;

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

    @Before
    public void setUp()
            throws ApkInstaller.ApkInstallerException, IOException, DeviceNotAvailableException {
        mIsLastTestPass = false;
        mCrawler =
                AppCrawlTester.newInstance(
                        mPackageName, getTestInformation(), mLogData, mConfiguration);
        if (mCrawlControllerEndpoint != null) {
            mCrawler.getOptions().setCrawlControllerEndpoint(mCrawlControllerEndpoint);
        }
        if (mRecordScreen) {
            mCrawler.getOptions().setRecordScreen(mRecordScreen);
        }
        if (mCollectGmsVersion) {
            mCrawler.getOptions().setCollectGmsVersion(mCollectGmsVersion);
        }
        if (mCollectAppVersion) {
            mCrawler.getOptions().setCollectAppVersion(mCollectAppVersion);
        }
        if (mUiAutomatorMode) {
            mCrawler.getOptions().setUiAutomatorMode(mUiAutomatorMode);
        }
        if (mRoboscriptFile != null) {
            mCrawler.getOptions().setRoboscriptFile(mRoboscriptFile);
        }
        if (mCrawlGuidanceProtoFile != null) {
            mCrawler.getOptions().setCrawlGuidanceProtoFile(mCrawlGuidanceProtoFile);
        }
        if (mLoginConfigDir != null) {
            mCrawler.getOptions().setLoginConfigDir(mLoginConfigDir);
        }
        if (mTimeoutSec != DEFAULT_TIMEOUT_SEC) {
            mCrawler.getOptions().setTimeoutSec(mTimeoutSec);
        }

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mApkInstaller.install(
                mCrawler.getOptions().getInstallApkPaths().stream()
                        .map(File::toPath)
                        .collect(Collectors.toList()),
                mCrawler.getOptions().getInstallArgs());

        mCrawler.runSetup();
    }

    @Test
    public void testAppCrash() throws DeviceNotAvailableException, CrawlerException {
        mCrawler.runTest();
        mIsLastTestPass = true;
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        if (!mIsApkSaved) {
            mIsApkSaved =
                    testUtils.saveApks(
                            mCrawler.getOptions().getSaveApkWhen(),
                            mIsLastTestPass,
                            mPackageName,
                            mCrawler.getOptions().getInstallApkPaths());
            if (mCrawler.getOptions().getRepackApk() != null) {
                mIsApkSaved &=
                        testUtils.saveApks(
                                mCrawler.getOptions().getSaveApkWhen(),
                                mIsLastTestPass,
                                mPackageName,
                                Arrays.asList(mCrawler.getOptions().getRepackApk()));
            }
        }

        try {
            mApkInstaller.uninstallAllInstalledPackages();
        } catch (ApkInstallerException e) {
            CLog.w("Uninstallation of installed apps failed during teardown: %s", e.getMessage());
        }
        if (!mCrawler.getOptions().isUiAutomatorMode()) {
            getDevice().uninstallPackage(mPackageName);
        }

        mCrawler.runTearDown();
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}
