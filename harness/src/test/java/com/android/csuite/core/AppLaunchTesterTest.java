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
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AppLaunchTesterTest {

    private static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private final ITestDevice mMockDevice = mock(ITestDevice.class);
    private static final String TEST_PACKAGE_NAME = "package_name";
    private static final TestInformation NULL_TEST_INFORMATION = null;
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void run_instrumentationTestFailed_testFailed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createFailingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        verify(mMockListener, times(1)).testFailed(any(), anyString());
    }

    @Test
    public void run_instrumentationTestPassed_testPassed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        verify(mMockListener, never()).testFailed(any(), anyString());
    }

    @Test
    public void run_takeScreenShot_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);
        sut.setScreenshotAfterLaunch(true);
        InputStreamSource screenshotData = new FileInputStreamSource(tempFolder.newFile());
        when(mMockDevice.getScreenshot()).thenReturn(screenshotData);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenshot"), Mockito.any(), Mockito.eq(screenshotData));
    }

    @Test
    public void run_recordScreen_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);
        sut.setRecordScreen(true);
        when(mMockDevice.pullFile(Mockito.any())).thenReturn(tempFolder.newFile());
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mMockDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mMockDevice.executeShellV2Command("pidof screenrecord"))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenrecord"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_collectAppVersion_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);
        sut.setCollectAppVersion(true);
        when(mMockDevice.executeShellV2Command(Mockito.startsWith("dumpsys package")))
                .thenReturn(createSuccessfulCommandResult());

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("versionCode"), Mockito.any(), Mockito.any());
        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("versionName"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_collectGmsVersion_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTester sut = createTesterWithInstrumentation(instrumentationTest);
        sut.setCollectGmsVersion(true);
        when(mMockDevice.executeShellV2Command(
                        Mockito.startsWith("dumpsys package " + GMS_PACKAGE_NAME)))
                .thenReturn(createSuccessfulCommandResult());

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("GMS_versionCode"), Mockito.any(), Mockito.any());
        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("GMS_versionName"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_packageResetSuccess() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(createSuccessfulCommandResult());
        AppLaunchTester sut = createTester();

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        verify(mMockListener, never()).testFailed(any(), anyString());
    }

    @Test
    public void run_packageResetError() throws DeviceNotAvailableException {
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(createFailedCommandResult());
        AppLaunchTester sut = createTester();

        sut.launchPackageAndCheckCrash(TEST_PACKAGE_NAME);

        verify(mMockListener, times(1)).testFailed(any(), anyString());
    }

    private static InstrumentationTest createFailingInstrumentationTest() {
        InstrumentationTest instrumentation =
                new InstrumentationTest() {
                    @Override
                    public void run(
                            final TestInformation testInfo, final ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        listener.testFailed(new TestDescription("", ""), "test failed");
                    }
                };
        return instrumentation;
    }

    private static InstrumentationTest createPassingInstrumentationTest() {
        InstrumentationTest instrumentation =
                new InstrumentationTest() {
                    @Override
                    public void run(
                            final TestInformation testInfo, final ITestInvocationListener listener)
                            throws DeviceNotAvailableException {}
                };
        return instrumentation;
    }

    private AppLaunchTester createTesterWithInstrumentation(InstrumentationTest instrumentation) {
        return new AppLaunchTester(createBaseTest()) {
            @Override
            protected InstrumentationTest createInstrumentationTest(String packageBeingTested) {
                return instrumentation;
            }

            @Override
            protected CommandResult resetPackage(String packageName)
                    throws DeviceNotAvailableException {
                return createSuccessfulCommandResult();
            }
        };
    }

    private AppLaunchTester createTester() {
        return new AppLaunchTester(createBaseTest());
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
