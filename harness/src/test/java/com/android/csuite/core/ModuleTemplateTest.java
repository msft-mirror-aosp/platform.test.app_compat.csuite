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

package com.android.csuite.core;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public final class ModuleTemplateTest {
    @Test
    public void substitute_multipleReplacementPairs_replaceAll() throws Exception {
        String template = "-ab";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "c", "b", "d"));

        assertThat(content).isEqualTo("-cd");
    }

    @Test
    public void substitute_replacementKeyNotInTemplate_doesNotReplace() throws Exception {
        String template = "-a";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("b", ""));

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void substitute_multipleReplacementKeyInTemplate_replaceTheKeys() throws Exception {
        String template = "-aba";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "c"));

        assertThat(content).isEqualTo("-cbc");
    }

    @Test
    public void substitute_noReplacementPairs_returnTemplate() throws Exception {
        String template = "-a";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of());

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void substitute_templateContentIsEmpty_returnEmptyString() throws Exception {
        String template = "";
        ModuleTemplate subject = createTestSubject(template);

        String content = subject.substitute("any_name", Map.of("a", "b"));

        assertThat(content).isEqualTo(template);
    }

    @Test
    public void loadFrom_templateMappingContainsNonexistTemplates_throwsException()
            throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 = Map.of("module1", "template1");
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of());

        assertThrows(IllegalArgumentException.class, () -> ModuleTemplate.loadFrom(config));
    }

    @Test
    public void loadFrom_templateMappingContainsExistingExtraTemplates_doesNotThrow()
            throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 = Map.of("module1", "template1");
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION));

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsXmlExtension_doesNotThrow() throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 =
                Map.of("module1", "template1" + ModuleTemplate.XML_FILE_EXTENSION);
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of("template1" + ModuleTemplate.TEMPLATE_FILE_EXTENSION));

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsCaseMismatchingXmlExtension_doesNotThrow()
            throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 =
                Map.of("module1", "template1" + ModuleTemplate.XML_FILE_EXTENSION.toUpperCase());
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of(
                                "template1"
                                        + ModuleTemplate.TEMPLATE_FILE_EXTENSION.toLowerCase()));

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_templateMappingContainsDefaultTemplate_doesNotThrow() throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 = Map.of("module1", "default_template");
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of());

        ModuleTemplate.loadFrom(config);
    }

    @Test
    public void loadFrom_duplicateTemplateMappingEntries_throwsException() throws Exception {
        String defaultTemplate = "";
        Map<String, String> map1 = Map.of("module1", "template1");
        Map<String, String> map2 = Map.of("module1", "template1");
        IConfiguration config =
                createConfiguration(
                        defaultTemplate,
                        List.of(map1, map2),
                        "default_template" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                        List.of("template1.xml"));

        assertThrows(IllegalArgumentException.class, () -> ModuleTemplate.loadFrom(config));
    }

    private static ModuleTemplate createTestSubject(String defaultTemplate)
            throws ConfigurationException, IOException {
        return createTestSubjectWithTemplateMapping(
                defaultTemplate,
                List.of(),
                "any_path" + ModuleTemplate.TEMPLATE_FILE_EXTENSION,
                List.of());
    }

    private static ModuleTemplate createTestSubjectWithTemplateMapping(
            String defaultTemplate,
            List<Map<String, String>> templateMappings,
            String defaultTemplatePath,
            List<String> extraTemplatePath)
            throws ConfigurationException, IOException {
        return ModuleTemplate.loadFrom(
                createConfiguration(
                        defaultTemplate, templateMappings, defaultTemplatePath, extraTemplatePath));
    }

    private static IConfiguration createConfiguration(
            String defaultTemplate,
            List<Map<String, String>> templateMappings,
            String defaultTemplatePath,
            List<String> extraTemplatePath)
            throws ConfigurationException {
        IConfiguration configuration = new Configuration("name", "description");

        ModuleTemplate moduleTemplate = new ModuleTemplate(resource -> defaultTemplate);
        OptionSetter optionSetter = new OptionSetter(moduleTemplate);
        optionSetter.setOptionValue(ModuleTemplate.TEMPLATE_OPTION, defaultTemplatePath);
        for (String extraTemplate : extraTemplatePath) {
            optionSetter.setOptionValue(ModuleTemplate.EXTRA_TEMPLATES_OPTION, extraTemplate);
        }
        configuration.setConfigurationObject(
                ModuleTemplate.MODULE_TEMPLATE_PROVIDER_OBJECT_TYPE, moduleTemplate);

        for (Map<String, String> map : templateMappings) {
            TemplateMappingProvider provider = () -> map.entrySet().stream();
            configuration.setConfigurationObject(
                    TemplateMappingProvider.TEMPLATE_MAPPING_PROVIDER_OBJECT_TYPE, provider);
        }

        return configuration;
    }
}
