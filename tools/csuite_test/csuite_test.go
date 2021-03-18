// Copyright 2020 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package csuite

import (
	"android/soong/android"
	"android/soong/java"
	"strings"
)

var (
	pctx = android.NewPackageContext("android/soong/csuite")
)

func init() {
	android.RegisterModuleType("csuite_test", CSuiteTestFactory)
}

type csuiteTestProperties struct {
	// Local path to a module template xml file.
	// The content of the template will be used to generate test modules at runtime.
	Test_config_template *string `android:"path"`
}

type CSuiteTest struct {
	// Java TestHost.
	java.TestHost

	// C-Suite test properties struct.
	csuiteTestProperties csuiteTestProperties

	// Local path to a xml config file to be included in the test plan.
	Test_plan_include *string `android:"path"`
}

func (cSuiteTest *CSuiteTest) generateTestConfigTemplate(rule *android.RuleBuilder, ctx android.ModuleContext) android.ModuleGenPath {
	if cSuiteTest.csuiteTestProperties.Test_config_template == nil {
		ctx.ModuleErrorf(`'test_config_template' is missing.`)
	}
	inputPath := android.PathForModuleSrc(ctx, *cSuiteTest.csuiteTestProperties.Test_config_template)
	genPath := android.PathForModuleGen(ctx, planConfigDirName, ctx.ModuleName()+configTemplateFileExtension)
	rule.Command().Textf("cp").Input(inputPath).Output(genPath)
	return genPath
}

func (cSuiteTest *CSuiteTest) generatePlanConfig(templatePathString string, ctx android.ModuleContext) android.ModuleGenPath {
	planName := ctx.ModuleName()
	genPath := android.PathForModuleGen(ctx, planConfigDirName, planName+planFileExtension)
	content := strings.Replace(planTemplateContent, "{planName}", planName, -1)
	content = strings.Replace(content, "{templatePath}", templatePathString, -1)
	android.WriteFileRule(ctx, genPath, content)
	return genPath
}

func (cSuiteTest *CSuiteTest) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	rule := android.NewRuleBuilder(pctx, ctx)

	configTemplateOutputPath := cSuiteTest.generateTestConfigTemplate(rule, ctx)
	cSuiteTest.AddExtraResource(configTemplateOutputPath)

	planOutputFile := cSuiteTest.generatePlanConfig(configTemplateOutputPath.Rel(), ctx)
	cSuiteTest.AddExtraResource(planOutputFile)

	rule.Build("CSuite", "generate C-Suite config files")
	cSuiteTest.TestHost.GenerateAndroidBuildActions(ctx)
}

func CSuiteTestFactory() android.Module {
	module := &CSuiteTest{}
	module.AddProperties(&module.csuiteTestProperties)
	installable := true
	autoGenConfig := false
	java.InitTestHost(&module.TestHost, &installable, []string{"csuite"}, &autoGenConfig)

	java.InitJavaModuleMultiTargets(module, android.HostSupported)

	return module
}

const (
	planConfigDirName           = `config`
	configTemplateFileExtension = `.xml.template`
	planFileExtension           = `.xml`
	planTemplateContent         = `<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2020 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->
<configuration>
     <test class="com.android.csuite.config.ModuleGenerator">
          <option name="template" value="{templatePath}" />
     </test>
     <include name="csuite-base" />
     <option name="plan" value="{planName}" />
</configuration>
`
)
