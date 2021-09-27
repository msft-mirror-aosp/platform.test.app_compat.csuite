/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.compatibility.testtype;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.compatibility.FailureCollectingListener;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.LogcatReceiver;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.CompatibilityTestResult;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.json.JSONException;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** A test that verifies that a single app can be successfully launched. */
public class AppLaunchTest
        implements IDeviceTest, IRemoteTest, IConfigurationReceiver, ITestFilterReceiver {
    @VisibleForTesting static final String SCREENSHOT_AFTER_LAUNCH = "screenshot-after-launch";
    @VisibleForTesting static final String COLLECT_APP_VERSION = "collect-app-version";
    @VisibleForTesting static final String COLLECT_GMS_VERSION = "collect-gms-version";
    @VisibleForTesting static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    @VisibleForTesting static final String RECORD_SCREEN = "record-screen";
    @VisibleForTesting static final String ENABLE_SPLASH_SCREEN = "enable-splash-screen";

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = SCREENSHOT_AFTER_LAUNCH,
            description = "Whether to take a screenshost after a package is launched.")
    private boolean mScreenshotAfterLaunch;

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
            name = ENABLE_SPLASH_SCREEN,
            description =
                    "Whether to enable splash screen when launching an package from the"
                            + " instrumentation test.")
    private boolean mEnableSplashScreen;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(name = "test-label", description = "Unique test identifier label.")
    private String mTestLabel = "AppCompatibility";

    @Option(name = "include-filter", description = "The include filter of the test type.")
    protected Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = "exclude-filter", description = "The exclude filter of the test type.")
    protected Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = "dismiss-dialog", description = "Attempt to dismiss dialog from apps.")
    protected boolean mDismissDialog = false;

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 15000;

    private static final String LAUNCH_TEST_RUNNER =
            "com.android.compatibilitytest.AppCompatibilityRunner";
    private static final String LAUNCH_TEST_PACKAGE = "com.android.compatibilitytest";
    private static final String PACKAGE_TO_LAUNCH = "package_to_launch";
    private static final String ARG_DISMISS_DIALOG = "ARG_DISMISS_DIALOG";
    private static final String APP_LAUNCH_TIMEOUT_LABEL = "app_launch_timeout_ms";
    private static final int LOGCAT_SIZE_BYTES = 20 * 1024 * 1024;
    private static final int BASE_INSTRUMENTATION_TEST_TIMEOUT_MS = 10 * 1000;

    private ITestDevice mDevice;
    private LogcatReceiver mLogcat;
    private IConfiguration mConfiguration;

    public AppLaunchTest() {
        this(null);
    }

    @VisibleForTesting
    public AppLaunchTest(String packageName) {
        mPackageName = packageName;
    }

    /**
     * Creates and sets up an instrumentation test with information about the test runner as well as
     * the package being tested (provided as a parameter).
     */
    protected InstrumentationTest createInstrumentationTest(String packageBeingTested) {
        InstrumentationTest instrumentationTest = new InstrumentationTest();

        instrumentationTest.setPackageName(LAUNCH_TEST_PACKAGE);
        instrumentationTest.setConfiguration(mConfiguration);
        instrumentationTest.addInstrumentationArg(PACKAGE_TO_LAUNCH, packageBeingTested);
        instrumentationTest.setRunnerName(LAUNCH_TEST_RUNNER);
        instrumentationTest.setDevice(mDevice);
        instrumentationTest.addInstrumentationArg(
                APP_LAUNCH_TIMEOUT_LABEL, Integer.toString(mAppLaunchTimeoutMs));
        instrumentationTest.addInstrumentationArg(
                ARG_DISMISS_DIALOG, Boolean.toString(mDismissDialog));
        instrumentationTest.addInstrumentationArg(
                ENABLE_SPLASH_SCREEN, Boolean.toString(mEnableSplashScreen));

        int testTimeoutMs = BASE_INSTRUMENTATION_TEST_TIMEOUT_MS + mAppLaunchTimeoutMs * 2;
        instrumentationTest.setShellTimeout(testTimeoutMs);
        instrumentationTest.setTestTimeout(testTimeoutMs);

        return instrumentationTest;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void run(final TestInformation testInfo, final ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        CLog.d("Start of run method.");
        CLog.d("Include filters: %s", mIncludeFilters);
        CLog.d("Exclude filters: %s", mExcludeFilters);

        Assert.assertNotNull("Package name cannot be null", mPackageName);

        TestDescription testDescription = createTestDescription();

        if (!inFilter(testDescription.toString())) {
            CLog.d("Test case %s doesn't match any filter", testDescription);
            return;
        }
        CLog.d("Complete filtering test case: %s", testDescription);

        long start = System.currentTimeMillis();
        listener.testRunStarted(mTestLabel, 1);
        mLogcat = new LogcatReceiver(getDevice(), LOGCAT_SIZE_BYTES, 0);
        mLogcat.start();

        try {
            testPackage(testInfo, testDescription, listener);
        } catch (InterruptedException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        } finally {
            mLogcat.stop();
            listener.testRunEnded(
                    System.currentTimeMillis() - start, new HashMap<String, Metric>());
        }
    }

    /**
     * Attempts to test a package and reports the results.
     *
     * @param listener The {@link ITestInvocationListener}.
     * @throws DeviceNotAvailableException
     */
    private void testPackage(
            final TestInformation testInfo,
            TestDescription testDescription,
            ITestInvocationListener listener)
            throws DeviceNotAvailableException, InterruptedException {
        CLog.d("Started testing package: %s.", mPackageName);

        listener.testStarted(testDescription, System.currentTimeMillis());

        CompatibilityTestResult result = createCompatibilityTestResult();
        result.packageName = mPackageName;

        try {
            if (mCollectAppVersion) {
                String versionCode = DeviceUtils.getPackageVersionCode(mDevice, mPackageName);
                String versionName = DeviceUtils.getPackageVersionName(mDevice, mPackageName);
                CLog.i(
                        "Testing package %s versionCode=%s, versionName=%s",
                        mPackageName, versionCode, versionName);
                listener.testLog(
                        String.format("%s_[versionCode=%s]", mPackageName, versionCode),
                        LogDataType.TEXT,
                        new ByteArrayInputStreamSource(versionCode.getBytes()));
                listener.testLog(
                        String.format("%s_[versionName=%s]", mPackageName, versionName),
                        LogDataType.TEXT,
                        new ByteArrayInputStreamSource(versionName.getBytes()));
            }

            if (mRecordScreen) {
                File video =
                        DeviceUtils.runWithScreenRecording(
                                mDevice,
                                () -> {
                                    launchPackage(testInfo, result);
                                });
                if (video != null) {
                    listener.testLog(
                            mPackageName + "_screenrecord_" + mDevice.getSerialNumber(),
                            LogDataType.MP4,
                            new FileInputStreamSource(video));
                } else {
                    CLog.e("Failed to get screen recording.");
                }
            } else {
                launchPackage(testInfo, result);
            }

            if (mScreenshotAfterLaunch) {
                try (InputStreamSource screenSource = mDevice.getScreenshot()) {
                    listener.testLog(
                            mPackageName + "_screenshot_" + mDevice.getSerialNumber(),
                            LogDataType.PNG,
                            screenSource);
                } catch (DeviceNotAvailableException e) {
                    CLog.e(
                            "Device %s became unavailable while capturing screenshot, %s",
                            mDevice.getSerialNumber(), e.toString());
                    throw e;
                }
            }

            if (mCollectGmsVersion) {
                String gmsVersionCode =
                        DeviceUtils.getPackageVersionCode(mDevice, GMS_PACKAGE_NAME);
                String gmsVersionName =
                        DeviceUtils.getPackageVersionName(mDevice, GMS_PACKAGE_NAME);
                CLog.i(
                        "GMS core versionCode=%s, versionName=%s",
                        mPackageName, gmsVersionCode, gmsVersionName);
                listener.testLog(
                        String.format("%s_[GMS_versionCode=%s]", mPackageName, gmsVersionCode),
                        LogDataType.TEXT,
                        new ByteArrayInputStreamSource(gmsVersionCode.getBytes()));
                listener.testLog(
                        String.format("%s_[GMS_versionName=%s]", mPackageName, gmsVersionName),
                        LogDataType.TEXT,
                        new ByteArrayInputStreamSource(gmsVersionName.getBytes()));
            }
        } finally {
            reportResult(listener, testDescription, result);
            stopPackage();
            try {
                postLogcat(result, listener);
            } catch (JSONException e) {
                CLog.w("Posting failed: %s.", e.getMessage());
            }
            listener.testEnded(
                    testDescription,
                    System.currentTimeMillis(),
                    Collections.<String, String>emptyMap());

            CLog.d("Completed testing package: %s.", mPackageName);
        }
    }

    /**
     * Method which attempts to launch a package.
     *
     * <p>Will set the result status to success if the package could be launched. Otherwise the
     * result status will be set to failure.
     *
     * @param result the {@link CompatibilityTestResult} containing the package info.
     * @throws DeviceNotAvailableException
     */
    private void launchPackage(final TestInformation testInfo, CompatibilityTestResult result)
            throws DeviceNotAvailableException {
        CLog.d("Launching package: %s.", result.packageName);

        CommandResult resetResult = resetPackage();
        if (resetResult.getStatus() != CommandStatus.SUCCESS) {
            result.status = CompatibilityTestResult.STATUS_ERROR;
            result.message = resetResult.getStatus() + resetResult.getStderr();
            return;
        }

        InstrumentationTest instrTest = createInstrumentationTest(result.packageName);

        FailureCollectingListener failureListener = createFailureListener();
        instrTest.run(testInfo, failureListener);
        CLog.d("Stack Trace: %s", failureListener.getStackTrace());

        if (failureListener.getStackTrace() != null) {
            CLog.w("Failed to launch package: %s.", result.packageName);
            result.status = CompatibilityTestResult.STATUS_FAILURE;
            result.message = failureListener.getStackTrace();
        } else {
            result.status = CompatibilityTestResult.STATUS_SUCCESS;
        }

        CLog.d("Completed launching package: %s", result.packageName);
    }

    /** Helper method which reports a test failed if the status is either a failure or an error. */
    private void reportResult(
            ITestInvocationListener listener, TestDescription id, CompatibilityTestResult result) {
        String message = result.message != null ? result.message : "unknown";
        String tag = errorStatusToTag(result.status);
        if (tag != null) {
            listener.testFailed(id, result.status + ":" + message);
        }
    }

    private String errorStatusToTag(String status) {
        if (status.equals(CompatibilityTestResult.STATUS_ERROR)) {
            return "ERROR";
        }
        if (status.equals(CompatibilityTestResult.STATUS_FAILURE)) {
            return "FAILURE";
        }
        return null;
    }

    /** Helper method which posts the logcat. */
    private void postLogcat(CompatibilityTestResult result, ITestInvocationListener listener)
            throws JSONException {
        InputStreamSource stream = null;
        String header =
                String.format(
                        "%s%s%s\n",
                        CompatibilityTestResult.SEPARATOR,
                        result.toJsonString(),
                        CompatibilityTestResult.SEPARATOR);

        try (InputStreamSource logcatData = mLogcat.getLogcatData()) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                baos.write(header.getBytes());
                StreamUtil.copyStreams(logcatData.createInputStream(), baos);
                stream = new ByteArrayInputStreamSource(baos.toByteArray());
            } catch (IOException e) {
                CLog.e("error inserting compatibility test result into logcat");
                CLog.e(e);
                // fallback to logcat data
                stream = logcatData;
            }
            listener.testLog("logcat_" + result.packageName, LogDataType.LOGCAT, stream);
        } finally {
            StreamUtil.cancel(stream);
        }
    }

    /**
     * Return true if a test matches one or more of the include filters AND does not match any of
     * the exclude filters. If no include filters are given all tests should return true as long as
     * they do not match any of the exclude filters.
     */
    protected boolean inFilter(String testName) {
        if (mExcludeFilters.contains(testName)) {
            return false;
        }
        if (mIncludeFilters.size() == 0 || mIncludeFilters.contains(testName)) {
            return true;
        }
        return false;
    }

    protected CommandResult resetPackage() throws DeviceNotAvailableException {
        return mDevice.executeShellV2Command(String.format("pm clear %s", mPackageName));
    }

    private void stopPackage() throws DeviceNotAvailableException {
        mDevice.executeShellCommand(String.format("am force-stop %s", mPackageName));
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Get a test description for use in logging. For compatibility with logs, this should be
     * TestDescription(test class name, test type).
     */
    private TestDescription createTestDescription() {
        return new TestDescription(getClass().getSimpleName(), mPackageName);
    }

    /** Get a FailureCollectingListener for failure listening. */
    private FailureCollectingListener createFailureListener() {
        return new FailureCollectingListener();
    }

    /**
     * Get a CompatibilityTestResult for encapsulating compatibility run results for a single app
     * package tested.
     */
    private CompatibilityTestResult createCompatibilityTestResult() {
        return new CompatibilityTestResult();
    }

    /** {@inheritDoc} */
    @Override
    public void addIncludeFilter(String filter) {
        checkArgument(!Strings.isNullOrEmpty(filter), "Include filter cannot be null or empty.");
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        checkNotNull(filters, "Include filters cannot be null.");
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getIncludeFilters() {
        return Collections.unmodifiableSet(mIncludeFilters);
    }

    /** {@inheritDoc} */
    @Override
    public void addExcludeFilter(String filter) {
        checkArgument(!Strings.isNullOrEmpty(filter), "Exclude filter cannot be null or empty.");
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        checkNotNull(filters, "Exclude filters cannot be null.");
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getExcludeFilters() {
        return Collections.unmodifiableSet(mExcludeFilters);
    }

    private static final class DeviceUtils {
        static final String UNKNOWN = "Unknown";
        private static final String VIDEO_PATH_ON_DEVICE = "/sdcard/screenrecord.mp4";
        private static final int WAIT_FOR_SCREEN_RECORDING_START_MS = 10 * 1000;

        static File runWithScreenRecording(ITestDevice device, RunnerTask action)
                throws DeviceNotAvailableException {
            // Start the recording thread in background
            CompletableFuture<CommandResult> recordingFuture =
                    CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            return device.executeShellV2Command(
                                                    String.format(
                                                            "screenrecord %s",
                                                            VIDEO_PATH_ON_DEVICE));
                                        } catch (DeviceNotAvailableException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .whenComplete(
                                    (commandResult, exception) -> {
                                        if (exception != null) {
                                            CLog.e(
                                                    "Device was lost during screenrecording: %s",
                                                    exception);
                                        } else {
                                            CLog.d(
                                                    "Screenrecord command completed: %s",
                                                    commandResult);
                                        }
                                    });

            // Make sure the recording has started
            String pid;
            long start = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() - start > WAIT_FOR_SCREEN_RECORDING_START_MS) {
                    throw new RuntimeException(
                            "Unnable to start screenrecord. Pid is not detected.");
                }

                String[] pids = device.executeShellCommand("pidof screenrecord").trim().split(" ");

                if (pids.length > 0) {
                    pid = pids[0];
                    break;
                }
            }

            File video = null;

            try {
                action.run();
            } finally {
                if (pid != null) {
                    device.executeShellV2Command(String.format("kill -2 %s", pid));
                    try {
                        recordingFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                    video = device.pullFile(VIDEO_PATH_ON_DEVICE);
                    device.deleteFile(VIDEO_PATH_ON_DEVICE);
                }
            }

            return video;
        }

        interface RunnerTask {
            void run() throws DeviceNotAvailableException;
        }

        /**
         * Gets the version name of a package installed on the device.
         *
         * @param packageName The full package name to query
         * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
         *     command failed.
         * @throws DeviceNotAvailableException
         */
        static String getPackageVersionName(ITestDevice device, String packageName)
                throws DeviceNotAvailableException {
            CommandResult cmdResult =
                    device.executeShellV2Command(
                            String.format("dumpsys package %s | grep versionName", packageName));

            String prefix = "versionName=";

            if (cmdResult.getStatus() != CommandStatus.SUCCESS
                    || !cmdResult.getStdout().contains(prefix)) {
                return UNKNOWN;
            }

            return cmdResult.getStdout().trim().substring(prefix.length());
        }

        /**
         * Gets the version code of a package installed on the device.
         *
         * @param packageName The full package name to query
         * @return The package version code, or 'Unknown' if the package doesn't exist or the adb
         *     command failed.
         * @throws DeviceNotAvailableException
         */
        static String getPackageVersionCode(ITestDevice device, String packageName)
                throws DeviceNotAvailableException {
            CommandResult cmdResult =
                    device.executeShellV2Command(
                            String.format("dumpsys package %s | grep versionCode", packageName));

            String prefix = "versionCode=";

            if (cmdResult.getStatus() != CommandStatus.SUCCESS
                    || !cmdResult.getStdout().contains(prefix)) {
                return UNKNOWN;
            }

            return cmdResult.getStdout().trim().split(" ")[0].substring(prefix.length());
        }
    }
}
