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
	"io/ioutil"
	"os"
	"strings"
	"testing"
)

var buildDir string

func TestValidBpDoesNotThrowError(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template"
    }
  `)

	_, errs := ctx.PrepareBuildActions(config)

	android.FailIfErrored(t, errs)
}

func TestBpContainsTestHostPropsThrowsError(t *testing.T) {
	ctx, _ := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template",
      data_native_bins: "bin"
    }
  `)

	_, errs := ctx.ParseBlueprintsFiles("Android.bp")

	android.FailIfNoMatchingErrors(t, `unrecognized property`, errs)
}

func TestBpContainsManifestThrowsError(t *testing.T) {
	ctx, _ := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template",
      test_config: "AndroidTest.xml"
    }
  `)

	_, errs := ctx.ParseBlueprintsFiles("Android.bp")

	android.FailIfNoMatchingErrors(t, `unrecognized property`, errs)
}

func TestBpMissingNameThrowsError(t *testing.T) {
	ctx, _ := createContextAndConfig(t, `
    csuite_test {
      test_config_template: "test_config.xml.template"
    }
  `)

	_, errs := ctx.ParseBlueprintsFiles("Android.bp")

	android.FailIfNoMatchingErrors(t, `'name' is missing`, errs)
}

func TestBpMissingTemplatePathThrowsError(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
    }
  `)

	ctx.ParseBlueprintsFiles("Android.bp")
	_, errs := ctx.PrepareBuildActions(config)

	android.FailIfNoMatchingErrors(t, `'test_config_template' is missing`, errs)
}

func TestGeneratedTestPlanContainsPlanName(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template"
    }
  `)

	_, parsingErrs := ctx.ParseBlueprintsFiles("Android.bp")
	_, buildErrs := ctx.PrepareBuildActions(config)

	android.FailIfErrored(t, parsingErrs)
	android.FailIfErrored(t, buildErrs)
	module := ctx.ModuleForTests("plan_name", android.BuildOs.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, "plan_name") {
		t.Errorf("The plan name is missing from the generated plan: %s", content)
	}
}

func TestGeneratedTestPlanContainsTemplatePath(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template"
    }
  `)

	_, parsingErrs := ctx.ParseBlueprintsFiles("Android.bp")
	_, buildErrs := ctx.PrepareBuildActions(config)

	android.FailIfErrored(t, parsingErrs)
	android.FailIfErrored(t, buildErrs)
	module := ctx.ModuleForTests("plan_name", android.BuildOs.String()+"_common")
	content := android.ContentFromFileRuleForTests(t, module.Output("config/plan_name.xml"))
	if !strings.Contains(content, "config/plan_name.xml.template") {
		t.Errorf("The template path is missing from the generated plan: %s", content)
	}
}

func TestTemplateFileCopyRuleExists(t *testing.T) {
	ctx, config := createContextAndConfig(t, `
    csuite_test {
      name: "plan_name",
      test_config_template: "test_config.xml.template"
    }
  `)

	_, parsingErrs := ctx.ParseBlueprintsFiles("Android.bp")
	_, buildErrs := ctx.PrepareBuildActions(config)

	android.FailIfErrored(t, parsingErrs)
	android.FailIfErrored(t, buildErrs)
	params := ctx.ModuleForTests("plan_name", android.BuildOs.String()+"_common").Rule("CSuite")
	assertPathsContains(t, getAllInputPaths(params), "test_config.xml.template")
	assertWritablePathsContainsRel(t, getAllOutputPaths(params), "config/plan_name.xml.template")
	if !strings.HasPrefix(params.RuleParams.Command, "cp") {
		t.Errorf("'cp' command is missing.")
	}
}

func TestMain(m *testing.M) {
	run := func() int {
		setUp()
		defer tearDown()

		return m.Run()
	}

	os.Exit(run())
}

func assertPathsContains(t *testing.T, paths android.Paths, path string) {
	for _, p := range paths {
		if p.String() == path {
			return
		}
	}
	t.Errorf("Cannot find expected path %s", path)
}

func assertWritablePathsContainsRel(t *testing.T, paths android.WritablePaths, relPath string) {
	for _, path := range paths {
		if path.Rel() == relPath {
			return
		}
	}
	t.Errorf("Cannot find expected relative path %s", relPath)
}

func getAllOutputPaths(params android.TestingBuildParams) android.WritablePaths {
	var paths []android.WritablePath
	if params.Output != nil {
		paths = append(paths, params.Output)
	}
	if params.ImplicitOutput != nil {
		paths = append(paths, params.ImplicitOutput)
	}
	if params.SymlinkOutput != nil {
		paths = append(paths, params.SymlinkOutput)
	}
	paths = append(paths, params.Outputs...)
	paths = append(paths, params.ImplicitOutputs...)
	paths = append(paths, params.SymlinkOutputs...)

	return paths
}

func getAllInputPaths(params android.TestingBuildParams) android.Paths {
	var paths []android.Path
	if params.Input != nil {
		paths = append(paths, params.Input)
	}
	if params.Implicit != nil {
		paths = append(paths, params.Implicit)
	}
	paths = append(paths, params.Inputs...)
	paths = append(paths, params.Implicits...)

	return paths
}

func setUp() {
	var err error
	buildDir, err = ioutil.TempDir("", "soong_csuite_test")
	if err != nil {
		panic(err)
	}
}

func tearDown() {
	os.RemoveAll(buildDir)
}

func createContextAndConfig(t *testing.T, bp string) (*android.TestContext, android.Config) {
	t.Helper()
	config := android.TestArchConfig(buildDir, nil, bp, nil)
	ctx := android.NewTestArchContext(config)
	ctx.RegisterModuleType("csuite_test", CSuiteTestFactory)
	ctx.Register()

	return ctx, config
}
