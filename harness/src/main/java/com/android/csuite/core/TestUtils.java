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

import com.android.compatibility.FailureCollectingListener;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.InstrumentationTest;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** A utility class that contains common methods used by tests. */
public final class TestUtils {
    private static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    private final AbstractCSuiteTest mTestBase;
    private final DeviceUtils mDeviceUtils;
    private final InstrumentationTestProvider mInstrumentationTestProvider;

    // The following constants are needed for the instrumentation crash checker.
    private static final String CRASH_CHECK_TEST_RUNNER =
            "com.android.csuite.crash_check.AppCrashCheckTestRunner";
    private static final String CRASH_CHECK_TEST_PACKAGE = "com.android.csuite.crash_check";
    private static final String PACKAGE_NAME_ARG = "PACKAGE_NAME_ARG";
    private static final String START_TIME_ARG = "START_TIME_ARG";
    private static final int BASE_INSTRUMENTATION_TEST_TIMEOUT_MS = 10 * 1000;

    public static TestUtils getInstance(AbstractCSuiteTest testBase) {
        return new TestUtils(testBase, new CrashCheckInstrumentationProvider());
    }

    @VisibleForTesting
    TestUtils(
            AbstractCSuiteTest testBase, InstrumentationTestProvider instrumentationTestProvider) {
        mTestBase = testBase;
        mDeviceUtils = DeviceUtils.getInstance(testBase.getDevice());
        mInstrumentationTestProvider = instrumentationTestProvider;
    }

    /**
     * Take a screenshot on the device and save it to the test result artifacts.
     *
     * @param prefix The file name prefix.
     * @throws DeviceNotAvailableException
     */
    public void collectScreenshot(String prefix) throws DeviceNotAvailableException {
        try (InputStreamSource screenSource = mTestBase.getDevice().getScreenshot()) {
            mTestBase.addTestArtifact(
                    prefix + "_screenshot_" + mTestBase.getDevice().getSerialNumber(),
                    LogDataType.PNG,
                    screenSource);
        }
    }

    /**
     * Record the device screen while running a task and save the video file to the test result
     * artifacts.
     *
     * @param job A job to run while recording the screen.
     * @param prefix The file name prefix.
     * @throws DeviceNotAvailableException
     */
    public void collectScreenRecord(DeviceUtils.RunnerTask job, String prefix)
            throws DeviceNotAvailableException {
        File video = mDeviceUtils.runWithScreenRecording(job);
        if (video != null) {
            mTestBase.addTestArtifact(
                    prefix + "_screenrecord_" + mTestBase.getDevice().getSerialNumber(),
                    LogDataType.MP4,
                    video);
        } else {
            CLog.e("Failed to get screen recording.");
        }
    }

    /**
     * Collect the GMS version name and version code, and save them as test result artifacts.
     *
     * @param prefix The file name prefix.
     * @throws DeviceNotAvailableException
     */
    public void collectGmsVersion(String prefix) throws DeviceNotAvailableException {
        String gmsVersionCode = mDeviceUtils.getPackageVersionCode(GMS_PACKAGE_NAME);
        String gmsVersionName = mDeviceUtils.getPackageVersionName(GMS_PACKAGE_NAME);
        CLog.i("GMS core versionCode=%s, versionName=%s", gmsVersionCode, gmsVersionName);

        // Note: If the file name format needs to be modified, do it with cautions as some users may
        // be parsing the output file name to get the version information.
        mTestBase.addTestArtifact(
                String.format("%s_[GMS_versionCode=%s]", prefix, gmsVersionCode),
                LogDataType.TEXT,
                gmsVersionCode.getBytes());
        mTestBase.addTestArtifact(
                String.format("%s_[GMS_versionName=%s]", prefix, gmsVersionName),
                LogDataType.TEXT,
                gmsVersionName.getBytes());
    }

    /**
     * Collect the given package's version name and version code, and save them as test result
     * artifacts.
     *
     * @param packageName The package name.
     * @throws DeviceNotAvailableException
     */
    public void collectAppVersion(String packageName) throws DeviceNotAvailableException {
        String versionCode = mDeviceUtils.getPackageVersionCode(packageName);
        String versionName = mDeviceUtils.getPackageVersionName(packageName);
        CLog.i("Package %s versionCode=%s, versionName=%s", packageName, versionCode, versionName);

        // Note: If the file name format needs to be modified, do it with cautions as some users may
        // be parsing the output file name to get the version information.
        mTestBase.addTestArtifact(
                String.format("%s_[versionCode=%s]", packageName, versionCode),
                LogDataType.TEXT,
                versionCode.getBytes());
        mTestBase.addTestArtifact(
                String.format("%s_[versionName=%s]", packageName, versionName),
                LogDataType.TEXT,
                versionName.getBytes());
    }

