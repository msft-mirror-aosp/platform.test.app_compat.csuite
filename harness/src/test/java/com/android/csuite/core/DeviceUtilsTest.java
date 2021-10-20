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

import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public final class DeviceUtilsTest {
    ITestDevice mDevice = Mockito.mock(ITestDevice.class);

    @Test
    public void currentTimeMillis_deviceCommandFailed_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createFailedCommandResult());

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_unexpectedFormat_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_successful_returnsTime() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        long result = sut.currentTimeMillis();

        assertThat(result).isEqualTo(Long.parseLong("123"));
    }

    @Test
    public void runWithScreenRecording_recordingDidNotStart_jobIsExecuted() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createFailedCommandResult());
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        sut.runWithScreenRecording(job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_recordCommandThrowsException_jobIsExecuted()
            throws Exception {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        IRunUtil fakeRunUtil = Mockito.mock(IRunUtil.class);
        when(fakeRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenThrow(new IOException());
        FakeClock fakeClock = new FakeClock();
        DeviceUtils sut =
                new DeviceUtils(mDevice, fakeClock.getSleeper(), fakeClock, () -> fakeRunUtil);
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnerTask job = () -> executed.set(true);

        sut.runWithScreenRecording(job);

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_NAME_PREFIX));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_CODE_PREFIX));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        DeviceUtils sut = createSubjectUnderTest(mDevice);
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo("123");
    }

    private static DeviceUtils createSubjectUnderTest(ITestDevice device) throws IOException {
        when(device.getSerialNumber()).thenReturn("SERIAL");
        IRunUtil fakeRunUtil = Mockito.mock(IRunUtil.class);
        when(fakeRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        FakeClock fakeClock = new FakeClock();

        return new DeviceUtils(device, fakeClock.getSleeper(), fakeClock, () -> fakeRunUtil);
    }

    private static class FakeClock implements DeviceUtils.Clock {
        private long mCurrentTime = System.currentTimeMillis();
        private DeviceUtils.Sleeper mSleeper = duration -> mCurrentTime += duration;

        private DeviceUtils.Sleeper getSleeper() {
            return mSleeper;
        }

        @Override
        public long currentTimeMillis() {
            return mCurrentTime += 1;
        }
    }

    private static ArgumentMatcher<String[]> contains(String... args) {
        return array -> Arrays.asList(array).containsAll(Arrays.asList(args));
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
