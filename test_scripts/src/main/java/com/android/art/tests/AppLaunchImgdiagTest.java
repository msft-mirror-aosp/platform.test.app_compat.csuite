/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.art.tests;

import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.TestUtils;
import com.android.csuite.tests.AppLaunchTest;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.After;
import org.junit.Assert;

import java.io.File;

/** A test that gets imgdiag data after launching an app. */
public class AppLaunchImgdiagTest extends AppLaunchTest {
    @Option(
            name = "imgdiag-out-path",
            description = "Path to directory containing imgdiag output files.")
    private String mImgdiagOutPath;

    @After
    public void tearDown() throws DeviceNotAvailableException, ApkInstallerException {
        String[] packagePids =
                getDevice().executeShellCommand("pidof " + mPackageName).strip().split(" ");
        Assert.assertEquals(1, packagePids.length);
        String targetPid = packagePids[0].strip();
        Assert.assertFalse(targetPid.isEmpty());

        String outFileName = String.format("imgdiag_%s_%s.txt", mPackageName, targetPid);
        String outFilePath = new File(mImgdiagOutPath, outFileName).getAbsolutePath();

        String imgdiagCmd = getImgdiagRunCmd(targetPid, outFilePath);
        CommandResult res = getDevice().executeShellV2Command(imgdiagCmd);
        Assert.assertEquals(
                String.format(
                        "Failed to run imgdiag:\n%s\nResult:\n%s", imgdiagCmd, res.toString()),
                CommandStatus.SUCCESS,
                res.getStatus());

        File imgdiagFile = getDevice().pullFile(outFilePath);
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);
        testUtils
                .getTestArtifactReceiver()
                .addTestArtifact(outFileName, LogDataType.HOST_LOG, imgdiagFile);

        super.tearDown();
    }

    /**
     * Constructs the command line string to run the `imgdiag` tool for gathering dirty image
     * objects.
     *
     * @param targetPid The process ID of the target process to analyze.
     * @param outFilePath The absolute file path on the device where the imgdiag data will be saved.
     * @return The complete `imgdiag` command that can be run with `adb shell`.
     */
    public static String getImgdiagRunCmd(String targetPid, String outFilePath) {
        return String.format(
                "imgdiag --zygote-diff-pid=`pidof zygote64` --image-diff-pid=%s"
                        + " --output=%s"
                        + " --dump-dirty-objects --boot-image="
                        + "/data/misc/apexdata/com.android.art/dalvik-cache/boot.art",
                targetPid, outFilePath);
    }
}
