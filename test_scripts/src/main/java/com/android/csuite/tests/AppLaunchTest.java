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

import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

/** A test that verifies that a single app can be successfully launched. */
public class AppLaunchTest extends BaseAppCompatTest {

    /** Implements the specific app launch logic. */
    @Override
    protected void performAppLaunch() throws DeviceNotAvailableException {
        try {
            // TODO(jelenacvetic): Remove this option once this method is tested.
            if (mColdAppLaunch) {
                mDeviceUtils.coldLaunchPackage(mPackageName);
            } else {
                mDeviceUtils.launchPackage(mPackageName);
            }
        } catch (DeviceUtilsException e) {
            Assert.fail("Failed to launch package " + mPackageName + ": " + e.getMessage());
        }

        CLog.d("Waiting %s milliseconds for the app to launch fully.", mAppLaunchTimeoutMs);
        RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);
    }
}