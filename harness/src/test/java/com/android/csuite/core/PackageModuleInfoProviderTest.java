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

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public final class PackageModuleInfoProviderTest {
    @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void get_templateContainsPlaceholders_replacesPlaceholdersInOutput() throws Exception {
        String content = "hello placeholder%s%s world";
        String packageName1 = "a";
        String packageName2 = "b";
        PackageModuleInfoProvider provider =
                new ProviderBuilder().addPackage(packageName1).addPackage(packageName2).build();
        IConfiguration config =
                createIConfigWithTemplate(
                        String.format(
                                content,
                                PackagesFileModuleInfoProvider.PACKAGE_PLACEHOLDER,
                                PackagesFileModuleInfoProvider.PACKAGE_PLACEHOLDER));

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(config);

        assertThat(collectModuleContentStrings(modulesInfo))
                .containsExactly(
                        String.format(content, packageName1, packageName1),
                        String.format(content, packageName2, packageName2));
    }

    @Test
    public void get_containsDuplicatedPackageNames_ignoreDuplicates() throws Exception {
        String packageName1 = "a";
        String packageName2 = "b";
        PackageModuleInfoProvider provider =
                new ProviderBuilder()
                        .addPackage(packageName1)
                        .addPackage(packageName1)
                        .addPackage(packageName2)
                        .build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(packageName1, packageName2);
    }

    @Test
    public void get_packageNamesProvided_returnsPackageNames() throws Exception {
        String packageName1 = "a";
        String packageName2 = "b";
        PackageModuleInfoProvider provider =
                new ProviderBuilder().addPackage(packageName1).addPackage(packageName2).build();

        Stream<ModuleInfoProvider.ModuleInfo> modulesInfo = provider.get(createIConfig());

        assertThat(collectModuleNames(modulesInfo)).containsExactly(packageName1, packageName2);
    }

    private List<String> collectModuleContentStrings(
            Stream<ModuleInfoProvider.ModuleInfo> modulesInfo) {
        return modulesInfo
                .map(ModuleInfoProvider.ModuleInfo::getContent)
                .collect(Collectors.toList());
    }

    private List<String> collectModuleNames(Stream<ModuleInfoProvider.ModuleInfo> modulesInfo) {
        return modulesInfo.map(ModuleInfoProvider.ModuleInfo::getName).collect(Collectors.toList());
    }

    private static final class ProviderBuilder {
        private final Set<String> mPackages = new HashSet<>();

        ProviderBuilder addPackage(String packageName) {
            mPackages.add(packageName);
            return this;
        }

        PackageModuleInfoProvider build() throws Exception {
            PackageModuleInfoProvider provider = new PackageModuleInfoProvider();

            OptionSetter optionSetter = new OptionSetter(provider);
            for (String p : mPackages) {
                optionSetter.setOptionValue(PackageModuleInfoProvider.PACKAGE_OPTION, p);
            }
            return provider;
        }
    }

    private IConfiguration createIConfig() throws ConfigurationException {
        return createIConfigWithTemplate(MODULE_TEMPLATE_CONTENT);
    }

    private IConfiguration createIConfigWithTemplate(String template)
            throws ConfigurationException {
        IConfiguration configuration = new Configuration("name", "description");
        configuration.setConfigurationObject(
                ModuleTemplate.MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE,
                createModuleTemplate(template));
        return configuration;
    }

    private ModuleTemplate createModuleTemplate(String template) throws ConfigurationException {
        ModuleTemplate moduleTemplate = new ModuleTemplate(resource -> template);
        new OptionSetter(moduleTemplate)
                .setOptionValue(ModuleTemplate.TEMPLATE_OPTION, "path.xml.template");
        return moduleTemplate;
    }

    private static final String MODULE_TEMPLATE_CONTENT =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<configuration description=\"description\">\n"
                    + "    <option name=\"package-name\" value=\"{package}\"/>\n"
                    + "    <target_generator class=\"some.generator.class\">\n"
                    + "        <option name=\"test-file-name\" value=\"app://{package}\"/>\n"
                    + "    </target_generator>\n"
                    + "    <test class=\"some.test.class\"/>\n"
                    + "</configuration>";
}
