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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/** A utility class that contains common methods to interact with the test device. */
public final class DeviceUtils {
    @VisibleForTesting static final String UNKNOWN = "Unknown";
    @VisibleForTesting static final String VERSION_CODE_PREFIX = "versionCode=";
    @VisibleForTesting static final String VERSION_NAME_PREFIX = "versionName=";

    @VisibleForTesting
    static final String LAUNCH_PACKAGE_COMMAND_TEMPLATE =
            "monkey -p %s -c android.intent.category.LAUNCHER 1";

    private static final String VIDEO_PATH_ON_DEVICE_TEMPLATE = "/sdcard/screenrecord_%s.mp4";
    @VisibleForTesting static final int WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS = 10 * 1000;
    @VisibleForTesting static final int WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS = 500;

    private final ITestDevice mDevice;
    private final Sleeper mSleeper;
    private final Clock mClock;
    private final RunUtilProvider mRunUtilProvider;

    public static DeviceUtils getInstance(ITestDevice device) {
        return new DeviceUtils(
                device,
                duration -> {
                    Thread.sleep(duration);
                },
                () -> System.currentTimeMillis(),
                () -> RunUtil.getDefault());
    }

    @VisibleForTesting
    DeviceUtils(ITestDevice device, Sleeper sleeper, Clock clock, RunUtilProvider runUtilProvider) {
        mDevice = device;
        mSleeper = sleeper;
        mClock = clock;
        mRunUtilProvider = runUtilProvider;
    }

    /**
     * A task that throws DeviceNotAvailableException. Use this interface instead of Runnable so
     * that the DeviceNotAvailableException won't need to be handled inside the run() method.
     */
    public interface RunnerTask {
        void run() throws DeviceNotAvailableException;
    }

    /**
     * Get the current device timestamp in milliseconds.
     *
     * @return The device time
     * @throws DeviceNotAvailableException When the device is not available.
     * @throws DeviceRuntimeException When the command to get device time failed or failed to parse
     *     the timestamp.
     */
    public long currentTimeMillis() throws DeviceNotAvailableException, DeviceRuntimeException {
        CommandResult result = mDevice.executeShellV2Command("echo ${EPOCHREALTIME:0:14}");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    "Failed to get device time: " + result,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        try {
            return Long.parseLong(result.getStdout().replace(".", "").trim());
        } catch (NumberFormatException e) {
            CLog.e("Cannot parse device time string: " + result.getStdout());
            throw new DeviceRuntimeException(
                    "Cannot parse device time string: " + result.getStdout(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    /**
     * Record the device screen while running a task.
     *
     * <p>This method will not throw exception when the screenrecord command failed unless the
     * device is unresponsive.
     *
     * @param action A runnable job that throws DeviceNotAvailableException.
     * @return The screen recording file on the host, or null if failed to get the recording file
     *     from the device.
     * @throws DeviceNotAvailableException When the device is unresponsive.
     */
    public File runWithScreenRecording(RunnerTask action) throws DeviceNotAvailableException {
        String videoPath = String.format(VIDEO_PATH_ON_DEVICE_TEMPLATE, new Random().nextInt());
        mDevice.deleteFile(videoPath);
        File video = null;

        // Start screen recording
        Process recordingProcess = null;
        try {
            recordingProcess =
                    mRunUtilProvider
                            .get()
                            .runCmdInBackground(
                                    String.format(
                                                    "adb -s %s shell screenrecord %s",
                                                    mDevice.getSerialNumber(), videoPath)
                                            .split("\\s+"));
        } catch (IOException ioException) {
            CLog.e("Exception is thrown when starting screen recording process: %s", ioException);
        }

        try {
            long start = mClock.currentTimeMillis();
            // Wait for the recording to start since it may take time for the device to start
            // recording
            while (recordingProcess != null) {
                CommandResult result = mDevice.executeShellV2Command("ls " + videoPath);
                if (result.getStatus() == CommandStatus.SUCCESS) {
                    break;
                }

                CLog.d(
                        "Screenrecord not started yet. Waiting %s milliseconds.",
                        WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);

                try {
                    mSleeper.sleep(WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (mClock.currentTimeMillis() - start
                        > WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS) {
                    CLog.e(
                            "Screenrecord did not start within %s milliseconds.",
                            WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS);
                    break;
                }
            }

            action.run();
        } finally {
            if (recordingProcess != null) {
                recordingProcess.destroy();
            }
            // Try to pull and delete the video file from the device anyway.
            video = mDevice.pullFile(videoPath);
            mDevice.deleteFile(videoPath);
        }

        return video;
    }

    /**
     * Launches a package on the device.
     *
     * @param packageName The package name to launch.
     * @return True if successfully launches the package or the package is already launched; False
     *     otherwise.
     * @throws DeviceNotAvailableException When device was lost.
     */
    public boolean launchPackage(String packageName) throws DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        String.format(LAUNCH_PACKAGE_COMMAND_TEMPLATE, packageName));
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            CLog.e("The command to launch package %s failed: %s", packageName, result);
            return false;
        }

        return true;
    }

    /**
     * Gets the version name of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionName(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionName", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_NAME_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().substring(VERSION_NAME_PREFIX.length());
    }

    /**
     * Gets the version code of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version code, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionCode(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionCode", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_CODE_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().split(" ")[0].substring(VERSION_CODE_PREFIX.length());
    }

    @VisibleForTesting
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    @VisibleForTesting
    interface Clock {
        long currentTimeMillis();
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }
}
