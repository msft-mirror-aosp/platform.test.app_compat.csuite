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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.TestAppInstallSetup;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** A Tradefed preparer that downloads and installs an app on the target device. */
public final class AppSetupPreparer implements ITargetPreparer {

    public static final String OPTION_GCS_APK_DIR = "gcs-apk-dir";
    private static final String OPTION_CHECK_DEVICE_AVAILABLE = "check-device-available";
    private static final String OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS =
            "exponential-backoff-multiplier-seconds";
    private static final String OPTION_MAX_RETRY = "max-retry";

    @Option(
            name = "test-file-name",
            description = "the name of an apk file to be installed on device. Can be repeated.")
    private Collection<String> mTestFileNames = new ArrayList<String>();

    @Option(name = "package-name", description = "Package name of the app being tested.")
    private String mPackageName;

    @Option(name = OPTION_MAX_RETRY, description = "Max number of retries upon TargetSetupError.")
    private int mMaxRetry = 0;

    @Option(
            name = OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS,
            description =
                    "The exponential backoff multiplier for retries in seconds ."
                            + "A value n means the preparer will wait for n^(retry_count) "
                            + "seconds between retries.")
    private int mExponentialBackoffMultiplierSeconds = 0;

    @Option(
            name = OPTION_CHECK_DEVICE_AVAILABLE,
            description = "Whether to check device avilibility upon setUp failure.")
    private boolean mCheckDeviceAvailable = false;

    private final TestAppInstallSetup mTestAppInstallSetup;
    private final Sleeper mSleeper;

    public AppSetupPreparer() {
        this(null, new TestAppInstallSetup(), Sleepers.DefaultSleeper.INSTANCE);
    }

    @VisibleForTesting
    public AppSetupPreparer(
            String packageName, TestAppInstallSetup testAppInstallSetup, Sleeper sleeper) {
        mPackageName = packageName;
        mTestAppInstallSetup = testAppInstallSetup;
        mSleeper = sleeper;
    }

    /** {@inheritDoc} */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        checkArgumentNonNegative(mMaxRetry, OPTION_MAX_RETRY);
        checkArgumentNonNegative(
                mExponentialBackoffMultiplierSeconds,
                OPTION_EXPONENTIAL_BACKOFF_MULTIPLIER_SECONDS);

        int runCount = 0;
        while (true) {
            try {
                runCount++;
                setUpOnce(device, buildInfo);
                break;
            } catch (TargetSetupError e) {
                checkDeviceAvailable(device);
                if (runCount > mMaxRetry) {
                    throw e;
                }
                CLog.w("setUp failed: %s. Run count: %d. Retrying...", e, runCount);
            }

            try {
                mSleeper.sleep(
                        Duration.ofSeconds(
                                (int) Math.pow(mExponentialBackoffMultiplierSeconds, runCount)));
            } catch (InterruptedException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public void setUpOnce(ITestDevice device, IBuildInfo buildInfo)
            throws DeviceNotAvailableException, BuildError, TargetSetupError {
        // TODO(b/147159584): Use a utility to get dynamic options.
        String gcsApkDirOption = buildInfo.getBuildAttributes().get(OPTION_GCS_APK_DIR);
        checkNotNull(gcsApkDirOption, "Option %s is not set.", OPTION_GCS_APK_DIR);

        File apkDir = new File(gcsApkDirOption);
        checkArgument(
                apkDir.isDirectory(),
                String.format("GCS Apk Directory %s is not a directory", apkDir));

        File packageDir = new File(apkDir.getPath(), mPackageName);
        checkArgument(
                packageDir.isDirectory(),
                String.format("Package directory %s is not a directory", packageDir));

        mTestAppInstallSetup.setAltDir(packageDir);

        List<String> apkFilePaths;
        try {
            apkFilePaths = listApkFilePaths(packageDir);
        } catch (IOException e) {
            throw new TargetSetupError(
                    String.format("Failed to access files in %s.", packageDir), e);
        }

        if (apkFilePaths.isEmpty()) {
            throw new TargetSetupError(
                    String.format("Failed to find apk files in %s.", packageDir));
        }

        for (String testFileName : mTestFileNames) {
            mTestAppInstallSetup.addTestFileName(testFileName);
        }

        if (apkFilePaths.size() == 1) {
            mTestAppInstallSetup.addTestFileName(apkFilePaths.get(0));
        } else {
            mTestAppInstallSetup.addSplitApkFileNames(String.join(",", apkFilePaths));
        }

        mTestAppInstallSetup.setUp(device, buildInfo);
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) throws DeviceNotAvailableException {
        mTestAppInstallSetup.tearDown(testInfo, e);
    }

    private List<String> listApkFilePaths(File downloadDir) throws IOException {
        return Files.walk(Paths.get(downloadDir.getPath()))
                .map(x -> x.getFileName().toString())
                .filter(s -> s.endsWith(".apk"))
                .collect(Collectors.toList());
    }

    private void checkDeviceAvailable(ITestDevice device) throws DeviceNotAvailableException {
        if (!mCheckDeviceAvailable) {
            return;
        }

        // Throw an exception for TF to retry the invocation if the device is no longer available
        // since retrying would be useless. Ideally we would wait for the device to recover but
        // that is currently not supported in TradeFed.
        if (device.getProperty("_") == null) {
            throw new DeviceNotAvailableException(
                    "getprop command failed. Might have lost connection to the device.");
        }
    }

    private void checkArgumentNonNegative(int val, String name) {
        checkArgument(val >= 0, "%s (%s) must not be negative", name, val);
    }

    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static class Sleepers {
        enum DefaultSleeper implements Sleeper {
            INSTANCE;

            @Override
            public void sleep(Duration duration) throws InterruptedException {
                Thread.sleep(duration.toMillis());
            }
        }

        private Sleepers() {}
    }
}
