/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.testng.Assert.assertThrows;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.targetprep.TargetSetupError;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.StringSubject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public final class GenerateModulePreparerTest {
    private static final String TEST_PACKAGE_NAME1 = "test.package.name1";
    private static final String TEST_PACKAGE_NAME2 = "test.package.name2";
    private static final String PACKAGE_PLACEHOLDER = "{package}";
    private static final Exception NO_EXCEPTION = null;

    private final FileSystem mFileSystem = Jimfs.newFileSystem(Configuration.unix());

    @Test
    public void tearDown_packageOptionIsSet_deletesGeneratedModules() throws Exception {
        TestInformation testInfo = createTestInfo();
        Path testsDir = createTestsDir();
        GenerateModulePreparer preparer =
                createPreparerBuilder()
                        .setTestsDir(testsDir)
                        .addPackage(TEST_PACKAGE_NAME1)
                        .addPackage(TEST_PACKAGE_NAME1) // Simulate duplicate package option
                        .addPackage(TEST_PACKAGE_NAME2)
                        .build();
        preparer.setUp(testInfo);

        preparer.tearDown(testInfo, NO_EXCEPTION);

        assertThatListDirectory(testsDir).isEmpty();
    }

    @Test
    public void tearDown_packageOptionIsNotSet_doesNotThrowError() throws Exception {
        TestInformation testInfo = createTestInfo();
        GenerateModulePreparer preparer =
                createPreparerBuilder().setTestsDir(createTestsDir()).build();
        preparer.setUp(testInfo);

        preparer.tearDown(testInfo, NO_EXCEPTION);
    }

    @Test
    public void setUp_packageNameIsEmptyString_throwsError() throws Exception {
        GenerateModulePreparer preparer = createPreparerBuilder().addPackage("").build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(createTestInfo()));
    }

    @Test
    public void setUp_packageNameContainsPlaceholder_throwsError() throws Exception {
        GenerateModulePreparer preparer =
                createPreparerBuilder().addPackage("a" + PACKAGE_PLACEHOLDER + "b").build();

        assertThrows(TargetSetupError.class, () -> preparer.setUp(createTestInfo()));
    }

    @Test
    public void setUp_packageOptionContainsDuplicates_ignoreDuplicates() throws Exception {
        Path testsDir = createTestsDir();
        GenerateModulePreparer preparer =
                createPreparerBuilder()
                        .setTestsDir(testsDir)
                        .addPackage(TEST_PACKAGE_NAME1)
                        .addPackage(TEST_PACKAGE_NAME1) // Simulate duplicate package option
                        .addPackage(TEST_PACKAGE_NAME2)
                        .build();

        preparer.setUp(createTestInfo());

        assertThatListDirectory(testsDir)
                .containsExactly(
                        getModuleConfigFile(testsDir, TEST_PACKAGE_NAME1),
                        getModuleConfigFile(testsDir, TEST_PACKAGE_NAME2));
    }

    @Test
    public void setUp_packageOptionNotSet_doesNotGenerate() throws Exception {
        Path testsDir = createTestsDir();
        GenerateModulePreparer preparer = createPreparerBuilder().setTestsDir(testsDir).build();

        preparer.setUp(createTestInfo());

        assertThatListDirectory(testsDir).isEmpty();
    }

    @Test
    public void setUp_templateContainsPlaceholders_replacesPlaceholdersInOutput() throws Exception {
        Path testsDir = createTestsDir();
        String content = "hello placeholder%s%s world";
        GenerateModulePreparer preparer =
                createPreparerBuilder()
                        .setTestsDir(testsDir)
                        .addPackage(TEST_PACKAGE_NAME1)
                        .addPackage(TEST_PACKAGE_NAME2)
                        .setTemplateContent(
                                String.format(content, PACKAGE_PLACEHOLDER, PACKAGE_PLACEHOLDER))
                        .build();

        preparer.setUp(createTestInfo());

        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME1)
                .isEqualTo(String.format(content, TEST_PACKAGE_NAME1, TEST_PACKAGE_NAME1));
        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME2)
                .isEqualTo(String.format(content, TEST_PACKAGE_NAME2, TEST_PACKAGE_NAME2));
    }

    @Test
    public void setUp_templateDoesNotContainPlaceholder_outputsTemplateContent() throws Exception {
        Path testsDir = createTestsDir();
        String content = "no placeholder";
        GenerateModulePreparer preparer =
                createPreparerBuilder()
                        .setTestsDir(testsDir)
                        .addPackage(TEST_PACKAGE_NAME1)
                        .addPackage(TEST_PACKAGE_NAME2)
                        .setTemplateContent(content)
                        .build();

        preparer.setUp(createTestInfo());

        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME1).isEqualTo(content);
        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME2).isEqualTo(content);
    }

    @Test
    public void setUp_templateContentIsEmpty_outputsTemplateContent() throws Exception {
        Path testsDir = createTestsDir();
        String content = "";
        GenerateModulePreparer preparer =
                createPreparerBuilder()
                        .setTestsDir(testsDir)
                        .addPackage(TEST_PACKAGE_NAME1)
                        .addPackage(TEST_PACKAGE_NAME2)
                        .setTemplateContent(content)
                        .build();

        preparer.setUp(createTestInfo());

        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME1).isEqualTo(content);
        assertThatModuleConfigFileContent(testsDir, TEST_PACKAGE_NAME2).isEqualTo(content);
    }

    private static StringSubject assertThatModuleConfigFileContent(
            Path testsDir, String packageName) throws IOException {
        return assertThat(
                new String(Files.readAllBytes(getModuleConfigFile(testsDir, packageName))));
    }

    private static IterableSubject assertThatListDirectory(Path dir) throws IOException {
        // Convert stream to list because com.google.common.truth.Truth8 is not available.
        return assertThat(
                Files.walk(dir)
                        .filter(p -> !p.equals(dir))
                        .collect(ImmutableList.toImmutableList()));
    }

    private static Path getModuleConfigFile(Path baseDir, String packageName) {
        return baseDir.resolve(packageName + ".config");
    }

    private Path createTestsDir() throws IOException {
        Path rootPath = mFileSystem.getPath("csuite");
        Files.createDirectories(rootPath);
        return Files.createTempDirectory(rootPath, "testDir");
    }

    private static TestInformation createTestInfo() {
        return TestInformation.newBuilder().build();
    }

    private PreparerBuilder createPreparerBuilder() throws IOException {
        return new PreparerBuilder()
                .setFileSystem(mFileSystem)
                .setTemplateContent(MODULE_TEMPLATE_CONTENT)
                .setOption(GenerateModulePreparer.OPTION_TEMPLATE, "empty_path");
    }

    private static final class PreparerBuilder {
        private final ListMultimap<String, String> mOptions = ArrayListMultimap.create();
        private final List<String> mPackages = new ArrayList<>();
        private Path mTestsDir;
        private String mTemplateContent;
        private FileSystem mFileSystem;

        PreparerBuilder addPackage(String packageName) {
            mPackages.add(packageName);
            return this;
        }

        PreparerBuilder setFileSystem(FileSystem fileSystem) {
            mFileSystem = fileSystem;
            return this;
        }

        PreparerBuilder setTemplateContent(String templateContent) {
            mTemplateContent = templateContent;
            return this;
        }

        PreparerBuilder setTestsDir(Path testsDir) {
            mTestsDir = testsDir;
            return this;
        }

        PreparerBuilder setOption(String key, String value) {
            mOptions.put(key, value);
            return this;
        }

        GenerateModulePreparer build() throws Exception {
            GenerateModulePreparer preparer =
                    new GenerateModulePreparer(
                            mFileSystem, testInfo -> mTestsDir, resourcePath -> mTemplateContent);

            OptionSetter optionSetter = new OptionSetter(preparer);
            for (Map.Entry<String, String> entry : mOptions.entries()) {
                optionSetter.setOptionValue(entry.getKey(), entry.getValue());
            }

            for (String packageName : mPackages) {
                optionSetter.setOptionValue(GenerateModulePreparer.OPTION_PACKAGE, packageName);
            }

            return preparer;
        }
    }

    private static final String MODULE_TEMPLATE_CONTENT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<configuration description=\"description\">\n"
                    + "    <option name=\"package-name\" value=\"{package}\"/>\n"
                    + "    <target_preparer class=\"some.preparer.class\">\n"
                    + "        <option name=\"test-file-name\" value=\"app://{package}\"/>\n"
                    + "    </target_preparer>\n"
                    + "    <test class=\"some.test.class\"/>\n"
                    + "</configuration>";
}
