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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public final class AbstractCSuiteTestTest {

    private final ITestInvocationListener mMockListener = mock(ITestInvocationListener.class);
    private static final String TEST_PACKAGE_NAME = "package_name";
    private static final TestInformation NULL_TEST_INFORMATION = null;
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void run_testFailed() throws DeviceNotAvailableException {
        AbstractCSuiteTest sut = createFailingTest();

        sut.run(NULL_TEST_INFORMATION, mMockListener);

        verifyFailedAndEndedCall(mMockListener);
    }

    @Test
    public void run_testPassed() throws DeviceNotAvailableException {
        AbstractCSuiteTest sut = createPassingTest();

        sut.run(NULL_TEST_INFORMATION, mMockListener);

        verifyPassedAndEndedCall(mMockListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_nullIncludeFilter_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_emptyIncludeFilter_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("");
    }

    @Test
    public void addIncludeFilter_validIncludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("test_filter");

        assertTrue(sut.getIncludeFilters().contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllIncludeFilters_nullIncludeFilter_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addAllIncludeFilters(null);
    }

    @Test
    public void addAllIncludeFilters_validIncludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();
        Set<String> test_filters = new HashSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllIncludeFilters(test_filters);

        assertTrue(sut.getIncludeFilters().contains("filter_one"));
        assertTrue(sut.getIncludeFilters().contains("filter_two"));
    }

    @Test
    public void clearIncludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();
        sut.addIncludeFilter("filter_test");

        sut.clearIncludeFilters();

        assertTrue(sut.getIncludeFilters().isEmpty());
    }

    @Test
    public void getIncludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();
        sut.addIncludeFilter("filter_test");

        assertEquals(sut.getIncludeFilters(), sut.getIncludeFilters());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_nullExcludeFilter_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_emptyExcludeFilter_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter("");
    }

    @Test
    public void addExcludeFilter_validExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter("test_filter");

        assertTrue(sut.getExcludeFilters().contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllExcludeFilters_nullExcludeFilters_throwsException() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addAllExcludeFilters(null);
    }

    @Test
    public void addAllExcludeFilters_validExcludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();
        Set<String> test_filters = new HashSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllExcludeFilters(test_filters);

        assertTrue(sut.getExcludeFilters().contains("filter_one"));
        assertTrue(sut.getExcludeFilters().contains("filter_two"));
    }

    @Test
    public void clearExcludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();
        sut.addExcludeFilter("filter_test");

        sut.clearExcludeFilters();

        assertTrue(sut.getExcludeFilters().isEmpty());
    }

    @Test
    public void getExcludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter("filter_test");

        assertEquals(sut.getExcludeFilters(), sut.getExcludeFilters());
    }

    @Test
    public void inFilter_withEmptyFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withRelatedIncludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_one");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withRelatedExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter("filter_one");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addExcludeFilter("filter_two");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withSameIncludeAndExcludeFilters() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_one");

        assertFalse(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilterAndRelatedExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_two"));
    }

    @Test
    public void inFilter_withRelatedIncludeFilterAndUnrelatedExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertTrue(sut.inFilter("filter_one"));
    }

    @Test
    public void inFilter_withUnrelatedIncludeFilterAndUnrelatedExcludeFilter() {
        AbstractCSuiteTest sut = createPassingTest();

        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.inFilter("filter_three"));
    }

    private AbstractCSuiteTest createFailingTest() {
        AbstractCSuiteTest test =
                new AbstractCSuiteTest() {
                    @Override
                    public void run() throws DeviceNotAvailableException {
                        testFailed("Failed");
                    }

                    @Override
                    public TestDescription createTestDescription() {
                        return new TestDescription(getClass().getSimpleName(), "any");
                    }
                };
        return test;
    }

    private AbstractCSuiteTest createPassingTest() {
        AbstractCSuiteTest test =
                new AbstractCSuiteTest() {
                    @Override
                    public void run() throws DeviceNotAvailableException {
                        // Test pass
                    }

                    @Override
                    public TestDescription createTestDescription() {
                        return new TestDescription(getClass().getSimpleName(), "any");
                    }
                };
        return test;
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
}
