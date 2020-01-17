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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import org.mockito.InOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AppLaunchTestTest {

    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private static final String TEST_PACKAGE_NAME = "package_name";

    @Test
    public void run_testFailed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createFailingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);

        appLaunchTest.run(mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    @Test
    public void run_testPassed() throws DeviceNotAvailableException {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTest();
        AppLaunchTest appLaunchTest = createLaunchTestWithInstrumentation(instrumentationTest);

        appLaunchTest.run(mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test
    public void run_packageResetSuccess() throws DeviceNotAvailableException {
        ITestDevice mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(new CommandResult(CommandStatus.SUCCESS));
        AppLaunchTest appLaunchTest = createLaunchTestWithMockDevice(mMockDevice);

        appLaunchTest.run(mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test
    public void run_packageResetError() throws DeviceNotAvailableException {
        ITestDevice mMockDevice = mock(ITestDevice.class);
        when(mMockDevice.executeShellV2Command(String.format("pm clear %s", TEST_PACKAGE_NAME)))
                .thenReturn(new CommandResult(CommandStatus.FAILED));
        AppLaunchTest appLaunchTest = createLaunchTestWithMockDevice(mMockDevice);

        appLaunchTest.run(mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    @Test
    public void run_testRetry_passedAfterTwoFailings() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTestAfterFailing(2);
        AppLaunchTest appLaunchTest = createLaunchTestWithRetry(instrumentationTest, 2);

        appLaunchTest.run(mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test
    public void run_testRetry_failedAfterThreeFailings() throws Exception {
        InstrumentationTest instrumentationTest = createPassingInstrumentationTestAfterFailing(3);
        AppLaunchTest appLaunchTest = createLaunchTestWithRetry(instrumentationTest, 2);

        appLaunchTest.run(mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_nullIncludeFilter_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_emptyIncludeFilter_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("");
    }

    @Test
    public void addIncludeFilter_validIncludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("test_filter");

        assertTrue(sut.mIncludeFilters.contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllIncludeFilters_nullIncludeFilter_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addAllIncludeFilters(null);
    }

    @Test
    public void addAllIncludeFilters_validIncludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();
        Set<String> test_filters = new HashSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllIncludeFilters(test_filters);

        assertTrue(sut.mIncludeFilters.contains("filter_one"));
        assertTrue(sut.mIncludeFilters.contains("filter_two"));
    }

    @Test
    public void clearIncludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();
        sut.addIncludeFilter("filter_test");

        sut.clearIncludeFilters();

        assertTrue(sut.mIncludeFilters.isEmpty());
    }

    @Test
    public void getIncludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();
        sut.addIncludeFilter("filter_test");

        assertEquals(sut.mIncludeFilters, sut.getIncludeFilters());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_nullExcludeFilter_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_emptyExcludeFilter_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter("");
    }

    @Test
    public void addExcludeFilter_validExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter("test_filter");

        assertTrue(sut.mExcludeFilters.contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllExcludeFilters_nullExcludeFilters_throwsException() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addAllExcludeFilters(null);
    }

    @Test
    public void addAllExcludeFilters_validExcludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();
        Set<String> test_filters = new HashSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllExcludeFilters(test_filters);

        assertTrue(sut.mExcludeFilters.contains("filter_one"));
        assertTrue(sut.mExcludeFilters.contains("filter_two"));
    }

    @Test
    public void clearExcludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();
        sut.addExcludeFilter("filter_test");

        sut.clearExcludeFilters();

        assertTrue(sut.mExcludeFilters.isEmpty());
    }

    @Test
    public void getExcludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter("filter_test");

        assertEquals(sut.mExcludeFilters, sut.getExcludeFilters());
    }

    @Test
    public void inFilter_withEmptyFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withRelatedIncludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_one");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withRelatedExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter("filter_one");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addExcludeFilter("filter_two");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withSameIncludeAndExcludeFilters() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_one");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilterAndRelatedExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_two"));
    }

    @Test
    public void inFilter_withRelatedIncludeFilterAndUnrelatedExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilterAndUnrelatedExcludeFilter() {
        AppLaunchTest sut = new AppLaunchTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_three"));
    }

    private InstrumentationTest createFailingInstrumentationTest() {
        InstrumentationTest instrumentation =
                new InstrumentationTest() {
                    @Override
                    public void run(final ITestInvocationListener listener)
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
                    public void run(final ITestInvocationListener listener)
                            throws DeviceNotAvailableException {}
                };
        return instrumentation;
    }

    private InstrumentationTest createPassingInstrumentationTestAfterFailing(int failedCount) {
        InstrumentationTest instrumentation =
                new InstrumentationTest() {
                    private int retryCount = 0;

                    @Override
                    public void run(final ITestInvocationListener listener)
                            throws DeviceNotAvailableException {
                        if (retryCount < failedCount) {
                            listener.testFailed(new TestDescription("", ""), "test failed");
                        }
                        retryCount++;
                    }
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
                        return new CommandResult(CommandStatus.SUCCESS);
                    }
                };
        appLaunchTest.setDevice(mock(ITestDevice.class));
        return appLaunchTest;
    }

    private AppLaunchTest createLaunchTestWithRetry(
            InstrumentationTest instrumentation, int retryCount) {
        AppLaunchTest appLaunchTest =
                new AppLaunchTest(TEST_PACKAGE_NAME, retryCount) {
                    @Override
                    protected InstrumentationTest createInstrumentationTest(
                            String packageBeingTested) {
                        return instrumentation;
                    }

                    @Override
                    protected CommandResult resetPackage() throws DeviceNotAvailableException {
                        return new CommandResult(CommandStatus.SUCCESS);
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
        inOrder.verify(listener, times(1)).testFailed(any(), any());
        inOrder.verify(listener, times(1))
                .testEnded(anyObject(), anyLong(), (Map<String, String>) any());
        inOrder.verify(listener, times(1)).testRunEnded(anyLong(), (HashMap<String, Metric>) any());
    }

    private static void verifyPassedAndEndedCall(ITestInvocationListener listener) {
        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener, times(1)).testRunStarted(anyString(), anyInt());
        inOrder.verify(listener, times(1)).testStarted(anyObject(), anyLong());
        inOrder.verify(listener, never()).testFailed(any(), any());
        inOrder.verify(listener, times(1))
                .testEnded(anyObject(), anyLong(), (Map<String, String>) any());
        inOrder.verify(listener, times(1)).testRunEnded(anyLong(), (HashMap<String, Metric>) any());
    }
  }
