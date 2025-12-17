/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A test that verifies that a single app can be launched and then moved to a secondary display. */
public class DisplayMoveTest extends BaseAppCompatTest {

    private static final long APP_MOVE_WAIT_MS = 3000;

    private static final String DISPLAY_MOVE_COMMAND =
            "dumpsys activity service SystemUIService WMShell desktopmode moveToNextDisplay";

    private static final String DUMP_WINDOWS_COMMAND = "dumpsys window windows";

    @Override
    protected void performPostLaunchActions() throws DeviceNotAvailableException {
        getDevice().executeShellCommand(DISPLAY_MOVE_COMMAND);

        CLog.d(
                "Waiting %s milliseconds for the app to stabilize on new display.",
                APP_MOVE_WAIT_MS);
        RunUtil.getDefault().sleep(APP_MOVE_WAIT_MS);

        checkWindowOnNonDefaultDisplay();
    }

    private void checkWindowOnNonDefaultDisplay() throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand(DUMP_WINDOWS_COMMAND);

        String[] windowBlocks = output.split("Window #");
        Pattern packagePattern =
                Pattern.compile("package=" + Pattern.quote(mPackageName) + "(\\s|$)");
        Pattern displayIdPattern = Pattern.compile("mDisplayId=(\\d+)");
        for (String block : windowBlocks) {
            if (block.trim().isEmpty()) continue;
            Matcher pkgMatcher = packagePattern.matcher(block);
            if (pkgMatcher.find()) {
                Matcher displayMatcher = displayIdPattern.matcher(block);
                if (displayMatcher.find()) {
                    int displayId = Integer.parseInt(displayMatcher.group(1));
                    if (displayId == 0) {
                        Assert.fail(
                                String.format(
                                        "Assertion Failed: Window for package '%s' is on default"
                                                + " display",
                                        mPackageName));
                    }
                    return;
                }
            }
        }

        Assert.fail(
                String.format(
                        "Assertion Failed: No window for package '%s' was found in window dump",
                        mPackageName));
    }
}
