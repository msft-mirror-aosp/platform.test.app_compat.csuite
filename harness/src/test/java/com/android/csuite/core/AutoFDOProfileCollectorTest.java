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
package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.android.csuite.core.TestUtils.TestArtifactReceiver;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.File;

@RunWith(JUnit4.class)
public class AutoFDOProfileCollectorTest {
    private final ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private final IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private final TestArtifactReceiver mTestArtifactReceiver =
            Mockito.mock(TestArtifactReceiver.class);
    private AutoFDOProfileCollector mAutoFDOProfileCollector;

    @Before
    public void setUp() throws Exception {
        when(mDevice.getSerialNumber()).thenReturn("serial");
        mAutoFDOProfileCollector = new AutoFDOProfileCollector(mDevice, () -> mRunUtil);
    }

    @Test
    public void recordAndCollectAutoFDOProfile_successScenario() throws Exception {
        when(mRunUtil.runCmdInBackground(ArgumentMatchers.<String>any()))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.pullFile(Mockito.eq(mAutoFDOProfileCollector.ON_DEVICE_PROFILE_PATH)))
                .thenReturn(Mockito.mock(File.class));
        assertThat(mAutoFDOProfileCollector.recordAutoFDOProfile(1)).isTrue();
        assertThat(mAutoFDOProfileCollector.collectAutoFDOProfile(mTestArtifactReceiver)).isTrue();

        Mockito.verify(mDevice, times(1)).pullFile(Mockito.any(String.class));
        Mockito.verify(mTestArtifactReceiver, times(1))
                .addTestArtifact(
                        Mockito.contains("autofdo_file"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void recordAndCollectAutoFDOProfile_failsToRecord() throws Exception {
        when(mRunUtil.runCmdInBackground(ArgumentMatchers.<String>any())).thenReturn(null);
        when(mDevice.pullFile(Mockito.eq(mAutoFDOProfileCollector.ON_DEVICE_PROFILE_PATH)))
                .thenReturn(Mockito.mock(File.class));
        assertThat(mAutoFDOProfileCollector.recordAutoFDOProfile(1)).isTrue();
        assertThat(mAutoFDOProfileCollector.collectAutoFDOProfile(mTestArtifactReceiver)).isFalse();

        Mockito.verify(mDevice, times(0)).pullFile(Mockito.any(String.class));
        Mockito.verify(mTestArtifactReceiver, times(0))
                .addTestArtifact(
                        Mockito.contains("autofdo_file"), Mockito.any(), Mockito.any(File.class));
    }

    @Test
    public void recordAndCollectAutoFDOProfile_failsToPullFile() throws Exception {
        when(mRunUtil.runCmdInBackground(ArgumentMatchers.<String>any()))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.pullFile(Mockito.eq(mAutoFDOProfileCollector.ON_DEVICE_PROFILE_PATH)))
                .thenReturn(null);
        assertThat(mAutoFDOProfileCollector.recordAutoFDOProfile(1)).isTrue();
        assertThat(mAutoFDOProfileCollector.collectAutoFDOProfile(mTestArtifactReceiver)).isFalse();

        Mockito.verify(mDevice, times(1)).pullFile(Mockito.any(String.class));
        Mockito.verify(mTestArtifactReceiver, times(0))
                .addTestArtifact(
                        Mockito.contains("autofdo_file"), Mockito.any(), Mockito.any(File.class));
    }
}
