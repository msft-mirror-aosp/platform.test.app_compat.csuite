/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;

import org.junit.runners.model.InitializationError;

public class DeviceJUnit4ClassRunner extends com.android.tradefed.testtype.DeviceJUnit4ClassRunner
        implements IConfigurationReceiver {
    private IConfiguration mConfiguration;

    public DeviceJUnit4ClassRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected Object createTest() throws Exception {
        Object testObj = super.createTest();

        if (testObj instanceof IConfigurationReceiver) {
            ((IConfigurationReceiver) testObj).setConfiguration(mConfiguration);
        }

        return testObj;
    }

    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}