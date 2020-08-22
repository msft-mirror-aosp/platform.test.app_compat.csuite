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

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Uninstalls a system app.
 *
 * <p>This preparer class may not restore the uninstalled system app after test completes.
 *
 * <p>The preparer may disable dm verity on some devices, and it does not re-enable it after
 * uninstalling a system app.
 */
public final class SystemAppRemovalPreparer implements ITargetPreparer {
    @VisibleForTesting static final String OPTION_PACKAGE_NAME = "package-name";
    @VisibleForTesting static final String PACKAGE_XML_PATH = "/data/system/packages.xml";
    private static final String PACKAGE_PERMISSION_PATTERN =
            "\\s+<item name=\".*\" package=\"%s\".*/>";
    static final String SYSPROP_DEV_BOOTCOMPLETE = "dev.bootcomplete";
    static final String SYSPROP_SYS_BOOT_COMPLETED = "sys.boot_completed";
    static final long WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS = 1000 * 60;

    @Option(
            name = OPTION_PACKAGE_NAME,
            description = "The package name of the system app to be removed.",
            importance = Importance.ALWAYS)
    private String mPackageName;

    /** {@inheritDoc} */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        checkNotNull(mPackageName);

        if (!isPackageInstalled(mPackageName, device)) {
            CLog.i("Package %s is not installed.", mPackageName);
            return;
        }

        String packageInstallDirectory = getPackageInstallDirectory(mPackageName, device);
        CLog.d("Install directory for package %s is %s", mPackageName, packageInstallDirectory);

        if (!isPackagePathSystemApp(packageInstallDirectory)) {
            CLog.w("%s is not a system app, skipping", mPackageName);
            return;
        }

        CLog.i("Uninstalling system app %s", mPackageName);

