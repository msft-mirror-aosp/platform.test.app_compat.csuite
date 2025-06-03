/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.csuite.core.AppCrawlTester.RunUtilProvider;
import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;

/** A utility class for collecting AutoFDO profile from the test device. */
public class AutoFDOProfileCollector {
    @VisibleForTesting
    static final String RECORD_CMDLINE =
            "adb -s %s shell su root simpleperf record -e cs-etm:k -z --duration %f -a"
                    + " --log-to-android-buffer -o /data/local/tmp/perf.data";

    @VisibleForTesting
    static final String INJECT_CMDLINE =
            "adb -s %s shell su root simpleperf inject --output branch-list -i"
                    + " /data/local/tmp/perf.data --exclude-perf -z --binary kernel.kallsyms -o"
                    + " /data/local/tmp/perf_inject.data --log-to-android-buffer";

    @VisibleForTesting
    static final String ON_DEVICE_PROFILE_PATH = "/data/local/tmp/perf_inject.data";

    private final ITestDevice mDevice;
    private final RunUtilProvider mRunUtilProvider;
    private Process mRecordProcess;

    /**
     * Create an {@link AutoFDOProfileCollector} instance.
     *
     * @param device The test device.
     * @return an {@link AutoFDOProfileCollector} instance
     */
    public static AutoFDOProfileCollector newInstance(ITestDevice device) {
        return new AutoFDOProfileCollector(device, () -> new RunUtil());
    }

    @VisibleForTesting
    AutoFDOProfileCollector(ITestDevice device, RunUtilProvider runUtilProvider) {
        mDevice = device;
        mRunUtilProvider = runUtilProvider;
    }

    /**
     * Starts recording an AutoFDO profile on device.
     *
     * <p>The recording runs in the background for the specified duration.
     *
     * @param durationSec The duration in seconds for which to record the profile.
     * @return {@code true} if the recording was successfully started; {@code false} otherwise.
     */
    public boolean recordAutoFDOProfile(double durationSec) {
        CLog.i("Record AutoFDO profile for " + durationSec + " seconds");
        try {
            mRecordProcess =
                    mRunUtilProvider
                            .get()
                            .runCmdInBackground(
                                    String.format(
                                                    RECORD_CMDLINE,
                                                    mDevice.getSerialNumber(),
                                                    durationSec)
                                            .split("\\s+"));
        } catch (IOException e) {
            CLog.e("Failed to start recording AutoFDO profile: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Collects the recorded AutoFDO profile from the device to the host.
     *
     * <p>This method adds the AutoFDO profile into test log files.
     *
     * @param testArtifactReceiver An instance of {@link TestArtifactReceiver}.
     * @return {@code true} if the profile was successfully collected; {@code false} otherwise.
     * @throws DeviceNotAvailableException when the device is lost.
     */
    public boolean collectAutoFDOProfile(TestArtifactReceiver testArtifactReceiver)
            throws DeviceNotAvailableException {
        if (!finishRecordingAutoFDOProfile()) {
            return false;
        }
        CLog.i("Pulling AutoFDO profile on host");
        File profile = mDevice.pullFile(ON_DEVICE_PROFILE_PATH);
        if (profile == null) {
            CLog.e("Failed to pull AutoFDO profile to host");
            return false;
        }
        testArtifactReceiver.addTestArtifact("autofdo_file", LogDataType.UNKNOWN, profile);
        return true;
    }

    private boolean finishRecordingAutoFDOProfile() {
        Process injectProcess = null;
        try {
            if (mRecordProcess == null) {
                CLog.e("Recording process isn't available");
                return false;
            }
            int retCode = mRecordProcess.waitFor();
            if (retCode != 0) {
                CLog.e("Error recording AutoFDO profile with exit code: " + retCode);
                return false;
            }
            mRecordProcess = null;
            injectProcess =
                    mRunUtilProvider
                            .get()
                            .runCmdInBackground(
                                    String.format(INJECT_CMDLINE, mDevice.getSerialNumber())
                                            .split("\\s+"));
            retCode = injectProcess.waitFor();
            if (retCode != 0) {
                CLog.e("Error converting AutoFDO profile: " + retCode);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            CLog.e("Error recording AutoFDO profile: " + e.getMessage());
            if (mRecordProcess != null) {
                mRecordProcess.destroyForcibly();
            }
            if (injectProcess != null) {
                injectProcess.destroyForcibly();
            }
            return false;
        } catch (IOException e) {
            CLog.e("Error recording AutoFDO profile: " + e.getMessage());
            return false;
        }
    }
}
