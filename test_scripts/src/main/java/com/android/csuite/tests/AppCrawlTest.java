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
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppCrawlTest extends BaseHostJUnit4Test {
    private static final String COLLECT_APP_VERSION = "collect-app-version";
    private static final String COLLECT_GMS_VERSION = "collect-gms-version";
    private static final String RECORD_SCREEN = "record-screen";
    @Rule public TestLogData mLogData = new TestLogData();

    @Option(name = RECORD_SCREEN, description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(
            name = COLLECT_APP_VERSION,
            description =
                    "Whether to collect package version information and store the information in"
                            + " test log files.")
    private boolean mCollectAppVersion;

    @Option(
            name = COLLECT_GMS_VERSION,
            description =
                    "Whether to collect GMS core version information and store the information in"
                            + " test log files.")
    private boolean mCollectGmsVersion;

    @Option(
            name = "apk",
            mandatory = true,
            description =
                    "Path to an apk file or a directory containing apk files of a single package.")
    private File mApk;

    @Option(name = "package-name", mandatory = true, description = "Package name of testing app.")
    private String mPackageName;

    @Test
    public void testAppCrash() throws DeviceNotAvailableException {
        AppCrawlTester crawler =
                AppCrawlTester.newInstance(
                        mApk.toPath(), mPackageName, getTestInformation(), mLogData);
        crawler.setRecordScreen(mRecordScreen);
        crawler.setCollectGmsVersion(mCollectGmsVersion);
        crawler.setCollectAppVersion(mCollectAppVersion);

        startAndCheckForCrash(crawler);

        getDevice().uninstallPackage(mPackageName);
        crawler.cleanUp();
    }

    private void startAndCheckForCrash(AppCrawlTester crawler) throws DeviceNotAvailableException {
        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());

        long startTime = deviceUtils.currentTimeMillis();

        CrawlerException crawlerException = null;
        try {
            crawler.start();
        } catch (CrawlerException e) {
            crawlerException = e;
        }

        ArrayList<String> failureMessages = new ArrayList<>();

        try {
            TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);
            String dropboxCrashLog =
                    testUtils.getDropboxPackageCrashLog(mPackageName, startTime, true);
            if (dropboxCrashLog
                    != null) { // Put dropbox crash log on the top of the failure messages.
                failureMessages.add(dropboxCrashLog);
            }
        } catch (IOException e) {
            Assert.fail("Error while getting dropbox crash log: " + e);
        }

        if (crawlerException != null) {
            failureMessages.add(crawlerException.getMessage());
        }

        Assert.assertTrue(
                String.join(
                        "\n============\n",
                        failureMessages.toArray(new String[failureMessages.size()])),
                failureMessages.isEmpty());
    }
}
