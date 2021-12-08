/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.common.jimfs.Jimfs;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public final class TestUtilsTest {
    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private final ITestDevice mMockDevice = mock(ITestDevice.class);
    private static final String TEST_PACKAGE_NAME = "package_name";
    @Rule public final TemporaryFolder mTempFolder = new TemporaryFolder();
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void listApks_withSplitApksInSubDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("sub"));
        Files.createFile(root.resolve("sub").resolve("base.apk"));
        Files.createFile(root.resolve("sub").resolve("config.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("base.apk", "config.apk");
    }

    @Test
    public void listApks_withSingleSplitApkDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("base.apk");
    }

    @Test
    public void listApks_withSplitApkDirectory_returnsListWithBaseApkAsTheFirstElement()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        Files.createFile(root.resolve("a.apk"));
        Files.createFile(root.resolve("b.apk"));
        Files.createFile(root.resolve("c.apk"));

        List<Path> res = TestUtils.listApks(root);

        assertThat(res.get(0).getFileName().toString()).isEqualTo("base.apk");
    }

    @Test
    public void listApks_withSingleApkDirectory_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withSingleApkFile_returnsApks() throws Exception {
        Path root = mFileSystem.getPath("single.apk");
        Files.createFile(root);

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withApkDirectoryContainingOtherFileTypes_returnsApksOnly()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        Files.createFile(root.resolve("single.not_apk"));

        List<Path> res = TestUtils.listApks(root);

        List<String> fileNames =
                res.stream()
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toList());
        assertThat(fileNames).containsExactly("single.apk");
    }

    @Test
    public void listApks_withApkDirectoryContainingNoApks_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.not_apk"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withNonApkFile_throwException() throws Exception {
        Path root = mFileSystem.getPath("single.not_apk");
        Files.createFile(root);

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void listApks_withApksInMultipleDirectories_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("1"));
        Files.createDirectories(root.resolve("2"));
        Files.createFile(root.resolve("1").resolve("single.apk"));
        Files.createFile(root.resolve("2").resolve("single.apk"));

        assertThrows(TestUtils.TestUtilsException.class, () -> TestUtils.listApks(root));
    }

    @Test
    public void isPackageProcessRunning_processIsRunning_returnsTrue() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        when(mMockDevice.executeShellV2Command(Mockito.startsWith("pidof")))
                .thenReturn(createSuccessfulCommandResult());

        boolean result = sut.isPackageProcessRunning(TEST_PACKAGE_NAME);

        assertThat(result).isTrue();
    }

    @Test
    public void isPackageProcessRunning_processNotRunning_returnsFalse() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        when(mMockDevice.executeShellV2Command(Mockito.startsWith("pidof")))
                .thenReturn(createFailedCommandResult());

        boolean result = sut.isPackageProcessRunning(TEST_PACKAGE_NAME);

        assertThat(result).isFalse();
    }

    @Test
    public void collectScreenshot_savesToTestLog() throws Exception {
        TestUtils sut = createSubjectUnderTest();
        InputStreamSource screenshotData = new FileInputStreamSource(mTempFolder.newFile());
        when(mMockDevice.getScreenshot()).thenReturn(screenshotData);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        sut.collectScreenshot(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenshot"), Mockito.any(), Mockito.eq(screenshotData));
    }

    @Test
    public void getDropboxPackageCrashLog_noEntries_returnsNull() throws Exception {
        DeviceUtils util = Mockito.mock(DeviceUtils.class);
        TestUtils sut = new TestUtils(createBaseTest(), util);
        when(util.getDropboxEntries(Mockito.any())).thenReturn(List.of());
        long startTime = 0;

        String result = sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, false);

        assertThat(result).isNull();
    }

    @Test
    public void getDropboxPackageCrashLog_noEntries_doesNotSaveOutput() throws Exception {
        DeviceUtils util = Mockito.mock(DeviceUtils.class);
        TestUtils sut = new TestUtils(createBaseTest(), util);
        when(util.getDropboxEntries(Mockito.any())).thenReturn(List.of());
        long startTime = 0;
        boolean saveToFile = true;

        sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, saveToFile);

        Mockito.verify(mMockListener, Mockito.never())
                .testLog(Mockito.contains("dropbox"), Mockito.any(), Mockito.any());
    }

    @Test
    public void getDropboxPackageCrashLog_appCrashed_saveOutput() throws Exception {
        DeviceUtils util = Mockito.mock(DeviceUtils.class);
        TestUtils sut = new TestUtils(createBaseTest(), util);
        when(util.getDropboxEntries(Mockito.any()))
                .thenReturn(
                        List.of(
                                new DeviceUtils.DropboxEntry(
                                        2,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        TEST_PACKAGE_NAME)));
        long startTime = 0;
        boolean saveToFile = true;

        sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, saveToFile);

        Mockito.verify(mMockListener, Mockito.times(1))
                .testLog(Mockito.contains("dropbox"), Mockito.any(), Mockito.any());
    }

    @Test
    public void getDropboxPackageCrashLog_containsOldEntries_onlyReturnsNewEntries()
            throws Exception {
        DeviceUtils util = Mockito.mock(DeviceUtils.class);
        TestUtils sut = new TestUtils(createBaseTest(), util);
        long startTime = 1;
        when(util.getDropboxEntries(Mockito.any()))
                .thenReturn(
                        List.of(
                                new DeviceUtils.DropboxEntry(
                                        0,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        TEST_PACKAGE_NAME + "entry1"),
                                new DeviceUtils.DropboxEntry(
                                        2,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        TEST_PACKAGE_NAME + "entry2")));

        String result = sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, false);

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
    }

    @Test
    public void getDropboxPackageCrashLog_containsOtherProcessEntries_onlyReturnsPackageEntries()
            throws Exception {
        DeviceUtils util = Mockito.mock(DeviceUtils.class);
        TestUtils sut = new TestUtils(createBaseTest(), util);
        long startTime = 1;
        when(util.getDropboxEntries(Mockito.any()))
                .thenReturn(
                        List.of(
                                new DeviceUtils.DropboxEntry(
                                        2,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        "other.package" + "entry1"),
                                new DeviceUtils.DropboxEntry(
                                        2,
                                        DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                .toArray(
                                                        new String
                                                                [DeviceUtils.DROPBOX_APP_CRASH_TAGS
                                                                        .size()])[0],
                                        TEST_PACKAGE_NAME + "entry2")));

        String result = sut.getDropboxPackageCrashLog(TEST_PACKAGE_NAME, startTime, false);

        assertThat(result).doesNotContain("entry1");
        assertThat(result).contains("entry2");
    }

    private TestUtils createSubjectUnderTest() {
        return new TestUtils(createBaseTest(), DeviceUtils.getInstance(mMockDevice));
    }

    private static CommandResult createSuccessfulCommandResult() {
        return createSuccessfulCommandResultWithStdout("");
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }

    private AbstractCSuiteTest createBaseTest() {
        AbstractCSuiteTest res =
                new AbstractCSuiteTest(createTestInfo(), mMockListener) {

                    @Override
                    public void run() throws DeviceNotAvailableException {
                        // Intentionally left empty
                    }

                    @Override
                    public TestDescription createTestDescription() {
                        return new TestDescription("", "");
                    }
                };
        res.setDevice(mMockDevice);
        return res;
    }

    private TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", mMockDevice);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }
}
