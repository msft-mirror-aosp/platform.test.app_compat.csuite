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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public final class DeviceUtilsTest {
    ITestDevice mDevice = Mockito.mock(ITestDevice.class);

    @Test
    public void runWithScreenRecording_deviceCommandFailed_jobIsExecuted() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellCommand(Mockito.startsWith("pidof screenrecord"))).thenReturn("");
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        DeviceUtils.runWithScreenRecording(mDevice, job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_deviceCommandSucceed_jobIsExecuted() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mDevice.executeShellCommand(Mockito.startsWith("pidof screenrecord")))
                .thenReturn("123");
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        DeviceUtils.runWithScreenRecording(mDevice, job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_deviceCommandSucceed_returnsVideoFile() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mDevice.executeShellCommand(Mockito.startsWith("pidof screenrecord")))
                .thenReturn("123");
        File videoFileOnDevice = new File("");
        when(mDevice.pullFile(Mockito.any())).thenReturn(videoFileOnDevice);

        File result = DeviceUtils.runWithScreenRecording(mDevice, () -> {});

        assertThat(result).isEqualTo(videoFileOnDevice);
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = DeviceUtils.getPackageVersionName(mDevice, "any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createSuccessfulCommandResultWithStdout("notVersion"));

        String result = DeviceUtils.getPackageVersionName(mDevice, "any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = DeviceUtils.getPackageVersionName(mDevice, "any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = DeviceUtils.getPackageVersionCode(mDevice, "any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createSuccessfulCommandResultWithStdout("notVersion"));

        String result = DeviceUtils.getPackageVersionCode(mDevice, "any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = DeviceUtils.getPackageVersionCode(mDevice, "any");

        assertThat(result).isEqualTo("123");
    }

    private static CommandResult createSuccessfulCommandResult() {
        return createSuccessfulCommandResultWithStdout("");
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }
}
