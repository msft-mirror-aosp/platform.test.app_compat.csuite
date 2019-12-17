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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;
import com.android.tradefed.util.FileUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Tradefed preparer that downloads and installs an app on the target device.
 */
public class AppSetupPreparer implements ITargetPreparer, ITargetCleaner {

  @Option(
      name = "package-name",
      description = "Package name of the app being tested."
  )
  private String mPackageName;

  @Option(
      name = "base-dir",
      description = "The directory where app APKs are located.",
      importance = Option.Importance.ALWAYS
  )
  private File mBaseDir;

  private TestAppInstallSetup appInstallSetup;

  public AppSetupPreparer() {
    this(null, null, new TestAppInstallSetup());
  }

  @VisibleForTesting
  public AppSetupPreparer(String packageName, File baseDir, TestAppInstallSetup appInstallSetup) {
    this.mPackageName = packageName;
    this.mBaseDir = baseDir;
    this.appInstallSetup = appInstallSetup;
  }

  /** {@inheritDoc} */
  @Override
  public void setUp(ITestDevice device, IBuildInfo buildInfo)
      throws DeviceNotAvailableException, TargetSetupError {

    checkNotNull(mBaseDir, "mBaseDir cannot be null.");
    checkArgument(mBaseDir.isDirectory(),
        String.format("mBaseDir %s is not a directory", mBaseDir));

    File downloadDir = prepareDownloadDir(buildInfo);

    try {
      downloadPackage(downloadDir);
    } catch (IOException e) {
      throw new TargetSetupError
          (String.format("Failed to download package from %s.", downloadDir), e);
    }
    appInstallSetup.setAltDir(downloadDir);

    List<String> apkFiles;
    try {
      apkFiles = listApkFiles(downloadDir);
    } catch (IOException e) {
      throw new TargetSetupError(String.format("Failed to access files in %s.", downloadDir), e);
    }

    if (apkFiles.isEmpty()) {
      throw new TargetSetupError(String.format("Failed to find apk files in %s.", downloadDir));
    }

    if (apkFiles.size() == 1) {
      appInstallSetup.addTestFileName(apkFiles.get(0));
    } else {
      appInstallSetup.addSplitApkFileNames(String.join(",", apkFiles));
    }

    appInstallSetup.setUp(device, buildInfo);
  }

  /** {@inheritDoc} */
  @Override
  public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
      throws DeviceNotAvailableException {
    deleteDownloadDir(buildInfo);
    appInstallSetup.tearDown(device, buildInfo, e);
  }

  protected File prepareDownloadDir(IBuildInfo buildInfo) throws TargetSetupError {
    File downloadDir = deleteDownloadDir(buildInfo);

    try {
      Files.createDirectory(Paths.get(downloadDir.getPath()));
    } catch (IOException e) {
      throw new TargetSetupError(
          String.format("Failed to create download directory %s.", downloadDir), e);
    }

    return downloadDir;
  }

  private File deleteDownloadDir(IBuildInfo buildInfo) {
    File downloadDir = getDownloadDir(buildInfo);
    FileUtil.recursiveDelete(downloadDir);

    return downloadDir;
  }

  protected void downloadPackage(File destDir) throws IOException {
    File sourceDir = new File(mBaseDir.getPath(), mPackageName);

    checkArgument(sourceDir.isDirectory(),
        String.format("sourceDir %s is not a directory", sourceDir));

    FileUtil.recursiveCopy(sourceDir, destDir);
  }

  private List<String> listApkFiles(File downloadDir) throws IOException {
    return Files.walk(Paths.get(downloadDir.getPath()))
        .filter(s -> s.toString().endsWith("apk"))
        .map(x -> x.getFileName().toString()).collect(Collectors.toList());
  }

  protected File getDownloadDir(IBuildInfo buildInfo) {
    checkArgument(buildInfo instanceof IDeviceBuildInfo,
        String.format("Provided buildInfo is not a %s", IDeviceBuildInfo.class.getCanonicalName()));

    return new File(((IDeviceBuildInfo) buildInfo).getTestsDir(), mPackageName);
  }
}
