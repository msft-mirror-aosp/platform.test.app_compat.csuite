/*
 * Copyright (C) 2019 The Android Open Source Project
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


import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.testng.Assert.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

@RunWith(JUnit4.class)
public final class AppSetupPreparerTest {

  @Rule public TemporaryFolder testSourceFolder = new TemporaryFolder();
  @Rule public TemporaryFolder testDestFolder = new TemporaryFolder();

  private ITestDevice mockDevice;
  private IDeviceBuildInfo mockDeviceBuildInfo;
  private TestAppInstallSetup mockAppInstallSetup;

  @Before
  public void setUp() throws Exception {
    mockDevice = mock(ITestDevice.class);

    File testDir = testDestFolder.newFolder("download_dir");
    mockDeviceBuildInfo = mock(IDeviceBuildInfo.class);
    when(mockDeviceBuildInfo.getTestsDir()).thenReturn(testDir);

    mockAppInstallSetup = mock(TestAppInstallSetup.class);
  }

  @Test
  public void setUp_baseDirIsNull_throwsException()
      throws DeviceNotAvailableException, TargetSetupError {
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", null, mockAppInstallSetup);

    assertThrows(NullPointerException.class,
        () -> preparer.setUp(mockDevice, mockDeviceBuildInfo));
  }

  @Test
  public void setUp_baseDirIsNotDir_throwsException()
      throws IOException, DeviceNotAvailableException, TargetSetupError {
    File tempFile = testSourceFolder.newFile("temp_file_name");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", tempFile, mockAppInstallSetup);

    assertThrows(IllegalArgumentException.class,
        () -> preparer.setUp(mockDevice, mockDeviceBuildInfo));
  }

  @Test
  public void setUp_packageDirDoesNotExist_throwsError()
      throws IOException, DeviceNotAvailableException, TargetSetupError {
    File baseDir = testSourceFolder.newFolder("base_dir");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);

    assertThrows(IllegalArgumentException.class,
        () -> preparer.setUp(mockDevice, mockDeviceBuildInfo));
  }

  @Test
  public void prepareDownloadDir_containsStaleFiles() throws IOException, TargetSetupError {
    File baseDir = testSourceFolder.newFolder("base_dir");
    File staleDownloadDir = testDestFolder.newFolder("stale_download_dir");
    File staleFile = new File(staleDownloadDir, "stale_file");
    staleFile.createNewFile();
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup) {
          @Override
          protected File getDownloadDir(IBuildInfo buildInfo) {

            return staleDownloadDir;
          }
        };

    File downloadDir = preparer.prepareDownloadDir(mockDeviceBuildInfo);

    assertFalse(new File(downloadDir, "stale_file.apk").exists());
  }

    @Test
  public void downloadPackage_success() throws IOException, TargetSetupError {
    File baseDir = testSourceFolder.newFolder("base_dir");
    createPackageFile(baseDir, "package_name", "apk_name_1.apk");
    createPackageFile(baseDir, "package_name", "apk_name_2.apk");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);
    File destDownloadDir = testDestFolder.newFolder("dest_dir_name");

    preparer.downloadPackage(destDownloadDir);

    assertTrue(new File(destDownloadDir, "apk_name_1.apk").exists());
    assertTrue(new File(destDownloadDir, "apk_name_2.apk").exists());
  }

    @Test
    public void setUp_apkDoesNotExist() throws Exception {
    File baseDir = testSourceFolder.newFolder("base_dir");
    // Create a file in package_name folder, but the file extension is not apk.
    createPackageFile(baseDir, "package_name", "non_apk_file");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);

    assertThrows(TargetSetupError.class,
        () -> preparer.setUp(mockDevice, mockDeviceBuildInfo));
  }

    @Test
    public void setUp_installSplitApk() throws Exception {
    File baseDir = testSourceFolder.newFolder("base_dir");
    createPackageFile(baseDir, "package_name", "apk_name_1.apk");
    createPackageFile(baseDir, "package_name", "apk_name_2.apk");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);

    preparer.setUp(mockDevice, mockDeviceBuildInfo);

    verify(mockAppInstallSetup, times(1)).setAltDir(any());
    verify(mockAppInstallSetup, times(1)).addSplitApkFileNames(anyString());
    verify(mockAppInstallSetup, times(1)).setUp(any(), any());
  }

    @Test
    public void setUp_installNonSplitApk() throws Exception {
    File baseDir = testSourceFolder.newFolder("base_dir");
    createPackageFile(baseDir, "package_name", "apk_name_1.apk");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);

    preparer.setUp(mockDevice, mockDeviceBuildInfo);

    verify(mockAppInstallSetup, times(1)).setAltDir(any());
    verify(mockAppInstallSetup, times(1)).addTestFileName(anyString());
    verify(mockAppInstallSetup, times(1)).setUp(any(), any());
  }

    @Test
    public void tearDown() throws Exception {
    File baseDir = testSourceFolder.newFolder("base_dir");
    createPackageFile(baseDir, "package_name", "apk_name_1.apk");
    createPackageFile(baseDir, "package_name", "apk_name_2.apk");
    AppSetupPreparer preparer =
        new AppSetupPreparer("package_name", baseDir, mockAppInstallSetup);
    preparer.setUp(mockDevice, mockDeviceBuildInfo);

    preparer.tearDown(mockDevice, mockDeviceBuildInfo, mock(Throwable.class));

    File destDir = preparer.getDownloadDir(mockDeviceBuildInfo);
    assertFalse(new File(destDir, "apk_name_1.apk").exists());
    assertFalse(new File(destDir, "apk_name_2.apk").exists());
    verify(mockAppInstallSetup, times(1)).tearDown(any(), any(), any());
  }

  private File createPackageFile(File parentDir, String packageName, String apkName)
      throws IOException {
    File packageDir = new File(parentDir, packageName);
    packageDir.mkdirs();
    File apkFile = new File(packageDir, apkName);
    apkFile.createNewFile();

    return apkFile;
  }
}
