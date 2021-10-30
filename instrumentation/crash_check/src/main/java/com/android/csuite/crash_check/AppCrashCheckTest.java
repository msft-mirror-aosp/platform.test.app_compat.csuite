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

package com.android.csuite.crash_check;

import android.content.Context;
import android.os.Bundle;
import android.os.DropBoxManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.Preconditions;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** An instrumentation test that detects crashes of an app. */
@RunWith(AndroidJUnit4.class)
public final class AppCrashCheckTest {

    private static final String TAG = AppCrashCheckTest.class.getSimpleName();
    private static final String PACKAGE_NAME_ARG = "PACKAGE_NAME_ARG";
    private static final String START_TIME_ARG = "START_TIME_ARG";
    private static final Set<String> DROPBOX_TAGS = new HashSet<>();
    private static final int MAX_CRASH_SNIPPET_LINES = 60;
    private static final int MAX_NUM_CRASH_SNIPPET = 3;

    private Context mContext;
    private Bundle mArgs;
    private Map<String, List<String>> mAppErrors = new HashMap<>();

    static {
        DROPBOX_TAGS.add("SYSTEM_TOMBSTONE");
        DROPBOX_TAGS.add("system_app_anr");
        DROPBOX_TAGS.add("system_app_native_crash");
        DROPBOX_TAGS.add("system_app_crash");
        DROPBOX_TAGS.add("data_app_anr");
        DROPBOX_TAGS.add("data_app_native_crash");
        DROPBOX_TAGS.add("data_app_crash");
    }

    @Before
    public void setUp() throws Exception {
        // Get permissions for privileged device operations.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        mContext = InstrumentationRegistry.getTargetContext();
        mArgs = InstrumentationRegistry.getArguments();
    }

    /** Checks for signs of crashes of the given app. */
    @Test
    public void checkForCrash() throws Exception {
        String packageName = mArgs.getString(PACKAGE_NAME_ARG);
        Preconditions.checkStringNotEmpty(
                packageName,
                String.format(
                        "Missing argument, use %s to specify the package name", PACKAGE_NAME_ARG));
        String startTimeArg = mArgs.getString(START_TIME_ARG);
        Preconditions.checkStringNotEmpty(
                packageName,
                String.format(
                        "Missing argument, use %s to specify the start time", START_TIME_ARG));
        long startTime = -1;
        try {
            startTime = Long.parseLong(startTimeArg);
        } catch (NumberFormatException e) {
            Assert.fail("Unable to parse the start time argument: " + e);
        }

        checkDropbox(startTime, packageName);
        if (mAppErrors.containsKey(packageName)) {
            StringBuilder message =
                    new StringBuilder("Error(s) detected for package: ").append(packageName);
            List<String> errors = mAppErrors.get(packageName);
            for (int i = 0; i < MAX_NUM_CRASH_SNIPPET && i < errors.size(); i++) {
                String err = errors.get(i);
                message.append("\n\n");
                // limit the size of each crash snippet
                message.append(truncate(err, MAX_CRASH_SNIPPET_LINES));
            }
            if (errors.size() > MAX_NUM_CRASH_SNIPPET) {
                message.append(
                        String.format(
                                "\n... %d more errors omitted ...",
                                errors.size() - MAX_NUM_CRASH_SNIPPET));
            }
            Assert.fail(message.toString());
        }
    }

    /**
     * Truncate the text to at most the specified number of lines, and append a marker at the end
     * when truncated
     *
     * @param text
     * @param maxLines
     * @return
     */
    private static String truncate(String text, int maxLines) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < maxLines && i < lines.length; i++) {
            ret.append(lines[i]);
            ret.append('\n');
        }
        if (lines.length > maxLines) {
            ret.append("... ");
            ret.append(lines.length - maxLines);
            ret.append(" more lines truncated ...\n");
        }
        return ret.toString();
    }

    /**
     * Check dropbox for entries of interest regarding the specified process
     *
     * @param startTime if not 0, only check entries with timestamp later than the start time
     * @param processName the process name to check for
     */
    private void checkDropbox(long startTime, String processName) {
        DropBoxManager dropbox = mContext.getSystemService(DropBoxManager.class);
        DropBoxManager.Entry entry = null;
        while (null != (entry = dropbox.getNextEntry(null, startTime))) {
            try {
                // only check entries with tag that's of interest
                String tag = entry.getTag();
                if (DROPBOX_TAGS.contains(tag)) {
                    String content = entry.getText(4096);
                    if (content != null) {
                        if (content.contains(processName)) {
                            addProcessError(processName, "dropbox:" + tag, content);
                        }
                    }
                }
                startTime = entry.getTimeMillis();
            } finally {
                entry.close();
            }
        }
    }

    private void addProcessError(String processName, String errorType, String errorInfo) {
        // parse out the package name if necessary, for apps with multiple processes
        String pkgName = processName.split(":", 2)[0];
        List<String> errors;
        if (mAppErrors.containsKey(pkgName)) {
            errors = mAppErrors.get(pkgName);
        } else {
            errors = new ArrayList<>();
        }
        errors.add(String.format("### Type: %s, Details:\n%s", errorType, errorInfo));
        mAppErrors.put(pkgName, errors);
    }
}
