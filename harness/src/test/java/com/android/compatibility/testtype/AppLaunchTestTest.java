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
package com.android.compatibility.testtype;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public final class AppLaunchTestTest {

    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private static final String TEST_PACKAGE_NAME = "package_name";
    private static final TestInformation NULL_TEST_INFORMATION = null;
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void run_instrumentationTestFailed_testFailed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createFailingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    @Test
    public void run_instrumentationTestPassed_testPassed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test
    public void run_takeScreenShot_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);
        new OptionSetter(appLaunchTest)
                .setOptionValue(AppLaunchTest.SCREENSHOT_AFTER_LAUNCH, "true");
        ITestDevice mockDevice = mock(ITestDevice.class);
        appLaunchTest.setDevice(mockDevice);
        InputStreamSource screenshotData = new FileInputStreamSource(tempFolder.newFile());
        when(mockDevice.getScreenshot()).thenReturn(screenshotData);
        when(mockDevice.getSerialNumber()).thenReturn("SERIAL");

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenshot"), Mockito.any(), Mockito.eq(screenshotData));
    }

    @Test
    public void run_recordScreen_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);
        new OptionSetter(appLaunchTest).setOptionValue(AppLaunchTest.RECORD_SCREEN, "true");
        ITestDevice mockDevice = mock(ITestDevice.class);
        appLaunchTest.setDevice(mockDevice);
        when(mockDevice.pullFile(Mockito.any())).thenReturn(tempFolder.newFile());
        when(mockDevice.getSerialNumber()).thenReturn("SERIAL");
        when(mockDevice.executeShellV2Command(Mockito.startsWith("screenrecord")))
                .thenReturn(createSuccessfulCommandResult());
        when(mockDevice.executeShellCommand("pidof screenrecord")).thenReturn("123");

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenrecord"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_collectAppVersion_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);
        new OptionSetter(appLaunchTest).setOptionValue(AppLaunchTest.COLLECT_APP_VERSION, "true");
        ITestDevice mockDevice = mock(ITestDevice.class);
        appLaunchTest.setDevice(mockDevice);
        when(mockDevice.executeShellV2Command(Mockito.startsWith("dumpsys package")))
                .thenReturn(createSuccessfulCommandResult());

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("versionCode"), Mockito.any(), Mockito.any());
        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("versionName"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_collectGmsVersion_savesToTestLog() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);
        new OptionSetter(appLaunchTest).setOptionValue(AppLaunchTest.COLLECT_GMS_VERSION, "true");
        ITestDevice mockDevice = mock(ITestDevice.class);
        appLaunchTest.setDevice(mockDevice);
        when(mockDevice.executeShellV2Command(
                        Mockito.startsWith("dumpsys package " + AppLaunchTest.GMS_PACKAGE_NAME)))
                .thenReturn(createSuccessfulCommandResult());

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("GMS_versionCode"), Mockito.any(), Mockito.any());
        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("GMS_versionName"), Mockito.any(), Mockito.any());
    }

    @Test
    public void run_packageResetSuccess() throws DeviceNotAvailableException {
        ITestDevice mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(createSuccessfulCommandResult());
        AppLaunchTest appLaunchTest = createLaunchTestWithMockDevice(mMockDevice);

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test
    public void run_packageResetError() throws DeviceNotAvailableException {
        ITestDevice mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(createFailedCommandResult());
        AppLaunchTest appLaunchTest = createLaunchTestWithMockDevice(mMockDevice);

        appLaunchTest.run(NULL_TEST_INFORMATION, mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    private InstrumentationTest createFailingInstrumentationTest() {
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

    private InstrumentationTest createPassingInstrumentationTest() {
        InstrumentationTest instrumentation =
                new InstrumentationTest() {
                    @Override
                    public void run(
                            final TestInformation testInfo, final ITestInvocationListener listener)
                            throws DeviceNotAvailableException {}
                };
        return instrumentation;
    }

    private AppLaunchTest createLaunchTestWithInstrumentation(InstrumentationTest instrumentation) {
        AppLaunchTest appLaunchTest =
                new AppLaunchTest(TEST_PACKAGE_NAME) {
                    @Override
                    protected InstrumentationTest createInstrumentationTest(
                            String packageBeingTested) {
                        return instrumentation;
                    }

                    @Override
                    protected CommandResult resetPackage() throws DeviceNotAvailableException {
                        return createSuccessfulCommandResult();
                    }
                };
        appLaunchTest.setDevice(mock(ITestDevice.class));
        return appLaunchTest;
    }

    private AppLaunchTest createLaunchTestWithMockDevice(ITestDevice device) {
        AppLaunchTest appLaunchTest = new AppLaunchTest(TEST_PACKAGE_NAME);
        appLaunchTest.setDevice(device);
        return appLaunchTest;
    }

    private static void verifyFailedAndEndedCall(ITestInvocationListener listener) {
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).testRunStarted(anyString(), anyInt());
        inOrder.verify(listener, times(1)).testStarted(anyObject(), anyLong());
        inOrder.verify(listener, times(1)).testFailed(any(), anyString());
        inOrder.verify(listener, times(1))
                .testEnded(anyObject(), anyLong(), (Map<String, String>) any());
        inOrder.verify(listener, times(1)).testRunEnded(anyLong(), (HashMap<String, Metric>) any());
    }

    private static void verifyPassedAndEndedCall(ITestInvocationListener listener) {
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).testRunStarted(anyString(), anyInt());
        inOrder.verify(listener, times(1)).testStarted(anyObject(), anyLong());
        inOrder.verify(listener, never()).testFailed(any(), anyString());
        inOrder.verify(listener, times(1))
                .testEnded(anyObject(), anyLong(), (Map<String, String>) any());
        inOrder.verify(listener, times(1)).testRunEnded(anyLong(), (HashMap<String, Metric>) any());
    }

    private CommandResult createSuccessfulCommandResult() {
        return createSuccessfulCommandResult("");
    }

    private CommandResult createSuccessfulCommandResult(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }
}
