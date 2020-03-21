# Lint as: python3
#
# Copyright 2020, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Tests C-Suite's crash detection behavior."""

import csuite_test_utils


class CrashDetectionTest(csuite_test_utils.TestCase):

  def test_no_crash_test_passes(self):
    completed_process = self.run_and_verify(
        test_app_package='android.csuite.nocrashtestapp',
        test_app_module='csuite_no_crash_test_app')

    self.assertRegex(completed_process.stdout, r"""PASSED\s*:\s*1""")

  def test_crash_on_launch_test_fails(self):
    completed_process = self.run_and_verify(
        test_app_package='android.csuite.crashonlaunchtestapp',
        test_app_module='csuite_crash_on_launch_test_app')

    self.assertRegex(completed_process.stdout, r"""FAILED\s*:\s*1""")

  def run_and_verify(self, test_app_package, test_app_module, tag=None):
    """Set up and run the launcher for a given test app."""

    with csuite_test_utils.CSuiteHarness(
    ) as harness, csuite_test_utils.PackageRepository() as repo:
      adb = csuite_test_utils.Adb()

      # We don't check the return code since adb returns non-zero exit code if
      # the package does not exist.
      adb.uninstall(test_app_package, check=False)
      self.assertNotIn(test_app_package, adb.list_packages())

      module_name = harness.add_module(test_app_package)
      repo.add_package_apks(
          test_app_package,
          csuite_test_utils.get_test_app_apks(test_app_module))

      adb.run(['logcat', '-c'])
      launcher_process = harness.run_and_wait([
          '--serial',
          csuite_test_utils.get_device_serial(), 'run', 'commandAndExit',
          'launch', '--gcs-apk-dir',
          repo.get_path(), '-m', module_name
      ])

      logcat_process = adb.run(
          ['logcat', '-d', '-v', 'brief', '-s', tag or test_app_package])
      self.assertIn('App launched', logcat_process.stdout)

      self.assertNotIn(test_app_package, adb.list_packages())

      return launcher_process


if __name__ == '__main__':
  csuite_test_utils.main()
