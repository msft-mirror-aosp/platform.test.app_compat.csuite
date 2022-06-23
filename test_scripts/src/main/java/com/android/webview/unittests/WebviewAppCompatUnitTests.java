/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.webview.unittests;

import com.android.webview.tests.WebviewPackage;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WebviewAppCompatUnitTests {

    @Test
    public void testSuccessfulParseBuildFromDumpsys() {
        String dumpsys =
                "Current WebView package (name, version): (com.android.webview, 102.0.5005.125)\n"
                        + "Valid package com.android.webview "
                        + "(versionName: 102.0.5005.125, versionCode: 5005625,"
                        + " targetSdkVersion: 33)";
        WebviewPackage webviewPackage = WebviewPackage.buildFromDumpsys(dumpsys);
        Assert.assertEquals(webviewPackage.getPackageName(), "com.android.webview");
        Assert.assertEquals(webviewPackage.getVersion(), "102.0.5005.125");
        Assert.assertEquals(webviewPackage.getVersionCode(), 5005625);
    }
}
