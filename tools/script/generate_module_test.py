#!/usr/bin/env python
#
# Copyright (C) 2020 The Android Open Source Project
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

# Disable import errors since repo upload hooks don't currently add external
# library dependencies on the Python path.
# TODO(hzalek): Move this configuration to a pylintrc file.
# pylint: disable=import-error

import io
import os
import unittest

from xml.etree import cElementTree as ET
from pyfakefs import fake_filesystem_unittest # pylint: disable=import-error

import generate_module


_AUTO_GENERATE_NOTE = 'THIS FILE WAS AUTO-GENERATED. DO NOT EDIT MANUALLY!'


class WriteTestModuleTest(unittest.TestCase):

    def test_xml_is_valid(self):
        package_name = 'package_name'
        out = io.StringIO()

        generate_module.write_test_module(package_name, out)

        test_module_generated = out.getvalue()
        self.assertTrue(self._contains_license(test_module_generated))
        self.assertTrue(self._is_validate_xml(test_module_generated))

    def _contains_license(self, generated_str: bytes) -> bool:
        return 'Copyright' in generated_str and \
                'Android Open Source Project' in generated_str

    def _is_validate_xml(self, xml_str: bytes) -> bool:
        ET.parse(io.BytesIO(xml_str.encode('utf8')))
        return True


class WriteBuildModuleTest(unittest.TestCase):

    def test_build_file_is_valid(self):
        package_name = 'package_name'
        out = io.StringIO()

        generate_module.write_build_module(package_name, out)

        build_module_generated = out.getvalue()
        self.assertTrue(self._contains_license(build_module_generated))
        self.assertTrue(self._are_parentheses_balanced(build_module_generated))
        self.assertIn('csuite_config', build_module_generated)
        self.assertIn(package_name, build_module_generated)

    def _contains_license(self, generated_str: bytes) -> bool:
        return 'Copyright' in generated_str and \
                'Android Open Source Project' in generated_str

    def _are_parentheses_balanced(self, generated_str: bytes) -> bool:
        parenthese_count = 0

        for elem in generated_str:
            if elem == '{':
                parenthese_count += 1
            elif elem == '}':
                parenthese_count -= 1

            if parenthese_count < 0:
                return False

        return parenthese_count == 0


class ParsePackageListTest(unittest.TestCase):

    def test_accepts_empty_lines(self):
        lines = io.StringIO('\n\n\npackage_name\n\n')

        package_list = generate_module.parse_package_list(lines)

        self.assertListEqual(['package_name'], list(package_list))

    def test_strips_trailing_whitespace(self):
        lines = io.StringIO('  package_name  ')

        package_list = generate_module.parse_package_list(lines)

        self.assertListEqual(['package_name'], list(package_list))

    def test_duplicate_package_name(self):
        lines = io.StringIO('\n\npackage_name\n\npackage_name\n')

        package_list = generate_module.parse_package_list(lines)

        self.assertListEqual(['package_name'], list(package_list))

    def test_ignore_comment_lines(self):
        lines = io.StringIO('\n# Comments.\npackage_name\n')

        package_list = generate_module.parse_package_list(lines)

        self.assertListEqual(['package_name'], list(package_list))


class ParseArgsTest(fake_filesystem_unittest.TestCase):

    def setUp(self):
        super(ParseArgsTest, self).setUp()
        self.setUpPyfakefs()

    def test_configuration_file_not_exist(self):
        package_list_file_path = '/test/package_list.txt'
        root_dir = '/test/modules'
        os.makedirs(root_dir)

        with self.assertRaises(SystemExit):
            generate_module.parse_args(
                ['--package_list', package_list_file_path,
                 '--root_dir', root_dir],
                out=io.StringIO(),
                err=io.StringIO())

    def test_module_dir_not_exist(self):
        package_list_file_path = '/test/package_list.txt'
        package_name1 = 'package_name_1'
        package_name2 = 'package_name_2'
        self.fs.create_file(package_list_file_path,
                            contents=(package_name1+'\n'+package_name2))
        root_dir = '/test/modules'

        with self.assertRaises(SystemExit):
            generate_module.parse_args(
                ['--package_list', package_list_file_path,
                 '--root_dir', root_dir],
                out=io.StringIO(),
                err=io.StringIO())


class GenerateAllModulesFromConfigTest(fake_filesystem_unittest.TestCase):

    def setUp(self):
        super(GenerateAllModulesFromConfigTest, self).setUp()
        self.setUpPyfakefs()

    def test_creates_package_files(self):
        package_list_file_path = '/test/package_list.txt'
        package_name1 = 'package_name_1'
        package_name2 = 'package_name_2'
        self.fs.create_file(package_list_file_path,
                            contents=(package_name1+'\n'+package_name2))
        root_dir = '/test/modules'
        self.fs.create_dir(root_dir)

        generate_module.generate_all_modules_from_config(
            package_list_file_path, root_dir)

        self.assertTrue(os.path.exists(
            os.path.join(root_dir, package_name1, 'Android.bp')))
        self.assertTrue(os.path.exists(
            os.path.join(root_dir, package_name1, 'AndroidTest.xml')))
        self.assertTrue(os.path.exists(
            os.path.join(root_dir, package_name2, 'Android.bp')))
        self.assertTrue(os.path.exists(
            os.path.join(root_dir, package_name2, 'AndroidTest.xml')))

    def test_removes_all_existing_package_files(self):
        root_dir = '/test/'
        package_dir = '/test/existing_package/'
        self.fs.create_file('test/existing_package/AndroidTest.xml',
                            contents=_AUTO_GENERATE_NOTE)
        self.fs.create_file('test/existing_package/Android.bp',
                            contents=_AUTO_GENERATE_NOTE)

        generate_module.remove_existing_package_files(root_dir)

        self.assertFalse(os.path.exists(package_dir))


if __name__ == '__main__':
    # Setting verbosity is required to generate output that the TradeFed test
    # runner can parse.
    unittest.main(verbosity=3)
