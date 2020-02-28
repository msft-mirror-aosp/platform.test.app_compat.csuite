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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public final class AppSetupPreparerTest {

    private static final String OPTION_GCS_APK_DIR = "gcs-apk-dir";
    private static final ITestDevice NULL_DEVICE = null;
    private static final IBuildInfo NULL_BUILD_INFO = null;
    private static final String NULL_PACKAGE_NAME = null;
    private static final TestAppInstallSetup NULL_TEST_APP_INSTALL_SETUP = null;
    private static final String TEST_PACKAGE_NAME = "test.package.name";

    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final IBuildInfo mBuildInfo = new BuildInfo();
    private final TestAppInstallSetup mMockAppInstallSetup = mock(TestAppInstallSetup.class);
    private final AppSetupPreparer mPreparer =
            new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup);

    @Test
    public void setUp_gcsApkDirIsNull_throwsException()
            throws DeviceNotAvailableException, TargetSetupError {
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, null);

        assertThrows(NullPointerException.class, () -> mPreparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_gcsApkDirIsNotDir_throwsException()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        File tempFile = tempFolder.newFile("temp_file_name");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, tempFile.getPath());

        assertThrows(
                IllegalArgumentException.class, () -> mPreparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_packageDirDoesNotExist_throwsError()
            throws IOException, DeviceNotAvailableException, TargetSetupError {
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(
                IllegalArgumentException.class, () -> mPreparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_apkDoesNotExist() throws Exception {
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "non_apk_file");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        assertThrows(TargetSetupError.class, () -> mPreparer.setUp(NULL_DEVICE, mBuildInfo));
    }

    @Test
    public void setUp_installSplitApk() throws Exception {
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_2.apk");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        mPreparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup)
                .addSplitApkFileNames(
                        argThat(s -> s.contains("apk_name_1.apk") && s.contains("apk_name_2.apk")));
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void setUp_installNonSplitApk() throws Exception {
        File gcsApkDir = tempFolder.newFolder("gcs_apk_dir");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "apk_name_1.apk");
        mBuildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());

        mPreparer.setUp(NULL_DEVICE, mBuildInfo);

        verify(mMockAppInstallSetup).setAltDir(packageDir);
        verify(mMockAppInstallSetup).addTestFileName("apk_name_1.apk");
        verify(mMockAppInstallSetup).setUp(any(), any());
    }

    @Test
    public void tearDown() throws Exception {
        TestInformation testInfo = TestInformation.newBuilder().build();

        mPreparer.tearDown(testInfo, null);

        verify(mMockAppInstallSetup, times(1)).tearDown(testInfo, null);
    }

    @Test
    public void setUp_withinRetryLimit_doesNotThrowException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, 1);
        doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);
    }

    @Test
    public void setUp_exceedsRetryLimit_throwException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, 1);
        doThrow(new TargetSetupError("Still failing"))
                .doThrow(new TargetSetupError("Still failing"))
                .doNothing()
                .when(mMockAppInstallSetup)
                .setUp(any(), any());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    @Test
    public void setUp_zeroMaxRetry_runsOnce() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, 0);
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_positiveMaxRetryButNoException_runsOnlyOnce() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, 1);
        doNothing().when(mMockAppInstallSetup).setUp(any(), any());

        preparer.setUp(NULL_DEVICE, buildInfo);

        verify(mMockAppInstallSetup, times(1)).setUp(any(), any());
    }

    @Test
    public void setUp_negativeMaxRetry_throwsException() throws Exception {
        IBuildInfo buildInfo = createValidBuildInfo();
        AppSetupPreparer preparer =
                new AppSetupPreparer(TEST_PACKAGE_NAME, mMockAppInstallSetup, -1);

        assertThrows(IllegalArgumentException.class, () -> preparer.setUp(NULL_DEVICE, buildInfo));
    }

    private IBuildInfo createValidBuildInfo() throws Exception {
        IBuildInfo buildInfo = new BuildInfo();
        File gcsApkDir = tempFolder.newFolder("any");
        File packageDir = new File(gcsApkDir.getPath(), TEST_PACKAGE_NAME);
        createPackageFile(gcsApkDir, TEST_PACKAGE_NAME, "test.apk");
        buildInfo.addBuildAttribute(OPTION_GCS_APK_DIR, gcsApkDir.getPath());
        return buildInfo;
    }

    private static File createPackageFile(File parentDir, String packageName, String apkName)
            throws IOException {
        File packageDir =
                Files.createDirectories(Paths.get(parentDir.getAbsolutePath(), packageName))
                        .toFile();

        return Files.createFile(Paths.get(packageDir.getAbsolutePath(), apkName)).toFile();
    }
}
