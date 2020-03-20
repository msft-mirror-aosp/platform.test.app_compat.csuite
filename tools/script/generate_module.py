# Lint as: python3
#
# Copyright (C) 2019 The Android Open Source Project
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
"""This script generates C-Suite configuration files for a list of apps."""

import argparse
import contextlib
import glob
import os
import sys

from typing import IO, Set, Text
from xml.dom import minidom
from xml.etree import cElementTree as ET
from xml.sax import saxutils

_AUTO_GENERATE_NOTE = 'THIS FILE WAS AUTO-GENERATED. DO NOT EDIT MANUALLY!'
_ANDROID_BP_FILE_NAME = 'Android.bp'
_ANDROID_XML_FILE_NAME = 'AndroidTest.xml'

_CSUITE_LAUNCH_TEST_CLASS =\
  'com.android.compatibility.testtype.AppLaunchTest'

_MODULE_PREPARERS = [
    ('com.android.tradefed.targetprep.TestAppInstallSetup', [{
        'name': 'test-file-name',
        'value': 'csuite-launch-instrumentation.apk'
    }]),
    ('com.android.compatibility.targetprep.AppSetupPreparer', []),
    ('com.android.tradefed.targetprep.RunCommandTargetPreparer', [
        {
            'name': 'run-command',
            'value': 'input keyevent KEYCODE_WAKEUP'
        },
        {
            'name': 'run-command',
            'value': 'input keyevent KEYCODE_MENU'
        },
        {
            'name': 'run-command',
            'value': 'input keyevent KEYCODE_HOME'
        },
    ]),
]

_CONFIG_TYPE_TARGET_PREPARER = 'target_preparer'
_CONFIG_TYPE_TEST = 'test'


def generate_all_modules_from_config(package_list_file_path, root_dir):
  """Generate multiple test and build modules.

  Args:
    package_list_file_path: path of a file containing package names.
    root_dir: root directory that modules will be generated in.
  """
  remove_existing_package_files(root_dir)

  with open(package_list_file_path) as fp:
    for line in parse_package_list(fp):
      _generate_module_files(line.strip(), root_dir)


def remove_existing_package_files(root_dir):
  for filename in glob.iglob(root_dir + '/**/AndroidTest.xml'):
    if _is_auto_generated(filename):
      os.remove(filename)

  for filename in glob.iglob(root_dir + '/**/Android.bp'):
    if _is_auto_generated(filename):
      os.remove(filename)

  _remove_empty_dirs(root_dir)


def _is_auto_generated(filename):
  with open(filename, 'r') as f:
    return _AUTO_GENERATE_NOTE in f.read()


def _remove_empty_dirs(path):
  for filename in os.listdir(path):
    file_path = os.path.join(path, filename)
    if os.path.isdir(file_path) and not os.listdir(file_path):
      os.rmdir(file_path)


def parse_package_list(package_list_file: IO[bytes]) -> Set[bytes]:
  packages = {line.strip() for line in package_list_file.readlines()}
  for package in packages:
    if package and not package.startswith('#'):
      yield package


def _generate_module_files(package_name, root_dir):
  """Generate test and build modules for a single package.

  Args:
    package_name: package name of test and build modules.
    root_dir: root directory that modules will be generated in.
  """
  package_dir = _create_package_dir(root_dir, package_name)

  build_module_path = os.path.join(package_dir, _ANDROID_BP_FILE_NAME)
  test_module_path = os.path.join(package_dir, _ANDROID_XML_FILE_NAME)

  with open(build_module_path, 'w') as f:
    write_build_module(package_name, f)

  with open(test_module_path, 'w') as f:
    write_test_module(package_name, f)


def _create_package_dir(root_dir, package_name):
  package_dir_path = os.path.join(root_dir, package_name)
  os.mkdir(package_dir_path)

  return package_dir_path


def write_build_module(package_name: Text, out_file: IO[bytes]) -> Text:
  build_module = _BUILD_MODULE_HEADER \
      + _BUILD_MODULE_TEMPLATE.format(package_name=package_name)
  out_file.write(build_module)


def write_test_module(package_name: Text, out_file: IO[bytes]) -> Text:
  """Writes the test module for the provided package into a file."""
  configuration = ET.Element('configuration',
                             {'description': 'Tests the compatibility of apps'})
  ET.SubElement(configuration, 'option', {
      'name': 'config-descriptor:metadata',
      'key': 'plan',
      'value': 'app-launch'
  })
  ET.SubElement(configuration, 'option', {
      'name': 'package-name',
      'value': package_name
  })

  for (preparer, options) in _MODULE_PREPARERS:
    _add_element_with_option(
        configuration, _CONFIG_TYPE_TARGET_PREPARER, preparer, options=options)

  _add_element_with_option(configuration, _CONFIG_TYPE_TEST,
                           _CSUITE_LAUNCH_TEST_CLASS)

  test_module = _TEST_MODULE_HEADER + _prettify(configuration)
  out_file.write(test_module)


def _add_element_with_option(elem, sub_elem, class_name, options=None):
  if options is None:
    options = []

  new_elem = ET.SubElement(elem, sub_elem, {
      'class': class_name,
  })
  for option in options:
    ET.SubElement(new_elem, 'option', option)


def _prettify(elem: ET.Element) -> Text:
  declaration = minidom.Document().toxml()
  parsed = minidom.parseString(ET.tostring(elem, 'utf-8'))

  return saxutils.unescape(
      parsed.toprettyxml(indent='    ')[len(declaration) + 1:])


_BUILD_MODULE_HEADER = """// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// {auto_generate_note}

""".format(auto_generate_note=_AUTO_GENERATE_NOTE)

_BUILD_MODULE_TEMPLATE = """csuite_config {{
    name: "csuite_{package_name}",
}}
"""

_TEST_MODULE_HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2019 The Android Open Source Project

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
<!-- {auto_generate_note}-->

""".format(auto_generate_note=_AUTO_GENERATE_NOTE)


def _file_path(path):
  if os.path.isfile(path):
    return path
  raise argparse.ArgumentTypeError('%s is not a valid path' % path)


def _dir_path(path):
  if os.path.isdir(path):
    return path
  raise argparse.ArgumentTypeError('%s is not a valid path' % path)


@contextlib.contextmanager
def _redirect_sys_output(out, err):
  current_out, current_err = sys.stdout, sys.stderr
  try:
    sys.stdout, sys.stderr = out, err
    yield
  finally:
    sys.stdout, sys.stderr = current_out, current_err


def parse_args(args, out=sys.stdout, err=sys.stderr):
  """Parses the provided sequence of arguments."""
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--package_list',
      type=_file_path,
      required=True,
      help='path of the file containing package names')
  parser.add_argument(
      '--root_dir',
      type=_dir_path,
      required=True,
      help='path of the root directory that' + 'modules will be generated in')

  # We redirect stdout and stderr to improve testability since ArgumentParser
  # always writes to those files. More specifically, the TradeFed python test
  # runner will choke parsing output that is not in the expected format.
  with _redirect_sys_output(out, err):
    return parser.parse_args(args)


def main():
  parser = parse_args(sys.argv[1:])
  generate_all_modules_from_config(parser.package_list, parser.root_dir)


if __name__ == '__main__':
  main()
