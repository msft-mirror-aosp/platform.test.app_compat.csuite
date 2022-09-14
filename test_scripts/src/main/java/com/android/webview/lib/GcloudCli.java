/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.webview.tests;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The WebView installer tool uses gsutil to download WebView apk's from GCS. The GcloudCli class
 * can be used to authenticate the host for using gsutil. This class does this by running 'gcloud
 * init' on the host machine. When the host machine runs the command, gcloud uses the application
 * default credentials to authenticate gsutil.
 */
public class GcloudCli {
    private static final long COMMAND_TIMEOUT_MILLIS = 5 * 60 * 1000;
    private File mGcloudCliDir;

    private RunUtil mRunUtil;

    private GcloudCli(File gcloudCliDir, RunUtil runUtil) {
        mRunUtil = runUtil;
        mGcloudCliDir = gcloudCliDir;
    }

    public static GcloudCli buildFromZipArchive(File gcloudCliZipArchive) throws IOException {
        Path gcloudCliDir = Files.createTempDirectory(null);
        try {
            CommandResult unzipRes =
                    RunUtil.getDefault()
                            .runTimedCmd(
                                    COMMAND_TIMEOUT_MILLIS,
                                    "unzip",
                                    gcloudCliZipArchive.getAbsolutePath(),
                                    "-d",
                                    gcloudCliDir.toFile().getAbsolutePath());
            Assert.assertEquals(
                    "Unable to unzip the gcloud cli zip archive",
                    unzipRes.getStatus(),
                    CommandStatus.SUCCESS);
            RunUtil runUtil = new RunUtil();
            // The 'gcloud init' command creates configuration files for gsutil and other
            // applications that use the gcloud sdk in the home directory. We can isolate
            // the effects of these configuration files to the processes that run the
            // gcloud and gsutil executables tracked by this class by setting the home
            // directory for processes that run those executables to a temporary directory
            // also tracked by this class.
            runUtil.setEnvVariable("HOME", gcloudCliDir.toFile().getAbsolutePath());
            File gcloudBin =
                    gcloudCliDir.resolve(Paths.get("google-cloud-sdk", "bin", "gcloud")).toFile();
            String gcloudInitScript =
                    String.format(
                            "printf \"1\\n1\" | %s init --console-only",
                            gcloudBin.getAbsolutePath());
            CommandResult gcloudInitRes =
                    runUtil.runTimedCmd(
                            COMMAND_TIMEOUT_MILLIS,
                            System.out,
                            System.out,
                            "sh",
                            "-c",
                            gcloudInitScript);
            Assert.assertEquals(
                    "gcloud cli initialization failed",
                    gcloudInitRes.getStatus(),
                    CommandStatus.SUCCESS);
            return new GcloudCli(gcloudCliDir.toFile(), runUtil);
        } catch (Exception e) {
            RunUtil.getDefault()
                    .runTimedCmd(
                            COMMAND_TIMEOUT_MILLIS,
                            "rm",
                            "-rf",
                            gcloudCliDir.toFile().getAbsolutePath());
            throw e;
        }
    }

    public File getGsutilBin() {
        return mGcloudCliDir
                .toPath()
                .resolve(Paths.get("google-cloud-sdk", "bin", "gsutil"))
                .toFile();
    }

    public RunUtil getRunUtil() {
        return mRunUtil;
    }

    public void tearDown() {
        RunUtil.getDefault()
                .runTimedCmd(COMMAND_TIMEOUT_MILLIS, "rm", "-rf", mGcloudCliDir.getAbsolutePath());
    }
}
