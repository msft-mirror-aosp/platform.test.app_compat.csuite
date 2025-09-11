/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.GCSFileDownloader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OptionClass(alias = "gcs-testsdir-downloader-preparer")
public class GcsTestsDirDownloaderPreparer extends BaseTargetPreparer {

    @Option(name = "gcs-file-map", description = "A map of GCS paths to local file names. " +
            "Key: GCS path (e.g., gs://bucket/file.txt), Value: Local destination file name.", mandatory = true)
    private Map<String, String> gcsFileMap = new HashMap<>();

    private GCSFileDownloader mDownloader = null;
    private List<File> mDownloadedFiles = new ArrayList<>();

    protected GCSFileDownloader getDownloader() {
        if (mDownloader == null) {
            mDownloader = new GCSFileDownloader();
        }
        return mDownloader;
    }

    @Override
    public void setUp(TestInformation testInformation) throws TargetSetupError {
        if (isDisabled()) {
            CLog.i("GcsTestsDirDownloaderPreparer is disabled.");
            return;
        }

        ITestDevice device = testInformation.getDevice();
        IBuildInfo buildInfo = testInformation.getBuildInfo();

        if (buildInfo == null) {
            throw new TargetSetupError("IBuildInfo is null in TestInformation.", device.getDeviceDescriptor());
        }

        Path finalDestDir;
        try {
            finalDestDir = new CompatibilityBuildHelper(buildInfo).getTestsDir().toPath();
        } catch (IOException e) {
            throw new TargetSetupError("Failed to get tests directory from BuildInfo", e, device.getDeviceDescriptor());
        }

        try {
            Files.createDirectories(finalDestDir);
        } catch (IOException e) {
            throw new TargetSetupError("Failed to create tests directory: " + finalDestDir, e, device.getDeviceDescriptor());
        }

        for (Map.Entry<String, String> entry : gcsFileMap.entrySet()) {
            String gcsPath = entry.getKey();
            String localName = entry.getValue();

            if (localName.endsWith(".config")) {
                throw new TargetSetupError(
                        "The local-file-name value '" + localName + "' cannot end with '.config'.",
                        device.getDeviceDescriptor());
            }

            File destinationFile = finalDestDir.resolve(localName).toFile();

            if (destinationFile.exists()) {
                throw new TargetSetupError("Failed to download file from GCS as file already exists: " + destinationFile.getAbsolutePath(), device.getDeviceDescriptor());
            }

            File parentDir = destinationFile.getParentFile();

            try {
                Files.createDirectories(parentDir.toPath());
            } catch (IOException e) {
                throw new TargetSetupError("Failed to create parent directories for: " + destinationFile.getAbsolutePath(), e, device.getDeviceDescriptor());
            }

            try {
                CLog.i("Downloading from GCS: %s to %s", gcsPath, destinationFile.getAbsolutePath());
                getDownloader().downloadFile(gcsPath, destinationFile);
                if (!destinationFile.exists()) {
                    throw new TargetSetupError("Failed to download file from GCS: " + gcsPath + " (file not found after download call)", device.getDeviceDescriptor());
                }
                CLog.i("Successfully downloaded GCS file to %s", destinationFile.getAbsolutePath());
                mDownloadedFiles.add(destinationFile);
            } catch (BuildRetrievalError e) {
                throw new TargetSetupError("Error downloading file from GCS: " + gcsPath, e, device.getDeviceDescriptor());
            }
        }
    }

    @Override
    public void tearDown(TestInformation testInformation, Throwable e) {
        if (isDisabled()) {
            CLog.i("GcsTestsDirDownloaderPreparer is disabled.");
            return;
        }

        CLog.i("Tearing down GcsTestsDirDownloaderPreparer, deleting %d downloaded files.",
                mDownloadedFiles.size());

        for (File file : mDownloadedFiles) {
            try{
                boolean deleted = Files.deleteIfExists(file.toPath());
                if (!deleted) {
                    CLog.w("Failed to delete file: %s", file.getAbsolutePath());
                } else {
                    CLog.i("Successfully deleted file: %s", file.getAbsolutePath());
                }
            }catch (IOException ioException) {
                CLog.w("Failed to delete file: %s", file.getAbsolutePath());
                CLog.w(ioException);
            }
        }
        mDownloadedFiles.clear();
    }

}