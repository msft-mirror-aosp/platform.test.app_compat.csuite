/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WebviewAppLaunchTest extends BaseHostJUnit4Test {
    @Rule public TestLogData mLogData = new TestLogData();

    private static final long COMMAND_TIMEOUT_MILLIS = 5 * 60 * 1000;

    private ApkInstaller mApkInstaller;
    private final List<WebviewPackage> mOrderedWebviews = new ArrayList<>();
    private WebviewPackage mPreInstalledWebview;
    private WebviewPackage mCurrentWebview;
    private GcloudCli mGcloudCli;

    @Option(name = "record-screen", description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = "webview-installer-tool",
            description = "Path to the webview installer executable.")
    private File mWebviewInstallerTool;

    @Option(name = "webview-version-to-test", description = "Version of Webview to test.")
    private List<String> mWebviewVersionToTest = new ArrayList<>();

    @Option(
            name = "webview-channel",
            description = "Release channel to fetch Webview from, i.e. stable.")
    private String mWebviewChannel;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "install-apk",
            description =
                    "The path to an apk file or a directory of apk files of a singe package to be"
                            + " installed on device. Can be repeated.")
    private List<File> mApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description = "Arguments for the 'adb install-multiple' package installation command.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for an app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 20000;

    @Option(
            name = "gcloud-cli-zip-archive",
            description = "Path to the google cli zip archive.",
            importance = Importance.ALWAYS)
    private File mGcloudCliZipArchive;

    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        mCurrentWebview = mPreInstalledWebview = getCurrentWebviewPackage();

        Assert.assertNotNull("Package name cannot be null", mPackageName);
        Assert.assertFalse(
                "--webview-version-to-test must be used when running this test",
                mWebviewVersionToTest.isEmpty());
        Assert.assertNotEquals(
                "Argument --webview-installer-tool must be used when "
                        + "using the --webview-version-to-test argument.",
                mWebviewInstallerTool,
                null);
        Assert.assertNotEquals(
                "Argument --gcloud-cli-zip must be used when "
                        + "using the --webview-version-to-test argument.",
                mGcloudCliZipArchive,
                null);

        mGcloudCli = GcloudCli.buildFromZipArchive(mGcloudCliZipArchive);
        mWebviewVersionToTest.sort(
                (version1, version2) -> {
                    String[] versionParts1 = version1.split("\\.");
                    String[] versionParts2 = version2.split("\\.");
                    for (int i = 0; i < 4; i++) {
                        int comparison =
                                Integer.compare(
                                        Integer.parseInt(versionParts1[i]),
                                        Integer.parseInt(versionParts2[i]));
                        if (comparison != 0) return -comparison;
                    }
                    return 0;
                });
        RunUtil.getDefault()
                .runTimedCmd(
                        COMMAND_TIMEOUT_MILLIS,
                        System.out,
                        System.out,
                        "chmod",
                        "755",
                        "-v",
                        mWebviewInstallerTool.getAbsolutePath());

        mApkInstaller = ApkInstaller.getInstance(getDevice());

        for (File apkPath : mApkPaths) {
            CLog.d("Installing " + apkPath);
            mApkInstaller.install(apkPath.toPath(), mInstallArgs);
        }

        DeviceUtils.getInstance(getDevice()).freezeRotation();

        printWebviewVersion(mPreInstalledWebview);
    }

    @Test
    public void testAppLaunch()
            throws DeviceNotAvailableException, InterruptedException, ApkInstallerException,
                    IOException {
        AssertionError lastError = null;
        // Try the latest webview version
        WebviewPackage lastWebviewInstalled;
        Iterator<String> webviewsToTestIterator = mWebviewVersionToTest.iterator();

        lastWebviewInstalled = installWebview(webviewsToTestIterator.next());

        try {
            assertAppLaunchNoCrash();
        } catch (AssertionError e) {
            lastError = e;
        } finally {
            uninstallWebview();
        }

        // If the app doesn't crash, complete the test.
        if (lastError == null) {
            return;
        }

        // If the app crashes, try the app with the original webview version that comes with the
        // device.
        try {
            assertAppLaunchNoCrash();
        } catch (AssertionError newError) {
            CLog.w(
                    "The app %s crashed both with and without the webview installation,"
                            + " ignoring the failure...",
                    mPackageName);
            return;
        }

        while (webviewsToTestIterator.hasNext()) {
            lastWebviewInstalled = installWebview(webviewsToTestIterator.next());
            try {
                assertAppLaunchNoCrash();
            } catch (AssertionError e) {
                lastError = e;
                continue;
            } finally {
                uninstallWebview();
            }
            break;
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
        printWebviewVersion();

        mGcloudCli.tearDown();
    }

    private void printWebviewVersion(WebviewPackage currentWebview)
            throws DeviceNotAvailableException {
        CLog.i("Current webview implementation: %s", currentWebview.getPackageName());
        CLog.i("Current webview version: %s", currentWebview.getVersion());
    }

    private void printWebviewVersion() throws DeviceNotAvailableException {
        WebviewPackage currentWebview = getCurrentWebviewPackage();
        printWebviewVersion(currentWebview);
    }

    private WebviewPackage installWebview(String webviewVersion)
            throws IOException, InterruptedException, DeviceNotAvailableException {
        // TODO(rmhasan): Remove the --non-next command line argument after
        // crbug.com/1002673 is resolved.
        List<String> fullCommandLineArgs =
                new ArrayList<>(
                        Arrays.asList(
                                mWebviewInstallerTool.getAbsolutePath(),
                                "--chrome-version",
                                webviewVersion,
                                "--non-next",
                                "--serial",
                                getDevice().getSerialNumber(),
                                "-vvv",
                                "--gsutil",
                                mGcloudCli.getGsutilExecutable().getAbsolutePath()));

        if (mWebviewChannel != null) {
            fullCommandLineArgs.addAll(Arrays.asList("-c", mWebviewChannel));
        }

        CommandResult installWebViewRes =
                mGcloudCli
                        .getRunUtil()
                        .runTimedCmd(
                                COMMAND_TIMEOUT_MILLIS,
                                System.out,
                                System.out,
                                fullCommandLineArgs.toArray(new String[0]));
        Assert.assertEquals(
                "The WebView installer tool failed to install WebView:\n"
                        + installWebViewRes.toString(),
                installWebViewRes.getStatus(),
                CommandStatus.SUCCESS);

        mCurrentWebview = getCurrentWebviewPackage();
        printWebviewVersion(mCurrentWebview);
        return mCurrentWebview;
    }

    private void uninstallWebview() throws DeviceNotAvailableException {
        Assert.assertNotEquals(
                "Test is attempting to uninstall the preinstalled WebView provider",
                mCurrentWebview,
                mPreInstalledWebview);
        updateWebviewImplementation(mPreInstalledWebview.getPackageName());
        getDevice().executeAdbCommand("uninstall", mCurrentWebview.getPackageName());
        mCurrentWebview = mPreInstalledWebview;
        printWebviewVersion(mCurrentWebview);
    }

    private void updateWebviewImplementation(String webviewPackageName)
            throws DeviceNotAvailableException {
        CommandResult res =
                getDevice()
                        .executeShellV2Command(
                                String.format(
                                        "cmd webviewupdate set-webview-implementation %s",
                                        webviewPackageName));
        Assert.assertEquals(
                "Failed to set webview update: " + res, res.getStatus(), CommandStatus.SUCCESS);
    }

    private WebviewPackage getCurrentWebviewPackage() throws DeviceNotAvailableException {
        String dumpsys = getDevice().executeShellCommand("dumpsys webviewupdate");
        return WebviewPackage.buildFromDumpsys(dumpsys);
    }

    private void assertAppLaunchNoCrash() throws DeviceNotAvailableException {
        DeviceUtils.getInstance(getDevice()).resetPackage(mPackageName);
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        if (mRecordScreen) {
            testUtils.collectScreenRecord(
                    () -> {
                        launchPackageAndCheckForCrash();
                    },
                    mPackageName);
        } else {
            launchPackageAndCheckForCrash();
        }
    }

    private void launchPackageAndCheckForCrash() throws DeviceNotAvailableException {
        CLog.d("Launching package: %s.", mPackageName);

        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);
        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        DeviceTimestamp startTime = deviceUtils.currentTimeMillis();
        try {
            deviceUtils.launchPackage(mPackageName);
        } catch (DeviceUtilsException e) {
            Assert.fail(e.getMessage());
        }

        CLog.d("Waiting %s milliseconds for the app to launch fully.", mAppLaunchTimeoutMs);
        RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);

        CLog.d("Completed launching package: %s", mPackageName);

        try {
            String crashLog = testUtils.getDropboxPackageCrashLog(mPackageName, startTime, true);
            if (crashLog != null) {
                Assert.fail(crashLog);
            }
        } catch (IOException e) {
            Assert.fail("Error while getting dropbox crash log: " + e);
        }
    }
}
