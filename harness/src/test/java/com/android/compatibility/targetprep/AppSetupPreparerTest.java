/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.compatibility.targetprep;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

@RunWith(JUnit4.class)
public final class AppSetupPreparerTest {
    private static final ITestDevice NULL_DEVICE = null;
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final Answer<Object> EMPTY_ANSWER = (i) -> null;

    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void setUp_gcsApkDirOptionNotSet_doesNotInstall() throws Exception {
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        AppSetupPreparer preparer = preparerBuilder().setInstaller(installer).build();
        IBuildInfo buildInfo = new BuildInfo();

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(installer, never()).addTestFile(any());
    }

    @Test
    public void setUp_gcsApkDirIsNotDir_throwsException() throws Exception {
        IBuildInfo buildInfo = new BuildInfo();
        File tempFile = tempFolder.newFile("temp_file_name");
        buildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, tempFile.getPath());
        AppSetupPreparer preparer = preparerBuilder().build();

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_installsApksInDirectory() throws Exception {
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        AppSetupPreparer preparer = preparerBuilder().setInstaller(installer).build();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        IBuildInfo buildInfo = new BuildInfo();
        buildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(installer).addTestFile(packageDir);
        verify(installer).setUp(any(), any());
    }

    @Test
    public void setUp_unresolvedAppUri_installs() throws Exception {
        String appUri = "app://com.example.app";
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_TEST_FILE_NAME, appUri)
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(installer).addTestFile(new File(appUri));
    }

    @Test
    public void tearDown_forwardsToInstaller() throws Exception {
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        AppSetupPreparer preparer = preparerBuilder().setInstaller(installer).build();
        TestInformation testInfo = TestInformation.newBuilder().build();

        preparer.tearDown(testInfo, null);

        verify(installer).tearDown(testInfo, null);
    }

    @Test
    public void setUp_withinRetryLimit_doesNotThrowException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(installer)
                .setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsRetryLimit_throwsException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doThrow(new TargetSetupError("Still failing"))
                .doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(installer)
                .setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_negativeTimeout_throwsException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setOption(AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "-1")
                        .build();

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_withinTimeout_doesNotThrowException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER)).when(installer).setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "1000")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsTimeout_throwsException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER)).when(installer).setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5")
                        .build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_timesOutWithoutExceedingRetryLimit_doesNotThrowException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER))
                .doNothing()
                .when(installer)
                .setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .setOption(AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_timesOutAndExceedsRetryLimit_doesNotThrowException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER)).when(installer).setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .setOption(AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5")
                        .build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_zeroMaxRetry_runsOnce() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doNothing().when(installer).setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "0")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(installer).setUp(any(), any());
    }

    @Test
    public void setUp_positiveMaxRetryButNoException_runsOnlyOnce() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doNothing().when(installer).setUp(any(), any());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(installer).setUp(any(), any());
    }

    @Test
    public void setUp_negativeMaxRetry_throwsException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder().setOption(AppSetupPreparer.OPTION_MAX_RETRY, "-1").build();

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndCheckDeviceAvailable_throwsDeviceNotAvailableException()
            throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "true")
                        .build();
        ITestDevice device = createUnavailableDevice();

        assertThrows(DeviceNotAvailableException.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_deviceConnectedAndCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "true")
                        .build();
        ITestDevice device = createAvailableDevice();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndNotCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "false")
                        .build();
        ITestDevice device = createUnavailableDevice();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_deviceNotAvailableAndWaitEnabled_throwsDeviceNotAvailableException()
            throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "1")
                        .build();
        ITestDevice device = createUnavailableDevice();

        assertThrows(DeviceNotAvailableException.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_deviceAvailableAndWaitEnabled_doesNotChangeException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "1")
                        .build();
        ITestDevice device = createAvailableDevice();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_deviceNotAvailableAndWaitDisabled_doesNotChangeException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(
                                mockInstallerThatThrows(
                                        new TargetSetupError("Connection reset by peer.")))
                        .setOption(AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "-1")
                        .build();
        ITestDevice device = createUnavailableDevice();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, buildInfo));
    }

    @Test
    public void setUp_negativeExponentialBackoffMultiplier_throwsIllegalArgumentException()
            throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setOption(
                                AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS,
                                "-1")
                        .build();

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_testFileNameOptionSet_forwardsToInstaller() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        doNothing().when(installer).addTestFile(captor.capture());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_TEST_FILE_NAME, "additional1.apk")
                        .setOption(AppSetupPreparer.OPTION_TEST_FILE_NAME, "additional2.apk")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);

        assertThat(captor.getAllValues())
                .containsAllOf(new File("additional1.apk"), new File("additional2.apk"));
    }

    @Test
    public void setUp_installArgOptionSet_forwardsToInstaller() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        doNothing().when(installer).addInstallArg(captor.capture());
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setInstaller(installer)
                        .setOption(AppSetupPreparer.OPTION_INSTALL_ARG, "-arg1")
                        .setOption(AppSetupPreparer.OPTION_INSTALL_ARG, "-arg2")
                        .build();

        preparer.setUp(NULL_DEVICE, buildInfo);

        assertThat(captor.getAllValues()).containsExactly("-arg1", "-arg2");
    }

    @Test
    public void setUp_zeroExponentialBackoffMultiplier_noSleepBetweenRetries() throws Exception {
        FakeSleeper fakeSleeper = new FakeSleeper();
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setSleeper(fakeSleeper)
                        .setInstaller(mockInstallerThatThrows(new TargetSetupError("Oops")))
                        .setOption(
                                AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "0")
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "1")
                        .build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(fakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    public void setUp_positiveExponentialBackoffMultiplier_sleepsBetweenRetries() throws Exception {
        FakeSleeper fakeSleeper = new FakeSleeper();
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setSleeper(fakeSleeper)
                        .setInstaller(mockInstallerThatThrows(new TargetSetupError("Oops")))
                        .setOption(
                                AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3")
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "3")
                        .build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(fakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(3));
        assertThat(fakeSleeper.getSleepHistory().get(1)).isEqualTo(Duration.ofSeconds(9));
        assertThat(fakeSleeper.getSleepHistory().get(2)).isEqualTo(Duration.ofSeconds(27));
    }

    @Test
    public void setUp_interruptedDuringBackoff_throwsException() throws Exception {
        FakeSleeper fakeSleeper = new FakeInterruptedSleeper();
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                preparerBuilder()
                        .setSleeper(fakeSleeper)
                        .setInstaller(mockInstallerThatThrows(new TargetSetupError("Oops")))
                        .setOption(
                                AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3")
                        .setOption(AppSetupPreparer.OPTION_MAX_RETRY, "3")
                        .build();

        try {
            assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(fakeSleeper.getSleepHistory().size()).isEqualTo(1);
        } finally {
            // Clear interrupt to not interfere with future tests.
            Thread.interrupted();
        }
    }

    private TestAppInstallSetup mockInstallerThatThrows(Exception e) throws Exception {
        TestAppInstallSetup installer = mock(TestAppInstallSetup.class);
        doThrow(e).when(installer).setUp(any(), any());
        return installer;
    }

    private IBuildInfo createValidBuildInfo() throws Exception {
        IBuildInfo buildInfo = new BuildInfo();
        File gcsApkDir = tempFolder.newFolder("any");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "test.apk");
        buildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());
        return buildInfo;
    }

    private static ITestDevice createUnavailableDevice() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getProperty(any())).thenReturn(null);
        doThrow(new DeviceNotAvailableException("_", "serial"))
                .when(device)
                .waitForDeviceAvailable(anyLong());
        return device;
    }

    private static ITestDevice createAvailableDevice() throws Exception {
        ITestDevice device = mock(ITestDevice.class);
        when(device.getProperty(any())).thenReturn("");
        when(device.waitForDeviceShell(anyLong())).thenReturn(true);
        doNothing().when(device).waitForDeviceAvailable(anyLong());

        return device;
    }

    private static File createPackageFile(File parentDir, String packageName, String apkName)
            throws IOException {
        File packageDir =
                Files.createDirectories(Paths.get(parentDir.getAbsolutePath(), packageName))
                        .toFile();

        return Files.createFile(Paths.get(packageDir.getAbsolutePath(), apkName)).toFile();
    }

    private static class FakeSleeper implements AppSetupPreparer.Sleeper {
        private ArrayList<Duration> mSleepHistory = new ArrayList<>();

        @Override
        public void sleep(Duration duration) throws InterruptedException {
            mSleepHistory.add(duration);
        }

        ArrayList<Duration> getSleepHistory() {
            return mSleepHistory;
        }
    }

    private static class FakeInterruptedSleeper extends FakeSleeper {
        @Override
        public void sleep(Duration duration) throws InterruptedException {
            super.sleep(duration);
            throw new InterruptedException("_");
        }
    }

    private static final class PreparerBuilder {

        private AppSetupPreparer.Sleeper mSleeper = new FakeSleeper();
        private TestAppInstallSetup mInstaller = mock(TestAppInstallSetup.class);
        private final ListMultimap<String, String> mOptions = ArrayListMultimap.create();

        PreparerBuilder setSleeper(AppSetupPreparer.Sleeper sleeper) {
            this.mSleeper = sleeper;
            return this;
        }

        PreparerBuilder setInstaller(TestAppInstallSetup installer) {
            this.mInstaller = installer;
            return this;
        }

        PreparerBuilder setOption(String key, String value) {
            mOptions.put(key, value);
            return this;
        }

        AppSetupPreparer build() throws ConfigurationException {
            AppSetupPreparer preparer = new AppSetupPreparer(null, mInstaller, mSleeper);
            OptionSetter optionSetter = new OptionSetter(preparer);

            for (Map.Entry<String, String> e : mOptions.entries()) {
                optionSetter.setOptionValue(e.getKey(), e.getValue());
            }

            return preparer;
        }
    }

    private static PreparerBuilder preparerBuilder() {
        return new PreparerBuilder().setOption("package-name", TEST_PACKAGE_NAME);
    }
}
