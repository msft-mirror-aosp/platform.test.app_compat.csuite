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

package com.android.art.targetprep;

import com.android.art.tests.AppLaunchImgdiagTest;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.ITargetPreparer;

import org.junit.Assert;

import java.io.File;

/** Collect imgdiag data from all zygote children at the end of the run. */
public class AllProcessesImgdiag implements ITestLoggerReceiver, ITargetPreparer {
    @Option(
            name = "imgdiag-out-path",
            description = "Path to directory containing imgdiag output files.")
    private String mImgdiagOutPath;

    private ITestLogger mTestLogger;

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mTestLogger = testLogger;
    }

    @Override
    public void setUp(TestInformation testInformation) {}

    @Override
    public void tearDown(TestInformation testInformation, Throwable e)
            throws DeviceNotAvailableException {
        Assert.assertTrue(testInformation.getDevice().doesFileExist(mImgdiagOutPath));

        String zygoteChildren =
                testInformation
                        .getDevice()
                        .executeShellCommand("ps --ppid `pgrep -f zygote64 -o` -o pid,args");

        // Skip "PID ARGS" header.
        for (String line : zygoteChildren.lines().skip(1).toList()) {
            String[] vals = line.strip().split("\\s+");
            Assert.assertEquals(2, vals.length);

            String targetPid = vals[0];
            String targetName = vals[1];

            String outFileName = String.format("imgdiag_%s_%s.txt", targetName, targetPid);
            String outFilePath = new File(mImgdiagOutPath, outFileName).getAbsolutePath();

            String imgdiagCmd = AppLaunchImgdiagTest.getImgdiagRunCmd(targetPid, outFilePath);
            testInformation.getDevice().executeShellCommand(imgdiagCmd);

            File imgdiagFile = testInformation.getDevice().pullFile(outFilePath);
            mTestLogger.testLog(
                    outFileName, LogDataType.HOST_LOG, new FileInputStreamSource(imgdiagFile));
        }
    }
}
