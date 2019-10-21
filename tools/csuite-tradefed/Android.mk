#
# Copyright (C) 2019 The Android Open Source Project.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PREBUILT_EXECUTABLES := src/scripts/csuite-tradefed
include $(BUILD_HOST_PREBUILT)


############################
# csuite-tradefed
############################

include $(CLEAR_VARS)

LOCAL_SUITE_BUILD_NUMBER := $(BUILD_NUMBER_FROM_FILE)
LOCAL_SUITE_TARGET_ARCH := $(TARGET_ARCH)
LOCAL_SUITE_NAME := CSUITE
LOCAL_SUITE_FULLNAME := "App Compatibility Test Suite"
LOCAL_SUITE_VERSION := 1.0

LOCAL_STATIC_JAVA_LIBRARIES := cts-tradefed-harness csuite-harness
LOCAL_MODULE := csuite-tradefed

include $(BUILD_COMPATIBILITY_SUITE)


############################
# csuite-tradefed-tests
############################

include $(CLEAR_VARS)

LOCAL_JAVA_RESOURCE_DIRS := src/test/resources

LOCAL_SRC_FILES += $(call all-java-files-under, src/test/java)

LOCAL_MODULE := csuite-tradefed-tests
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := tradefed csuite-tradefed
LOCAL_STATIC_JAVA_LIBRARIES := csuite-harness-tests

include $(BUILD_HOST_JAVA_LIBRARY)