        runWithWritableFilesystem(
                device,
                () -> {
                    removePackageInstallDirectory(packageInstallDirectory, device);
                    removePackageData(mPackageName, device);
                    removePackagePermissions(mPackageName, device);

                    // Restart Android framework for the above deletion to take effect.
                    restartFramework(device);
                });
    }

    private interface PreparerTask {
        void run() throws TargetSetupError, DeviceNotAvailableException;
    }

    private static void runWithWritableFilesystem(ITestDevice device, PreparerTask action)
            throws TargetSetupError, DeviceNotAvailableException {
        runAsRoot(
                device,
                () -> {
                    // TODO(yuexima): The remountSystemWritable method may internally disable dm
                    // verity on some devices. Consider restoring verity which would require a
                    // reboot.
                    device.remountSystemWritable();

                    try {
                        action.run();
                    } finally {
                        remountSystemReadOnly(device);
                    }
                });
    }

    private static void runAsRoot(ITestDevice device, PreparerTask action)
            throws TargetSetupError, DeviceNotAvailableException {
        boolean disableRootAfterUninstall = false;

        if (!device.isAdbRoot()) {
            if (!device.enableAdbRoot()) {
                throw new TargetSetupError("Failed to enable adb root");
            }

            disableRootAfterUninstall = true;
        }

        try {
            action.run();
        } finally {
            if (disableRootAfterUninstall && !device.disableAdbRoot()) {
                throw new TargetSetupError("Failed to disable adb root");
            }
        }
    }

    private static void restartFramework(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        // 'stop' is a blocking command.
        executeShellCommandOrThrow(device, "stop", "Failed to stop framework");
        // Set the boot complete flags to false. When the framework is started again, both flags
        // will be set to true by the system upon the completion of restarting. This allows
        // ITestDevice#waitForBootComplete to wait for framework start, and it only works
        // when adb is rooted.
        device.setProperty(SYSPROP_SYS_BOOT_COMPLETED, "0");
        device.setProperty(SYSPROP_DEV_BOOTCOMPLETE, "0");
        // 'start' is a non-blocking command.
        executeShellCommandOrThrow(device, "start", "Failed to start framework");
        device.waitForBootComplete(WAIT_FOR_BOOT_COMPLETE_TIMEOUT_MILLIS);
    }

    private static CommandResult executeShellCommandOrThrow(
            ITestDevice device, String command, String failureMessage)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult = device.executeShellV2Command(command);

        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            throw new TargetSetupError(
                    String.format("%s; Command result: %s", failureMessage, commandResult));
        }

        return commandResult;
    }

    private static CommandResult executeShellCommandOrLog(
            ITestDevice device, String command, String failureMessage)
            throws DeviceNotAvailableException {
        CommandResult commandResult = device.executeShellV2Command(command);
        if (commandResult.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("%s. Command result: %s", failureMessage, commandResult);
        }

        return commandResult;
    }

    private static void remountSystemReadOnly(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        executeShellCommandOrThrow(
                device,
                "mount -o ro,remount /system",
                "Failed to remount system partition as read only");
    }

    private static boolean isPackagePathSystemApp(String packagePath) {
        return packagePath.startsWith("/system/") || packagePath.startsWith("/product/");
    }

    /**
     * Removes system app's unchangeable permissions.
     *
     * <p>Some system apps may have 'unchangeable permissions' which cannot be modified through any
     * public APIs. We have to edit the packages.xml to force remove them. If we don't remove them,
     * the re-installation of the package will fail.
     */
    private static void removePackagePermissions(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CLog.d("Revoking package permissions for %s", packageName);
        Path packageXml = device.pullFile(PACKAGE_XML_PATH).toPath();
        if (packageXml == null) {
            throw new TargetSetupError(
                    String.format("Failed to pull package xml from device: %s", PACKAGE_XML_PATH));
        }

        try {
            Files.write(
                    packageXml,
                    Files.readAllLines(packageXml, StandardCharsets.UTF_8)
                            .stream()
                            .filter(
                                    line ->
                                            !line.matches(
                                                    String.format(
                                                            PACKAGE_PERMISSION_PATTERN,
                                                            packageName)))
                            .collect(Collectors.toList()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TargetSetupError(e.getMessage(), e);
        }

        if (!device.pushFile(packageXml.toFile(), PACKAGE_XML_PATH)) {
            throw new TargetSetupError(
                    String.format("Failed to push package xml from %s", packageXml));
        }
    }

    private static void removePackageInstallDirectory(
            String packageInstallDirectory, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CLog.i("Removing package install directory %s", packageInstallDirectory);
        executeShellCommandOrThrow(
                device,
                String.format("rm -r %s", packageInstallDirectory),
                String.format(
                        "Failed to remove system app package path %s", packageInstallDirectory));
    }

    private static void removePackageData(String packageName, ITestDevice device)
            throws DeviceNotAvailableException {
        String dataPath = String.format("/data/data/%s", packageName);
        CLog.i("Removing package data directory for %s", dataPath);
        executeShellCommandOrLog(
                device,
                String.format("rm -r %s", dataPath),
                String.format(
                        "Failed to remove system app data %s from %s", packageName, dataPath));
    }

    private static boolean isPackageInstalled(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult =
                executeShellCommandOrThrow(
                        device,
                        String.format("pm list packages %s", packageName),
                        "Failed to execute pm command");

        if (commandResult.getStdout() == null) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to get pm command output: %s", commandResult.getStdout()));
        }

        return Arrays.asList(commandResult.getStdout().split("\\r?\\n"))
                .contains(String.format("package:%s", packageName));
    }

    private static String getPackageInstallDirectory(String packageName, ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        CommandResult commandResult =
                executeShellCommandOrThrow(
                        device,
                        String.format("pm path %s", packageName),
                        "Failed to execute pm command");

        if (commandResult.getStdout() == null
                || !commandResult.getStdout().startsWith("package:")) {
            throw new TargetSetupError(
                    String.format(
                            "Failed to get pm path command output %s", commandResult.getStdout()));
        }

        String packageInstallPath = commandResult.getStdout().substring("package:".length());
        return Paths.get(packageInstallPath).getParent().toString();
    }
}
