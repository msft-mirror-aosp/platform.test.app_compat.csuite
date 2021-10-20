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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
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
    DeviceUtils mDeviceUtils = DeviceUtils.getInstance(mDevice);

    @Test
    public void currentTimeMillis_deviceCommandFailed_throwsException() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createFailedCommandResult());

        assertThrows(DeviceRuntimeException.class, () -> mDeviceUtils.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_unexpectedFormat_throwsException() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        assertThrows(DeviceRuntimeException.class, () -> mDeviceUtils.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_successful_returnsTime() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        long result = mDeviceUtils.currentTimeMillis();

        assertThat(result).isEqualTo(Long.parseLong("123"));
    }

    @Test
    public void runWithScreenRecording_recordCommandThrowsException_jobIsExecuted()
            throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenThrow(new DeviceNotAvailableException("empty", "empty"));
        when(mDevice.executeShellV2Command(Mockito.startsWith("pidof screenrecord")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.pullFile(Mockito.any())).thenReturn(null);
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        mDeviceUtils.runWithScreenRecording(job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_recordCommandFailed_jobIsExecuted() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pidof screenrecord")))
                .thenReturn(createFailedCommandResult());
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        mDeviceUtils.runWithScreenRecording(job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_deviceCommandSucceed_jobIsExecuted() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pidof screenrecord")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        mDeviceUtils.runWithScreenRecording(job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_deviceCommandSucceed_returnsVideoFile() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pidof screenrecord")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));
        File videoFileOnDevice = new File("");
        when(mDevice.pullFile(Mockito.any())).thenReturn(videoFileOnDevice);

        File result = mDeviceUtils.runWithScreenRecording(() -> {});

        assertThat(result).isEqualTo(videoFileOnDevice);
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = mDeviceUtils.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_NAME_PREFIX));

        String result = mDeviceUtils.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = mDeviceUtils.getPackageVersionName("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = mDeviceUtils.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_CODE_PREFIX));

        String result = mDeviceUtils.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = mDeviceUtils.getPackageVersionCode("any");

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