    /**
     * Looks for crash log of a package in the device's dropbox entries.
     *
     * @param packageName The package name of an app.
     * @param startTimeOnDevice The device timestamp after which the check starts. Dropbox items
     *     before this device timestamp will be ignored.
     * @return A string of crash log if crash was found; null otherwise.
     * @throws DeviceNotAvailableException
     */
    public String getDropboxPackageCrashedLog(String packageName, long startTimeOnDevice)
            throws DeviceNotAvailableException {
        mDeviceUtils.resetPackage(CRASH_CHECK_TEST_PACKAGE);

        InstrumentationTest instrumentationTest = mInstrumentationTestProvider.get();

        instrumentationTest.addInstrumentationArg(PACKAGE_NAME_ARG, packageName);
        instrumentationTest.addInstrumentationArg(START_TIME_ARG, Long.toString(startTimeOnDevice));
        instrumentationTest.setDevice(mTestBase.getDevice());
        instrumentationTest.setConfiguration(mTestBase.getConfiguration());
        FailureCollectingListener failureListener = new FailureCollectingListener();
        instrumentationTest.run(mTestBase.getTestInfo(), failureListener);

        return failureListener.getStackTrace();
    }

    /**
     * Checks whether the process of the given package is running on the device.
     *
     * @param packageName The package name of an app.
     * @return True if the package is running; False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean isPackageProcessRunning(String packageName) throws DeviceNotAvailableException {
        return mTestBase.getDevice().executeShellV2Command("pidof " + packageName).getExitCode()
                == 0;
    }

    /**
     * Generates a list of APK paths where the base.apk of split apk files are always on the first
     * index if exists.
     *
     * <p>If the apk path is a single apk, then the apk is returned. If the apk path is a directory
     * containing only one non-split apk file, the apk file is returned. If the apk path is a
     * directory containing split apk files for one package, then the list of apks are returned and
     * the base.apk sits on the first index. If the apk path does not contain any apk files, or
     * multiple apk files without base.apk, then an IOException is thrown.
     *
     * @return A list of APK paths.
     * @throws TestUtilsException If failed to read the apk path or unexpected number of apk files
     *     are found under the path.
     */
    public static List<Path> listApks(Path root) throws TestUtilsException {
        // The apk path points to a non-split apk file.
        if (Files.isRegularFile(root)) {
            if (!root.toString().endsWith(".apk")) {
                throw new TestUtilsException(
                        "The file on the given apk path is not an apk file: " + root);
            }
            return List.of(root);
        }

        List<Path> apks;
        CLog.d("APK path = " + root);
        try (Stream<Path> fileTree = Files.walk(root)) {
            apks =
                    fileTree.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".apk"))
                            .collect(Collectors.toList());
        } catch (IOException e) {
            throw new TestUtilsException("Failed to list apk files.", e);
        }

        if (apks.isEmpty()) {
            throw new TestUtilsException("The apk directory does not contain any apk files");
        }

        // The apk path contains a single non-split apk or the base.apk of a split-apk.
        if (apks.size() == 1) {
            return apks;
        }

        if (apks.stream().map(path -> path.getParent().toString()).distinct().count() != 1) {
            throw new TestUtilsException(
                    "Apk files are not all in the same folder: "
                            + Arrays.deepToString(apks.toArray(new Path[apks.size()])));
        }

        if (apks.stream().filter(path -> path.getFileName().toString().equals("base.apk")).count()
                == 0) {
            throw new TestUtilsException(
                    "Multiple non-split apk files detected: "
                            + Arrays.deepToString(apks.toArray(new Path[apks.size()])));
        }

        Collections.sort(
                apks,
                (first, second) -> first.getFileName().toString().equals("base.apk") ? -1 : 0);

        return apks;
    }

    /** An exception class representing crawler test failures. */
    public static final class TestUtilsException extends Exception {
        /**
         * Constructs a new {@link TestUtilsException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        private TestUtilsException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link TestUtilsException} with a meaningful error message, and a cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the TestUtilsException.
         */
        private TestUtilsException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link TestUtilsException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the TestUtilsException.
         */
        private TestUtilsException(Throwable cause) {
            super(cause);
        }
    }

    @VisibleForTesting
    interface InstrumentationTestProvider {
        InstrumentationTest get();
    }

    private static class CrashCheckInstrumentationProvider implements InstrumentationTestProvider {
        @Override
        public InstrumentationTest get() {
            InstrumentationTest instrumentationTest = new InstrumentationTest();

            instrumentationTest.setPackageName(CRASH_CHECK_TEST_PACKAGE);
            instrumentationTest.setRunnerName(CRASH_CHECK_TEST_RUNNER);
            instrumentationTest.setShellTimeout(BASE_INSTRUMENTATION_TEST_TIMEOUT_MS);
            instrumentationTest.setTestTimeout(BASE_INSTRUMENTATION_TEST_TIMEOUT_MS);

            return instrumentationTest;
        }
    }
}
