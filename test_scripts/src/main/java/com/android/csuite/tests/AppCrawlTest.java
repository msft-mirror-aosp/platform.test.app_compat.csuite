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
import com.android.csuite.core.AppCrawlTester;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.base.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppCrawlTest extends BaseHostJUnit4Test {
    private static final String COLLECT_APP_VERSION = "collect-app-version";
    private static final String COLLECT_GMS_VERSION = "collect-gms-version";
    private static final String RECORD_SCREEN = "record-screen";

    @Rule public TestLogData mLogData = new TestLogData();

    private ApkInstaller mApkInstaller;
    private AppCrawlTester mCrawler;

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Option(
            name = COLLECT_GMS_VERSION,
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    private boolean mCollectGmsVersion;

    @Option(
            name = "repack-apk",
            mandatory = false,
            description =
                    "Path to an apk file or a directory containing apk files of a single package "
                            + "to repack and install in Espresso mode")
    private File mRepackApk;

    @Option(
            name = "install-apk",
            mandatory = false,
            description =
                    "The path to an apk file or a directory of apk files to be installed on the"
                            + " device. In Ui-automator mode, this includes both the target apk to"
                            + " install and any dependencies. In Espresso mode this can include"
                            + " additional libraries or dependencies.")
    private final List<File> mInstallApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description =
                    "Arguments for the 'adb install-multiple' package installation command for"
                            + " UI-automator mode.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(name = "package-name", mandatory = true, description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "ui-automator-mode",
            mandatory = false,
            description =
                    "Run the crawler with UIAutomator mode. Apk option is not required in this"
                            + " mode.")
    private boolean mUiAutomatorMode = false;

    @Option(
            name = "robo-script-file",
            description = "A Roboscript file to be executed by the crawler.")
    private File mRoboscriptFile;

    // TODO(b/234512223): add support for contextual roboscript files

    @Option(
            name = "crawl-guidance-proto-file",
            description = "A CrawlGuidance file to be executed by the crawler.")
    private File mCrawlGuidanceProtoFile;

    @Before
    public void setUp() throws ApkInstaller.ApkInstallerException, IOException {
        mCrawler = AppCrawlTester.newInstance(mPackageName, getTestInformation(), mLogData);
        if (!mUiAutomatorMode) {
            setApkForEspressoMode();
        }
        mCrawler.setRecordScreen(mRecordScreen);
        mCrawler.setCollectGmsVersion(mCollectGmsVersion);
        mCrawler.setCollectAppVersion(mCollectAppVersion);
        mCrawler.setUiAutomatorMode(mUiAutomatorMode);
        mCrawler.setRoboscriptFile(toPathOrNull(mRoboscriptFile));
        mCrawler.setCrawlGuidanceProtoFile(toPathOrNull(mCrawlGuidanceProtoFile));

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        mApkInstaller.install(
                mInstallApkPaths.stream().map(File::toPath).collect(Collectors.toList()),
                mInstallArgs);
    }

    /** Helper method to fetch the path of optional File variables. */
    private static Path toPathOrNull(@Nullable File f) {
        return f == null ? null : f.toPath();
    }

    /**
     * For Espresso mode, checks that a path with the location of the apk to repackage was provided
     */
    private void setApkForEspressoMode() {
        Preconditions.checkNotNull(
                mRepackApk, "Apk file path is required when not running in UIAutomator mode");
        // set the root path of the target apk for Espresso mode
        mCrawler.setApkPath(mRepackApk.toPath());
    }

    @Test
    public void testAppCrash() throws DeviceNotAvailableException {
        mCrawler.startAndAssertAppNoCrash();
    }

    @After
    public void tearDown() throws DeviceNotAvailableException, ApkInstaller.ApkInstallerException {
        mApkInstaller.uninstallAllInstalledPackages();
        getDevice().uninstallPackage(mPackageName);
        mCrawler.cleanUp();
    }
}
