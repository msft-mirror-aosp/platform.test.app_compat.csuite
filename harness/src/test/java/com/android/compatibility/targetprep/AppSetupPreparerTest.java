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
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

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
import java.util.List;

@RunWith(JUnit4.class)
public final class AppSetupPreparerTest {
    private static final ITestDevice NULL_DEVICE = null;
    private static final IBuildInfo NULL_BUILD_INFO = null;
    private static final String NULL_PACKAGE_NAME = null;
    private static final TestAppInstallSetup NULL_TEST_APP_INSTALL_SETUP = null;
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final Answer<Object> EMPTY_ANSWER = (i) -> null;

    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final IBuildInfo mBuildInfo = new BuildInfo();
    private final TestAppInstallSetup mMockAppInstallSetup = mock(TestAppInstallSetup.class);
    private FakeSleeper mFakeSleeper;
    private Configuration mConfiguration = new Configuration(null, null);

    @Test
    public void setUp_gcsApkDirIsNull_throwsException()
            throws DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, null);

        assertThrows(NullPointerException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_gcsApkDirIsNotDir_throwsException()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        File tempFile = tempFolder.newFile("temp_file_name");
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, tempFile.getPath());

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_packageDirDoesNotExist_throwsError()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_apkDoesNotExist() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "non_apk_file");
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_installSplitApk() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_2.apk");
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        preparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup)
                .addSplitApkFileNames(
                        argThat(s -> s.contains("apk_name_1.apk") && s.contains("apk_name_2.apk")));
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void setUp_installNonSplitApk() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        mBuildInfo.addBuildAttribute(AppSetupPreparer.OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        preparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup).addTestFileName("apk_name_1.apk");
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void tearDown() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        TestInformation testInfo = TestInformation.newBuilder().build();

        preparer.tearDown(testInfo, null);

        verify(mMockAppInstallSetup, times(1)).tearDown(testInfo, null);
    }

    @Test
    public void setUp_withinRetryLimit_doesNotThrowException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsRetryLimit_throwException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        doThrow(new TargetSetupError("Still failing"))
                .doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_negativeTimeout_throwsException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "-1");

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_withinTimeout_doesNotThrowException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "1000");
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER))
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsTimeout_throwsException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5");
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER))
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_timesOutWithoutExceedingRetryLimit_doesNotThrowException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5");
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_timesOutAndExceedsRetryLimit_doesNotThrowException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_SETUP_TIMEOUT_MILLIS, "5");
        doAnswer(new AnswersWithDelay(10, EMPTY_ANSWER))
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_zeroMaxRetry_runsOnce() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "0");
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_positiveMaxRetryButNoException_runsOnlyOnce() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_negativeMaxRetry_throwsException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "-1");

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndCheckDeviceAvailable_throwsDeviceNotAvailableException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "true");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                DeviceNotAvailableException.class,
                () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceConnectedAndCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "true");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createAvailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceDisconnectedAndNotCheckDeviceAvailable_doesNotChangeException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_CHECK_DEVICE_AVAILABLE, "false");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceNotAvailableAndWaitEnabled_throwsDeviceNotAvailableException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "1");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                DeviceNotAvailableException.class,
                () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceAvailableAndWaitEnabled_doesNotChangeException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "1");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createAvailableDevice(), buildInfo));
    }

    @Test
    public void setUp_deviceNotAvailableAndWaitDisabled_doesNotChangeException() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(
                preparer, AppSetupPreparer.OPTION_WAIT_FOR_DEVICE_AVAILABLE_SECONDS, "-1");
        makeInstallerThrow(new TargetSetupError("Connection reset by peer."));

        assertThrows(
                TargetSetupError.class, () -> preparer.setUp(createUnavailableDevice(), buildInfo));
    }

    @Test
    public void setUp_negativeExponentialBackoffMultiplier_throwsIllegalArgumentException()
            throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(
                preparer, AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "-1");

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_testFileNameOptionSet_forwardsToUnderlyingPreparer() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_TEST_FILE_NAME, "additional1.apk");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_TEST_FILE_NAME, "additional2.apk");
        ArgumentCaptor<String> stringArgCaptor = ArgumentCaptor.forClass(String.class);

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, atLeast(2)).addTestFileName(stringArgCaptor.capture());
        List<String> values = stringArgCaptor.getAllValues();
        assertThat(values).contains("additional1.apk");
        assertThat(values).contains("additional2.apk");
    }

    @Test
    public void setUp_installArgOptionSet_forwardsToUnderlyingPreparer() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(preparer, AppSetupPreparer.OPTION_INSTALL_ARG, "-arg1");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_INSTALL_ARG, "-arg2");
        ArgumentCaptor<String> stringArgCaptor = ArgumentCaptor.forClass(String.class);

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, atLeast(2)).addInstallArg(stringArgCaptor.capture());
        List<String> values = stringArgCaptor.getAllValues();
        assertThat(values).contains("-arg1");
        assertThat(values).contains("-arg2");
    }

    @Test
    public void setUp_zeroExponentialBackoffMultiplier_noSleepBetweenRetries() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(
                preparer, AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "0");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "1");
        makeInstallerThrow(new TargetSetupError(""));

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(mFakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(0));
    }

    @Test
    public void setUp_positiveExponentialBackoffMultiplier_sleepsBetweenRetries() throws Exception {
        AppSetupPreparer preparer = createPreparer();
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(
                preparer, AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "3");
        makeInstallerThrow(new TargetSetupError(""));

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
        assertThat(mFakeSleeper.getSleepHistory().get(0)).isEqualTo(Duration.ofSeconds(3));
        assertThat(mFakeSleeper.getSleepHistory().get(1)).isEqualTo(Duration.ofSeconds(9));
        assertThat(mFakeSleeper.getSleepHistory().get(2)).isEqualTo(Duration.ofSeconds(27));
    }

    @Test
    public void setUp_interruptedDuringBackoff_throwsException() throws Exception {
        FakeSleeper sleeper = new FakeInterruptedSleeper();
        AppSetupPreparer preparer = createPreparerWithSleeper(sleeper);
        IBuildInfo buildInfo = createValidBuildInfo();
        setPreparerOption(
                preparer, AppSetupPreparer.OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS, "3");
        setPreparerOption(preparer, AppSetupPreparer.OPTION_MAX_RETRY, "3");
        makeInstallerThrow(new TargetSetupError(""));

        try {
            assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(sleeper.getSleepHistory().size()).isEqualTo(1);
        } finally {
            // Clear interrupt to not interfere with future tests.
            Thread.interrupted();
        }
    }

    private void setPreparerOption(AppSetupPreparer preparer, String key, String val)
            throws Exception {
        new OptionSetter(preparer).setOptionValue(key, val);
    }

    private void makeInstallerThrow(Exception e) throws Exception {
        doThrow(e).when(mMockAppInstallSetup).setUp(any(), any());
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
        doThrow(new DeviceNotAvailableException("_"))
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

    private AppSetupPreparer createPreparer() {
        mFakeSleeper = new FakeSleeper();
        return createPreparerWithSleeper(mFakeSleeper);
    }

    private AppSetupPreparer createPreparerWithSleeper(FakeSleeper sleeper) {
        return new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, sleeper);
    }
}
