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

import com.android.csuite.core.AbstractCSuiteTest;
import com.android.csuite.core.AppCrawlTester;
import com.android.csuite.core.AppCrawlTester.CrawlerException;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.TestDescription;

import java.io.File;

/** A test that verifies that a single app can be successfully launched. */
public class AppCrawlTest extends AbstractCSuiteTest {
    private static final String COLLECT_APP_VERSION = "collect-app-version";
    private static final String COLLECT_GMS_VERSION = "collect-gms-version";
    private static final String RECORD_SCREEN = "record-screen";

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

    /*
     * {@inheritDoc}
     */
    @Override
    protected void run() throws DeviceNotAvailableException {
        AppCrawlTester crawler = AppCrawlTester.newInstance();
        TestUtils testUtils = TestUtils.getInstance(this);

        if (mCollectGmsVersion) {
            testUtils.collectGmsVersion(mPackageName);
        }

        if (mRecordScreen) {
            testUtils.collectScreenRecord(
                    () -> {
                        startAndCheckForCrash(crawler);
                    },
                    mPackageName);
        } else {
            startAndCheckForCrash(crawler);
        }

        // Must be done after the crawler run because the app is installed.
        if (mCollectAppVersion) {
            testUtils.collectAppVersion(mPackageName);
        }

        getDevice().uninstallPackage(mPackageName);
        crawler.collectOutputZip(this);
        crawler.cleanUp();
    }

    private void startAndCheckForCrash(AppCrawlTester crawler) throws DeviceNotAvailableException {
        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());

        long startTime = deviceUtils.currentTimeMillis();

        try {
            crawler.startCrawl(this.getTestInfo(), mApk.toPath(), mPackageName);
        } catch (CrawlerException e) {
            testFailed(e.getMessage());
            return;
        }

        String crashLog =
                TestUtils.getInstance(this).getDropboxPackageCrashedLog(mPackageName, startTime);
        if (crashLog != null) {
            testFailed(crashLog);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected TestDescription createTestDescription() {
        return new TestDescription(getClass().getSimpleName(), mPackageName);
    }
}
