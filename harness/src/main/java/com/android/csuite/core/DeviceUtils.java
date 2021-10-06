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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A utility class that contains common methods to interact with the test device. */
public final class DeviceUtils {
    @VisibleForTesting static final String UNKNOWN = "Unknown";
    @VisibleForTesting static final String VERSION_CODE_PREFIX = "versionCode=";
    @VisibleForTesting static final String VERSION_NAME_PREFIX = "versionName=";
    private static final String VIDEO_PATH_ON_DEVICE = "/sdcard/screenrecord.mp4";
    private static final int WAIT_FOR_SCREEN_RECORDING_START_MS = 10 * 1000;
    private static final int WAIT_FOR_SCREEN_RECORDING_END_MS = 10 * 1000;

    /**
     * A task that throws DeviceNotAvailableException. Use this interface instead of Runnable so
     * that the DeviceNotAvailableException won't need to be handled inside the run() method.
     */
    public interface RunnerTask {
        void run() throws DeviceNotAvailableException;
    }

    /**
     * Record the device screen while running a task.
     *
     * <p>This method will not throw exception when the screenrecord command failed unless the
     * device is unresponsive.
     *
     * @param device The test device
     * @param action A runnable job that throws DeviceNotAvailableException.
     * @return The screen recording file on the host, or null if failed to get the recording file
     *     from the device.
     * @throws DeviceNotAvailableException When the device is unresponsive.
     */
    public static File runWithScreenRecording(ITestDevice device, RunnerTask action)
            throws DeviceNotAvailableException {
        ExecutorService executors =
                MoreExecutors.getExitingExecutorService(
                        (ThreadPoolExecutor) Executors.newFixedThreadPool(1));

        // Start the recording thread in background
        CompletableFuture<CommandResult> recordingFuture =
                CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        return device.executeShellV2Command(
                                                String.format(
                                                        "screenrecord %s", VIDEO_PATH_ON_DEVICE));
                                    } catch (DeviceNotAvailableException e) {
                                        throw new RuntimeException(e);
                                    }
                                },
                                executors)
                        .whenComplete(
                                (commandResult, exception) -> {
                                    if (commandResult != null) {
                                        CLog.d("Screenrecord command completed: %s", commandResult);
                                    }
                                    executors.shutdown();
                                });

        // Make sure the recording has started
        String pid;
        long start = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - start > WAIT_FOR_SCREEN_RECORDING_START_MS) {
                throw new RuntimeException("Unnable to start screenrecord. Pid is not detected.");
            }

            CommandResult result = device.executeShellV2Command("pidof screenrecord");
            if (result.getStatus() != CommandStatus.SUCCESS) {
                CLog.d("The pid of screenrecord is not found yet. Trying again. %s", result);
                continue;
            }

            String[] pids = result.getStdout().trim().split(" ");

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
                    recordingFuture.get(WAIT_FOR_SCREEN_RECORDING_START_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    CLog.e(e);
                    recordingFuture.cancel(true);
                } catch (ExecutionException e) {
                    CLog.e("Failed to complete the screenrecord command: %s", e);
                }
                video = device.pullFile(VIDEO_PATH_ON_DEVICE);
                device.deleteFile(VIDEO_PATH_ON_DEVICE);
            }
        }

        return video;
    }

    /**
     * Gets the version name of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public static String getPackageVersionName(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        CommandResult cmdResult =
                device.executeShellV2Command(
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
    public static String getPackageVersionCode(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        CommandResult cmdResult =
                device.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionCode", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_CODE_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().split(" ")[0].substring(VERSION_CODE_PREFIX.length());
    }
}