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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public final class AppCrawlTesterTest {
    private final TestArtifactReceiver mMockTestArtifactReceiver =
            Mockito.mock(TestArtifactReceiver.class);
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());
    private final ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private final TestInformation mTestInfo = createTestInfo();
    private final TestLogData mMockTestLogData = Mockito.mock(TestLogData.class);
    private final IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);

    @Test
    public void start_withSplitApksDirectory_doesNotThrowException() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());

        suj.start();
    }

    @Test
    public void start_credentialIsProvidedToCrawler() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());

        suj.start();

        Mockito.verify(mRunUtil)
                .setEnvVariable(Mockito.eq("GOOGLE_APPLICATION_CREDENTIALS"), Mockito.anyString());
    }

    @Test
    public void start_withSplitApksInSubDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("sub"));
        Files.createFile(root.resolve("sub").resolve("base.apk"));
        Files.createFile(root.resolve("sub").resolve("config.apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        suj.start();
    }

    @Test
    public void start_withSingleSplitApkDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        suj.start();
    }

    @Test
    public void start_withSingleApkDirectory_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        suj.start();
    }

    @Test
    public void start_withSingleApkFile_doesNotThrowException() throws Exception {
        Path root = mFileSystem.getPath("single.apk");
        Files.createFile(root);
        AppCrawlTester suj = createPreparedTestSubject(root);

        suj.start();
    }

    @Test
    public void start_withApkDirectoryContainingOtherFileTypes_doesNotThrowException()
            throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.apk"));
        Files.createFile(root.resolve("single.not_apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        suj.start();
    }

    @Test
    public void start_withApkDirectoryContainingNoApks_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("single.not_apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> suj.start());
    }

    @Test
    public void start_withNonApkPath_throwException() throws Exception {
        Path root = mFileSystem.getPath("single.not_apk");
        Files.createFile(root);
        AppCrawlTester suj = createPreparedTestSubject(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> suj.start());
    }

    @Test
    public void start_withApksInMultipleDirectories_throwException() throws Exception {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("1"));
        Files.createDirectories(root.resolve("2"));
        Files.createFile(root.resolve("1").resolve("single.apk"));
        Files.createFile(root.resolve("2").resolve("single.apk"));
        AppCrawlTester suj = createPreparedTestSubject(root);

        assertThrows(AppCrawlTester.CrawlerException.class, () -> suj.start());
    }

    @Test
    public void start_preparerNotRun_throwsException() throws Exception {
        AppCrawlTester suj = createNotPreparedTestSubject(createApkPathWithSplitApks());

        assertThrows(AppCrawlTester.CrawlerException.class, () -> suj.start());
    }

    @Test
    public void start_alreadyRun_throwsException() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();

        assertThrows(AppCrawlTester.CrawlerException.class, () -> suj.start());
    }

    @Test
    public void cleanUp_removesOutputDirectory() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();
        assertTrue(Files.exists(suj.mOutput));

        suj.cleanUp();

        assertFalse(Files.exists(suj.mOutput));
    }

    @Test
    public void createCrawlerRunCommand_containsRequiredCrawlerParams() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();
        List<Path> apks = Arrays.asList(Path.of("some.apk"));

        String[] result = suj.createCrawlerRunCommand(mTestInfo, apks);

        assertThat(result).asList().contains("--key-store-file");
        assertThat(result).asList().contains("--key-store-password");
        assertThat(result).asList().contains("--device-serial-code");
        assertThat(result).asList().contains("--apk-file");
    }

    @Test
    public void createCrawlerRunCommand_crawlerIsExecutedThroughJavaJar() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();
        List<Path> apks = Arrays.asList(Path.of("some.apk"));

        String[] result = suj.createCrawlerRunCommand(mTestInfo, apks);

        assertThat(result).asList().contains("java");
        assertThat(result).asList().contains("-jar");
    }

    @Test
    public void createCrawlerRunCommand_splitApksProvided_useApkFileAndSplitApkFilesParams()
            throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();
        List<Path> apks =
                Arrays.asList(Path.of("base.apk"), Path.of("config1.apk"), Path.of("config2.apk"));

        String[] result = suj.createCrawlerRunCommand(mTestInfo, apks);

        assertThat(Arrays.asList(result).stream().filter(s -> s.equals("--apk-file")).count())
                .isEqualTo(1);
        assertThat(
                        Arrays.asList(result).stream()
                                .filter(s -> s.equals("--split-apk-files"))
                                .count())
                .isEqualTo(2);
    }

    @Test
    public void createCrawlerRunCommand_doesNotContainNullOrEmptyStrings() throws Exception {
        AppCrawlTester suj = createPreparedTestSubject(createApkPathWithSplitApks());
        suj.start();
        List<Path> apks =
                Arrays.asList(Path.of("base.apk"), Path.of("config1.apk"), Path.of("config2.apk"));

        String[] result = suj.createCrawlerRunCommand(mTestInfo, apks);

        assertThat(Arrays.asList(result).stream().filter(s -> s == null).count()).isEqualTo(0);
        assertThat(Arrays.asList(result).stream().map(String::trim).filter(String::isEmpty).count())
                .isEqualTo(0);
    }

    private void simulatePreparerWasExecutedSuccessfully()
            throws ConfigurationException, IOException, TargetSetupError {
        IRunUtil runUtil = Mockito.mock(IRunUtil.class);
        Mockito.when(runUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        AppCrawlTesterPreparer preparer = new AppCrawlTesterPreparer(() -> runUtil);
        OptionSetter optionSetter = new OptionSetter(preparer);
        optionSetter.setOptionValue(
                AppCrawlTesterPreparer.SDK_TAR_OPTION,
                Files.createDirectories(mFileSystem.getPath("sdk")).toString());
        optionSetter.setOptionValue(
                AppCrawlTesterPreparer.CRAWLER_BIN_OPTION,
                Files.createDirectories(mFileSystem.getPath("bin")).toString());
        optionSetter.setOptionValue(
                AppCrawlTesterPreparer.CREDENTIAL_JSON_OPTION,
                Files.createDirectories(mFileSystem.getPath("cred.json")).toString());
        preparer.setUp(mTestInfo);
    }

    private AppCrawlTester createNotPreparedTestSubject(Path apkPath) {
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        Mockito.when(mDevice.getSerialNumber()).thenReturn("serial");
        return new AppCrawlTester(
                apkPath, "package.name", mTestInfo, mMockTestArtifactReceiver, () -> mRunUtil);
    }

    private AppCrawlTester createPreparedTestSubject(Path apkPath)
            throws IOException, ConfigurationException, TargetSetupError {
        Mockito.when(mDevice.getSerialNumber()).thenReturn("serial");
        simulatePreparerWasExecutedSuccessfully();
        Mockito.when(mRunUtil.runTimedCmd(Mockito.anyLong(), ArgumentMatchers.<String>any()))
                .thenReturn(createSuccessfulCommandResult());
        return new AppCrawlTester(
                apkPath, "package.name", mTestInfo, mMockTestArtifactReceiver, () -> mRunUtil);
    }

    private TestInformation createTestInfo() {
        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("device1", mDevice);
        context.addDeviceBuildInfo("device1", new BuildInfo());
        return TestInformation.newBuilder().setInvocationContext(context).build();
    }

    private Path createApkPathWithSplitApks() throws IOException {
        Path root = mFileSystem.getPath("apk");
        Files.createDirectories(root);
        Files.createFile(root.resolve("base.apk"));
        Files.createFile(root.resolve("config.apk"));

        return root;
    }

    private static CommandResult createSuccessfulCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout("");
        commandResult.setStderr("");
        return commandResult;
    }
}
