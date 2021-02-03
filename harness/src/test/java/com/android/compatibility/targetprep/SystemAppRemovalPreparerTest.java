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


import static org.testng.Assert.assertThrows;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SystemAppRemovalPreparerTest {
    private static final ITestDevice NULL_DEVICE = null;
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final String SYSTEM_APP_INSTALL_DIRECTORY = "/system/app";
    private static final String CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX = "pm list packages ";
    private static final String GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX = "pm path ";
    private static final String REMOVE_SYSTEM_APP_COMMAND_PREFIX =
            "rm -r " + SYSTEM_APP_INSTALL_DIRECTORY;
    private static final String REMOVE_APP_DATA_COMMAND_PREFIX = "rm -r /data/data";
    private static final String MOUNT_COMMAND_PREFIX = "mount";

    @Test
    public void setUp_packageNameIsNull_throws() throws Exception {
        SystemAppRemovalPreparer preparer = new SystemAppRemovalPreparer();

        assertThrows(NullPointerException.class, () -> preparer.setUp(null, null));
    }

    @Test
    public void setUp_packageIsNotInstalled_doesNotRemove() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        // Mock the device as if the test package does not exist on device
        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout("");
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void setUp_differentPackageWithSameNamePrefixInstalled_doesNotRemove() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        // Mock the device as if the test package does not exist on device
        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(String.format("package:%s_some_more_chars", TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void setUp_checkPackageInstalledCommandFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_getInstallDirectoryCommandFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_packageIsNotSystemApp_doesNotRemove() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithUserAppInstalled();

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(0)).executeShellV2Command(Mockito.startsWith("rm"));
    }

    @Test
    public void setUp_adbAlreadyRooted_doesNotRootAgain() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.isAdbRoot()).thenReturn(true);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(0)).enableAdbRoot();
    }

    @Test
    public void setUp_adbNotAlreadyRooted_rootAdbAndThenUnroot() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.isAdbRoot()).thenReturn(false);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(1)).enableAdbRoot();
        Mockito.verify(device, Mockito.times(1)).disableAdbRoot();
    }

    @Test
    public void setUp_adbRootCommandFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.enableAdbRoot()).thenThrow(new DeviceNotAvailableException());

        assertThrows(DeviceNotAvailableException.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_adbRootFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.enableAdbRoot()).thenReturn(false);

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_adbDisableRootCommandFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.disableAdbRoot()).thenThrow(new DeviceNotAvailableException());

        assertThrows(DeviceNotAvailableException.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_adbDisableRootFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(device.disableAdbRoot()).thenReturn(false);

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_adbRemountFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.doThrow(new DeviceNotAvailableException()).when(device).remountSystemWritable();

        assertThrows(DeviceNotAvailableException.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_adbRemounted_mountReadOnlyAfterwards() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.doNothing().when(device).remountSystemWritable();

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(1)).remountSystemWritable();
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(MOUNT_COMMAND_PREFIX));
    }

    @Test
    public void setUp_mountReadOnlyFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(MOUNT_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_removePackageInstallDirectoryFailed_throws() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    @Test
    public void setUp_removePackageDataDirectoryFailed_doesNotThrow() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX)))
                .thenReturn(createFailedCommandResult());

        preparer.setUp(device, null);
    }

    @Test
    public void setUp_packageIsSystemApp_appRemoved() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX));
    }

    @Test
    public void setUp_noUpdatePackagePresent_appRemoved() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        int numberOfUpdates = 0;
        ITestDevice device = createGoodDeviceWithSystemAppInstalled(numberOfUpdates);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(numberOfUpdates + 1))
                .uninstallPackage(TEST_PACKAGE_NAME);
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
    }

    @Test
    public void setUp_someUpdatePackagesPresent_appRemoved() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        int numberOfUpdates = 2;
        ITestDevice device = createGoodDeviceWithSystemAppInstalled(numberOfUpdates);

        preparer.setUp(device, null);

        Mockito.verify(device, Mockito.times(numberOfUpdates + 1))
                .uninstallPackage(TEST_PACKAGE_NAME);
        Mockito.verify(device, Mockito.times(1))
                .executeShellV2Command(Mockito.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX));
    }

    @Test
    public void setUp_tooManyUpdatePackagesPresent_throwsException() throws Exception {
        SystemAppRemovalPreparer preparer = preparerBuilder().build();
        ITestDevice device =
                createGoodDeviceWithSystemAppInstalled(
                        SystemAppRemovalPreparer.MAX_NUMBER_OF_UPDATES + 1);

        assertThrows(TargetSetupError.class, () -> preparer.setUp(device, null));
    }

    private static final class PreparerBuilder {
        private final ListMultimap<String, String> mOptions = ArrayListMultimap.create();

        PreparerBuilder setOption(String key, String value) {
            mOptions.put(key, value);
            return this;
        }

        SystemAppRemovalPreparer build() throws ConfigurationException {
            SystemAppRemovalPreparer preparer = new SystemAppRemovalPreparer();
            OptionSetter optionSetter = new OptionSetter(preparer);

            for (Map.Entry<String, String> e : mOptions.entries()) {
                optionSetter.setOptionValue(e.getKey(), e.getValue());
            }

            return preparer;
        }
    }

    private ITestDevice createGoodDeviceWithUserAppInstalled() throws Exception {
        ITestDevice device = createGoodDeviceWithSystemAppInstalled();

        CommandResult commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(
                String.format("package:/data/app/%s/%s.apk", TEST_PACKAGE_NAME, TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        return device;
    }

    private ITestDevice createGoodDeviceWithSystemAppInstalled() throws Exception {
        return createGoodDeviceWithSystemAppInstalled(1);
    }

    private ITestDevice createGoodDeviceWithSystemAppInstalled(int numberOfUpdatesInstalled)
            throws Exception {
        ITestDevice device = Mockito.mock(ITestDevice.class);
        CommandResult commandResult;

        // Uninstall updates
        String uninstallFailureMessage = "Failure [DELETE_FAILED_INTERNAL_ERROR]";
        if (numberOfUpdatesInstalled == 0) {
            Mockito.when(device.uninstallPackage(TEST_PACKAGE_NAME))
                    .thenReturn(uninstallFailureMessage);
        } else {
            String[] uninstallResults = new String[numberOfUpdatesInstalled];
            uninstallResults[numberOfUpdatesInstalled - 1] = uninstallFailureMessage;
            Mockito.when(device.uninstallPackage(TEST_PACKAGE_NAME))
                    .thenReturn(null, uninstallResults);
        }

        // List package
        commandResult = createSuccessfulCommandResult();
        commandResult.setStdout("package:" + TEST_PACKAGE_NAME);
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        CHECK_PACKAGE_INSTALLED_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        // Get package path
        commandResult = createSuccessfulCommandResult();
        commandResult.setStdout(
                String.format(
                        "package:%s/%s/%s.apk",
                        SYSTEM_APP_INSTALL_DIRECTORY, TEST_PACKAGE_NAME, TEST_PACKAGE_NAME));
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(
                                        GET_PACKAGE_INSTALL_PATH_COMMAND_PREFIX)))
                .thenReturn(commandResult);

        // Adb root
        Mockito.when(device.isAdbRoot()).thenReturn(false);
        Mockito.when(device.enableAdbRoot()).thenReturn(true);

        // Adb remount
        Mockito.doNothing().when(device).remountSystemWritable();

        // Remove package install directory
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_SYSTEM_APP_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        // Remove package data directory
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(REMOVE_APP_DATA_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        // Restart framework
        Mockito.when(device.executeShellV2Command(ArgumentMatchers.eq("start")))
                .thenReturn(createSuccessfulCommandResult());
        Mockito.when(device.executeShellV2Command(ArgumentMatchers.eq("stop")))
                .thenReturn(createSuccessfulCommandResult());

        // Disable adb root
        Mockito.when(device.disableAdbRoot()).thenReturn(true);

        // Remount read only
        Mockito.when(
                        device.executeShellV2Command(
                                ArgumentMatchers.startsWith(MOUNT_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        return device;
    }

    private static PreparerBuilder preparerBuilder() {
        return new PreparerBuilder()
                .setOption(SystemAppRemovalPreparer.OPTION_PACKAGE_NAME, TEST_PACKAGE_NAME);
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        return commandResult;
    }
}
