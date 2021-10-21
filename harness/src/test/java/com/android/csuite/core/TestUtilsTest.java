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
public final class TestUtilsTest {

    private static final String GMS_PACKAGE_NAME = "com.google.android.gms";
    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private final ITestDevice mMockDevice = mock(ITestDevice.class);
    private static final String TEST_PACKAGE_NAME = "package_name";
    private static final TestInformation NULL_TEST_INFORMATION = null;
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void assertPackageNotCrashed_instrumentationTestFailed_testFailed()
            throws DeviceNotAvailableException {
        TestUtils sut = new TestUtils(createBaseTest(), () -> createFailingInstrumentationTest());
        when(mMockDevice.executeShellV2Command(
                        Mockito.startsWith(DeviceUtils.RESET_PACKAGE_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        sut.assertPackageNotCrashed(TEST_PACKAGE_NAME, 0);

        verify(mMockListener, times(1)).testFailed(any(), anyString());
    }

    @Test
    public void assertPackageNotCrashed_instrumentationTestPassed_testPassed()
            throws DeviceNotAvailableException {
        TestUtils sut = new TestUtils(createBaseTest(), () -> createPassingInstrumentationTest());
        when(mMockDevice.executeShellV2Command(
                        Mockito.startsWith(DeviceUtils.RESET_PACKAGE_COMMAND_PREFIX)))
                .thenReturn(createSuccessfulCommandResult());

        sut.assertPackageNotCrashed(TEST_PACKAGE_NAME, 0);

        verify(mMockListener, never()).testFailed(any(), anyString());
    }

    @Test
    public void collectScreenshot_savesToTestLog() throws Exception {
        TestUtils sut = new TestUtils(createBaseTest(), () -> createPassingInstrumentationTest());
        InputStreamSource screenshotData = new FileInputStreamSource(tempFolder.newFile());
        when(mMockDevice.getScreenshot()).thenReturn(screenshotData);
        when(mMockDevice.getSerialNumber()).thenReturn("SERIAL");

        sut.collectScreenshot(TEST_PACKAGE_NAME);

        Mockito.verify(mMockListener, times(1))
                .testLog(Mockito.contains("screenshot"), Mockito.any(), Mockito.eq(screenshotData));
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
