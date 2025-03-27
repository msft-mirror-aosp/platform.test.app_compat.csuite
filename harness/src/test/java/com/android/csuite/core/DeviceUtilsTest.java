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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public final class DeviceUtilsTest {
    private ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Test
    public void grantExternalStoragePermissions_commandFailed_doesNotThrow() throws Exception {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(mDevice.executeShellV2Command(captor.capture()))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        sut.grantExternalStoragePermissions(TEST_PACKAGE_NAME);

        assertThat(captor.getValue()).contains("MANAGE_EXTERNAL_STORAGE allow");
    }

    @Test
    public void isPackageInstalled_packageIsInstalled_returnsTrue() throws Exception {
        String packageName = "package.name";
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout("\npackage:" + packageName + "\n"));
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isPackageInstalled(packageName);

        assertTrue(res);
    }

    @Test
    public void isPackageInstalled_packageIsNotInstalled_returnsFalse() throws Exception {
        String packageName = "package.name";
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();

        boolean res = sut.isPackageInstalled(packageName);

        assertFalse(res);
    }

    @Test
    public void isPackageInstalled_commandFailed_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.isPackageInstalled("package.name"));
    }

    @Test
    public void launchPackage_pmDumpFailedAndPackageDoesNotExist_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout("no packages"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("package.name"));
    }

    @Test
    public void launchPackage_pmDumpFailedAndPackageExists_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm list packages")))
                .thenReturn(createSuccessfulCommandResultWithStdout("package:package.name"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("package.name"));
    }

    @Test
    public void launchPackage_amStartCommandFailed_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("com.google.android.gms"));
    }

    @Test
    public void launchPackage_amFailedToLaunchThePackage_throws() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "Error: Activity not started, unable to resolve Intent"));
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("com.google.android.gms"));
    }

    @Test
    public void launchPackage_monkeyFailedButAmSucceed_doesNotThrow() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "        87f1610"
                                    + " com.google.android.gms/.app.settings.GoogleSettingsActivity"
                                    + " filter 7357509\n"
                                    + "          Action: \"android.intent.action.MAIN\"\n"
                                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                                    + "          Category:"
                                    + " \"android.intent.category.NOTIFICATION_PREFERENCES\""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();

        sut.launchPackage("com.google.android.gms");
    }

    @Test
    public void launchPackage_monkeySucceed_doesNotThrow() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        when(mDevice.executeShellV2Command(Mockito.startsWith("pm dump")))
                .thenReturn(createFailedCommandResult());
        when(mDevice.executeShellV2Command(Mockito.startsWith("am start")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        sut.launchPackage("package.name");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncherAndMainAndDefault_returnsIt()
            throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncherAndMain_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void
            getLaunchActivity_oneActivityIsLauncherAndOneActivityIsMain_returnsTheLauncherActivity()
                    throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.app.settings.GoogleSettingsActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsMain_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.bugreport.BugreportActivity");
    }

    @Test
    public void getLaunchActivity_oneActivityIsLauncher_returnsIt() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Category: \"android.intent.category.LAUNCHER\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Action: \"android.intent.action.MAIN\"\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        String res = sut.getLaunchActivity(pmDump);

        assertThat(res).isEqualTo("com.google.android.gms/.bugreport.BugreportActivity");
    }

    @Test
    public void getLaunchActivity_noMainOrLauncherActivities_throws() throws Exception {
        String pmDump =
                "        eecc562 com.google.android.gms/.bugreport.BugreportActivity filter"
                    + " ac016f3\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "        87f1610 com.google.android.gms/.app.settings.GoogleSettingsActivity"
                    + " filter 7357509\n"
                    + "          Category: \"android.intent.category.NOTIFICATION_PREFERENCES\"\n"
                    + "        28957f2 com.google.android.gms/.kids.SyncTailTrapperActivity filter"
                    + " 83cbcc0\n"
                    + "          Category: \"android.intent.category.HOME\"\n"
                    + "          Category: \"android.intent.category.DEFAULT\"\n"
                    + "          mPriority=10, mOrder=0, mHasStaticPartialTypes=false,"
                    + " mHasDynamicPartialTypes=false";
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.getLaunchActivity(pmDump));
    }

    @Test
    public void currentTimeMillis_deviceCommandFailed_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createFailedCommandResult());

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_unexpectedFormat_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_successful_returnsTime() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        DeviceTimestamp result = sut.currentTimeMillis();

        assertThat(result.get()).isEqualTo(Long.parseLong("123"));
    }

    @Test
    public void runWithScreenRecording_recordingDidNotStart_jobIsExecuted() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createFailedCommandResult());
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, (video, time) -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_recordCommandThrowsException_jobIsExecuted()
            throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenThrow(new IOException());
        DeviceUtils sut = createSubjectUnderTest();
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, (video, time) -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_jobThrowsException_videoFileIsHandled() throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();
        DeviceUtils.RunnableThrowingDeviceNotAvailable job =
                () -> {
                    throw new RuntimeException();
                };
        AtomicBoolean handled = new AtomicBoolean(false);

        assertThrows(
                RuntimeException.class,
                () -> sut.runWithScreenRecording(job, (video, time) -> handled.set(true)));

        assertThat(handled.get()).isTrue();
    }

    @Test
    public void getSdkLevel_returnsSdkLevelInteger() throws DeviceNotAvailableException {
        DeviceUtils sut = createSubjectUnderTest();
        int sdkLevel = 30;
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("" + sdkLevel));

        int result = sut.getSdkLevel();

        assertThat(result).isEqualTo(sdkLevel);
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_NAME_PREFIX));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_CODE_PREFIX));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo("123");
    }

    private DeviceUtils createSubjectUnderTest() throws DeviceNotAvailableException {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo ${EPOCHREALTIME")))
                .thenReturn(createSuccessfulCommandResultWithStdout("1"));
        when(mDevice.executeShellV2Command(Mockito.eq("getprop ro.build.version.sdk")))
                .thenReturn(createSuccessfulCommandResultWithStdout("34"));
        FakeClock fakeClock = new FakeClock();
        return new DeviceUtils(mDevice, fakeClock.getSleeper(), fakeClock, () -> mRunUtil);
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
