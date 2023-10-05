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

package com.android.csuite.tests;

import com.android.csuite.core.AppCrawlTester;
import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.csuite.core.DeviceJUnit4ClassRunner;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppCrawlTest extends BaseHostJUnit4Test implements IConfigurationReceiver {
    @Rule public TestLogData mLogData = new TestLogData();

    private AppCrawlTester mCrawler;
    private IConfiguration mConfiguration;

    @Before
    public void setUp() throws DeviceNotAvailableException, CrawlerException {
        mCrawler = AppCrawlTester.newInstance(getTestInformation(), mLogData, mConfiguration);
        mCrawler.runSetup();
    }

    @Test
    public void testAppCrash() throws DeviceNotAvailableException, CrawlerException {
        mCrawler.runTest();
    }

    @After
    public void tearDown() {
        mCrawler.runTearDown();
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}
