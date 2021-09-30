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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * An abstract base test class that handles most of the required TradeFed API usages.
 *
 * <p>Test classes that extends this class will be recognized as a TradeFed IRemoteTest and the test
 * information will be received before the abstract #run method is called. The implementing class
 * needs to call the #testFailed method when the test fails or there's some error. Otherwise the
 * test will be considered passed in the test result unless there's an exception thrown.
 */
public abstract class AbstractCSuiteTest
        implements IDeviceTest, IRemoteTest, IConfigurationReceiver, ITestFilterReceiver {

    @Option(name = "include-filter", description = "The include filter of the test type.")
    private final Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = "exclude-filter", description = "The exclude filter of the test type.")
    private final Set<String> mExcludeFilters = new HashSet<>();

    private ITestDevice mDevice;
    private IConfiguration mConfiguration;
    private TestDescription mTestDescription;
    private TestInformation mTestInfo;
    private ITestInvocationListener mInvocationListener;
    private static final String TEST_LABEL = "AppCompatibility";

    public AbstractCSuiteTest() {
        // Intentionally left blank.
    }

    @VisibleForTesting
    AbstractCSuiteTest(TestInformation testInfo, ITestInvocationListener invocationListener) {
        mTestDescription = createTestDescription();
        mTestInfo = testInfo;
        mInvocationListener = invocationListener;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public final void run(
            final TestInformation testInfo, final ITestInvocationListener invocationListener)
            throws DeviceNotAvailableException {
        CLog.d("Start of run method.");
        CLog.d("Include filters: %s", getIncludeFilters());
        CLog.d("Exclude filters: %s", getExcludeFilters());

        mTestDescription = createTestDescription();
        mTestInfo = testInfo;
        mInvocationListener = invocationListener;

        if (!inFilter(mTestDescription.toString())) {
            CLog.d("Test case %s doesn't match any filter", mTestDescription);
            return;
        }
        CLog.d("Complete filtering test case: %s", mTestDescription);

        long start = System.currentTimeMillis();
        mInvocationListener.testRunStarted(TEST_LABEL, 1);
        mInvocationListener.testStarted(mTestDescription, System.currentTimeMillis());

        try {
            run();
        } finally {
            mInvocationListener.testEnded(
                    mTestDescription,
                    System.currentTimeMillis(),
                    Collections.<String, String>emptyMap());
            mInvocationListener.testRunEnded(
                    System.currentTimeMillis() - start, new HashMap<String, Metric>());
        }
    }

    /** Mark the test as failed in the test result. */
    protected final void testFailed(String msg) {
        mInvocationListener.testFailed(mTestDescription, msg);
    }

    protected final void addTestArtifact(String name, LogDataType type, byte[] bytes) {
        mInvocationListener.testLog(name, type, new ByteArrayInputStreamSource(bytes));
    }

    protected final void addTestArtifact(String name, LogDataType type, File file) {
        mInvocationListener.testLog(name, type, new FileInputStreamSource(file));
    }

    protected final void addTestArtifact(String name, LogDataType type, InputStreamSource source) {
        mInvocationListener.testLog(name, type, source);
    }

    protected final TestInformation getTestInfo() {
        return mTestInfo;
    }

    protected final ITestInvocationListener getInvocationListener() {
        return mInvocationListener;
    }

    protected final TestDescription getTestDescription() {
        return mTestDescription;
    }

    /** Executes the main part of the test. */
    protected abstract void run() throws DeviceNotAvailableException;

    /**
     * Gets a test description for use in logging. For compatibility with logs, this should be
     * TestDescription(test class name, test type).
     */
    protected abstract TestDescription createTestDescription();

    /**
     * Return true if a test matches one or more of the include filters AND does not match any of
     * the exclude filters. If no include filters are given all tests should return true as long as
     * they do not match any of the exclude filters.
     */
    @VisibleForTesting
    final boolean inFilter(String testName) {
        if (mExcludeFilters.contains(testName)) {
            return false;
        }
        if (mIncludeFilters.size() == 0 || mIncludeFilters.contains(testName)) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public final void addIncludeFilter(String filter) {
        checkArgument(!Strings.isNullOrEmpty(filter), "Include filter cannot be null or empty.");
        mIncludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public final void addAllIncludeFilters(Set<String> filters) {
        checkNotNull(filters, "Include filters cannot be null.");
        mIncludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public final void clearIncludeFilters() {
        mIncludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public final Set<String> getIncludeFilters() {
        return Collections.unmodifiableSet(mIncludeFilters);
    }

    /** {@inheritDoc} */
    @Override
    public final void addExcludeFilter(String filter) {
        checkArgument(!Strings.isNullOrEmpty(filter), "Exclude filter cannot be null or empty.");
        mExcludeFilters.add(filter);
    }

    /** {@inheritDoc} */
    @Override
    public final void addAllExcludeFilters(Set<String> filters) {
        checkNotNull(filters, "Exclude filters cannot be null.");
        mExcludeFilters.addAll(filters);
    }

    /** {@inheritDoc} */
    @Override
    public final void clearExcludeFilters() {
        mExcludeFilters.clear();
    }

    /** {@inheritDoc} */
    @Override
    public final Set<String> getExcludeFilters() {
        return Collections.unmodifiableSet(mExcludeFilters);
    }

    @Override
    public final void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }

    protected final IConfiguration getConfiguration() {
        return mConfiguration;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public final void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public final ITestDevice getDevice() {
        return mDevice;
    }
}
