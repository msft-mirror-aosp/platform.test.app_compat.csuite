/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.compatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.PublicApkUtil.ApkInfo;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@RunWith(JUnit4.class)
public final class AppCompatibilityTestTest {

    private ConcreteAppCompatibilityTest sut;

    private class ConcreteAppCompatibilityTest extends AppCompatibilityTest {

        public ConcreteAppCompatibilityTest() {
            super(null, null, null);
        }

        @Override
        protected InstrumentationTest createInstrumentationTest(String packageBeingTested) {
            return null;
        }
    }

    @Before
    public void setUp() {
        sut = new ConcreteAppCompatibilityTest();
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_nullIncludeFilter_throwsException() {
        sut.addIncludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addIncludeFilter_emptyIncludeFilter_throwsException() {
        sut.addIncludeFilter("");
    }

    @Test
    public void addIncludeFilter_validIncludeFilter() {
        sut.addIncludeFilter("test_filter");

        assertTrue(sut.mIncludeFilters.contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllIncludeFilters_nullIncludeFilter_throwsException() {
        sut.addAllIncludeFilters(null);
    }

    @Test
    public void addAllIncludeFilters_validIncludeFilters() {
        Set<String> test_filters = new TreeSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllIncludeFilters(test_filters);

        assertTrue(sut.mIncludeFilters.contains("filter_one"));
        assertTrue(sut.mIncludeFilters.contains("filter_two"));
    }

    @Test
    public void clearIncludeFilters() {
        sut.addIncludeFilter("filter_test");

        sut.clearIncludeFilters();

        assertTrue(sut.mIncludeFilters.isEmpty());
    }

    @Test
    public void getIncludeFilters() {
        sut.addIncludeFilter("filter_test");

        assertEquals(sut.mIncludeFilters, sut.getIncludeFilters());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_nullExcludeFilter_throwsException() {
        sut.addExcludeFilter(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addExcludeFilter_emptyExcludeFilter_throwsException() {
        sut.addExcludeFilter("");
    }

    @Test
    public void addExcludeFilter_validExcludeFilter() {
        sut.addExcludeFilter("test_filter");

        assertTrue(sut.mExcludeFilters.contains("test_filter"));
    }

    @Test(expected = NullPointerException.class)
    public void addAllExcludeFilters_nullExcludeFilters_throwsException() {
        sut.addAllExcludeFilters(null);
    }

    @Test
    public void addAllExcludeFilters_validExcludeFilters() {
        Set<String> test_filters = new TreeSet<>();
        test_filters.add("filter_one");
        test_filters.add("filter_two");

        sut.addAllExcludeFilters(test_filters);

        assertTrue(sut.mExcludeFilters.contains("filter_one"));
        assertTrue(sut.mExcludeFilters.contains("filter_two"));
    }

    @Test
    public void clearExcludeFilters() {
        sut.addExcludeFilter("filter_test");

        sut.clearExcludeFilters();

        assertTrue(sut.mExcludeFilters.isEmpty());
    }

    @Test
    public void getExcludeFilters() {
        sut.addExcludeFilter("filter_test");

        assertEquals(sut.mExcludeFilters, sut.getExcludeFilters());
    }

    @Test
    public void filterApk_withNoFilter() {
        List<ApkInfo> testList = createApkList();

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(filteredList, testList);
    }

    @Test
    public void filterApk_withRelatedIncludeFilters() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_one");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(convertList(filteredList), Arrays.asList("filter_one"));
    }

    @Test
    public void filterApk_withUnrelatedIncludeFilters() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_three");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertTrue(filteredList.isEmpty());
    }

    @Test
    public void filterApk_withRelatedExcludeFilters() {
        List<ApkInfo> testList = createApkList();
        sut.addExcludeFilter("filter_one");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(convertList(filteredList), Arrays.asList("filter_two"));
    }

    @Test
    public void filterApk_withUnrelatedExcludeFilters() {
        List<ApkInfo> testList = createApkList();
        sut.addExcludeFilter("filter_three");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(filteredList, testList);
    }

    @Test
    public void filterApk_withSameIncludeAndExcludeFilters() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_one");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertTrue(filteredList.isEmpty());
    }

    @Test
    public void filterApk_withDifferentIncludeAndExcludeFilter() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_one");
        sut.addIncludeFilter("filter_two");
        sut.addExcludeFilter("filter_two");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(convertList(filteredList), Arrays.asList("filter_one"));
    }

    @Test
    public void filterApk_withUnrelatedIncludeFilterAndRelatedExcludeFilter() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_three");
        sut.addExcludeFilter("filter_two");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertTrue(filteredList.isEmpty());
    }

    @Test
    public void filterApk_withRelatedIncludeFilterAndUnrelatedExcludeFilter() {
        List<ApkInfo> testList = createApkList();
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_three");

        List<ApkInfo> filteredList = sut.filterApk(testList);

        assertEquals(convertList(filteredList), Arrays.asList("filter_one"));
    }

    private List<ApkInfo> createApkList() {
        List<ApkInfo> testList = new ArrayList<>();
        ApkInfo apk_info_one = new ApkInfo(0, "filter_one", "", "", "");
        ApkInfo apk_info_two = new ApkInfo(0, "filter_two", "", "", "");
        testList.add(apk_info_one);
        testList.add(apk_info_two);
        return testList;
    }

    private List<String> convertList(List<ApkInfo> apkList) {
        List<String> convertedList = new ArrayList<>();
        for (ApkInfo apkInfo : apkList) {
            convertedList.add(apkInfo.packageName);
        }
        return convertedList;
    }

    @Test
    public void filterTest_withEmptyFilter() {
        assertTrue(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withRelatedIncludeFilter() {
        sut.addIncludeFilter("filter_one");

        assertTrue(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withUnrelatedIncludeFilter() {
        sut.addIncludeFilter("filter_two");

        assertFalse(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withRelatedExcludeFilter() {
        sut.addExcludeFilter("filter_one");

        assertFalse(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withUnrelatedExcludeFilter() {
        sut.addExcludeFilter("filter_two");

        assertTrue(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withSameIncludeAndExcludeFilters() {
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_one");

        assertFalse(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withUnrelatedIncludeFilterAndRelatedExcludeFilter() {
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.filterTest("filter_two"));
    }

    @Test
    public void filterTest_withRelatedIncludeFilterAndUnrelatedExcludeFilter() {
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertTrue(sut.filterTest("filter_one"));
    }

    @Test
    public void filterTest_withUnRelatedIncludeFilterAndUnrelatedExcludeFilter() {
        sut.addIncludeFilter("filter_one");
        sut.addExcludeFilter("filter_two");

        assertFalse(sut.filterTest("filter_three"));
    }
  }
