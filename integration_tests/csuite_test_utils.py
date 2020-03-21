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
"""Utilities for C-Suite tests."""

import argparse
import contextlib
import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Sequence, Text
import unittest
import zipfile

# Export the TestCase class to reduce the number of imports tests have to list.
TestCase = unittest.TestCase


class CSuiteHarness(contextlib.AbstractContextManager):
  """Interface class for interacting with the C-Suite harness.

  WARNING: Explicitly clean up created instances or use as a context manager.
  Not doing so will result in a ResourceWarning for the implicit cleanup which
  confuses the TradeFed Python test output parser.
  """

  def __init__(self):
    self._tmp_dir_obj = tempfile.TemporaryDirectory(prefix='csuite')
    self._suite_dir = pathlib.Path(self._tmp_dir_obj.name)

    with zipfile.ZipFile(_get_standalone_zip_path(), 'r') as f:
      f.extractall(self._suite_dir)

    # Add owner-execute permission on scripts since zip does not preserve them.
    self._launcher_binary = self._suite_dir.joinpath(
        'android-csuite/tools/csuite-tradefed')
    _add_owner_exec_permission(self._launcher_binary)

    self._generate_module_binary = self._suite_dir.joinpath(
        'android-csuite/tools/csuite_generate_module')
    _add_owner_exec_permission(self._generate_module_binary)

    self._testcases_dir = self._suite_dir.joinpath('android-csuite/testcases')

  def __exit__(self, unused_type, unused_value, unused_traceback):
    self.cleanup()

  def cleanup(self):
    self._tmp_dir_obj.cleanup()

  def add_module(self, package_name: Text) -> Text:
    """Generates and adds a test module for the provided package."""
    module_name = 'csuite_%s' % package_name

    with tempfile.TemporaryDirectory() as o:
      out_dir = pathlib.Path(o)
      package_list_path = out_dir.joinpath('packages.list')

      package_list_path.write_text(package_name + '\n')

      flags = ['--package_list', package_list_path, '--root_dir', out_dir]

      subprocess.check_output(
          [self._generate_module_binary] + flags,
          stderr=subprocess.PIPE,
          universal_newlines=True)

      out_file_path = self._testcases_dir.joinpath(module_name + '.config')
      shutil.copy(
          out_dir.joinpath(package_name, 'AndroidTest.xml'), out_file_path)

      return module_name

  def run_and_wait(self, flags: Sequence[Text]) -> subprocess.CompletedProcess:
    """Starts the Tradefed launcher and waits for it to complete."""
    env = os.environ.copy()

    # Clear environment variables that would cause the script to think it's in a
    # build tree.
    del env['ANDROID_BUILD_TOP']
    del env['ANDROID_HOST_OUT']

    # Clear environment variables that would cause TradeFed to find test configs
    # other than the ones created by the test.
    del env['ANDROID_HOST_OUT_TESTCASES']
    del env['ANDROID_TARGET_OUT_TESTCASES']

    # Clear environment variables that might cause the suite to pick up a
    # connected device that wasn't explicitly specified.
    del env['ANDROID_SERIAL']

    # Set the environment variable that TradeFed requires to find test modules.
    env['ANDROID_TARGET_OUT_TESTCASES'] = self._testcases_dir

    return subprocess.run(
        [self._launcher_binary] + flags,
        capture_output=True,
        env=env,
        universal_newlines=True,
        check=False)


class PackageRepository(contextlib.AbstractContextManager):
  """A file-system based APK repository for use in tests.

  WARNING: Explicitly clean up created instances or use as a context manager.
  Not doing so will result in a ResourceWarning for the implicit cleanup which
  confuses the TradeFed Python test output parser.
  """

  def __init__(self):
    self._tmp_dir_obj = tempfile.TemporaryDirectory(prefix='csuite_apk_dir')
    self._root_dir = pathlib.Path(self._tmp_dir_obj.name)

  def __exit__(self, unused_type, unused_value, unused_traceback):
    self.cleanup()

  def cleanup(self):
    self._tmp_dir_obj.cleanup()

  def get_path(self) -> pathlib.Path:
    """Returns the path to the repository's root directory."""
    return self._root_dir

  def add_package_apks(self, package_name: Text,
                       apk_paths: Sequence[pathlib.Path]):
    """Adds the provided package APKs to the repository."""
    apk_dir = self._root_dir.joinpath(package_name)

    # Raises if the directory already exists.
    apk_dir.mkdir()
    for f in apk_paths:
      shutil.copy(f, apk_dir)


class Adb:
  """Encapsulates adb functionality to simplify usage in tests.

  Most methods in this class raise an exception if they fail to execute. This
  behavior can be overridden by using the check parameter.
  """

  def __init__(self,
               adb_binary_path: pathlib.Path = None,
               device_serial: Text = None):
    self._args = [adb_binary_path or 'adb']

    device_serial = device_serial or get_device_serial()
    if device_serial:
      self._args.extend(['-s', device_serial])

  def shell(self,
            args: Sequence[Text],
            check: bool = None) -> subprocess.CompletedProcess:
    """Runs an adb shell command and waits for it to complete.

    Note that the exit code of the returned object corresponds to that of
    the adb command and not the command executed in the shell.

    Args:
      args: a sequence of program arguments to pass to the shell.
      check: whether to raise if the process terminates with a non-zero exit
        code.

    Returns:
      An object representing a process that has finished and that can be
      queried.
    """
    return self.run(['shell'] + args, check)

  def run(self,
          args: Sequence[Text],
          check: bool = None) -> subprocess.CompletedProcess:
    """Runs an adb command and waits for it to complete."""
    if check is None:
      check = True
    return subprocess.run(
        self._args + args,
        capture_output=True,
        universal_newlines=True,
        check=check)

  def uninstall(self, package_name: Text, check: bool = None):
    """Uninstalls the specified package."""
    self.run(['uninstall', package_name], check=check)

  def list_packages(self) -> Sequence[Text]:
    """Lists packages installed on the device."""
    p = self.shell(['pm', 'list', 'packages'])
    return [l.split(':')[1] for l in p.stdout.splitlines()]


def _add_owner_exec_permission(path: pathlib.Path):
  path.chmod(path.stat().st_mode | stat.S_IEXEC)


def get_test_app_apks(app_module_name: Text) -> Sequence[pathlib.Path]:
  """Returns a test app's apk file paths."""
  return [_get_test_file(app_module_name + '.apk')]


def _get_standalone_zip_path():
  """Returns the suite standalone zip file's path."""
  return _get_test_file('csuite-standalone.zip')


def _get_test_file(name: Text) -> pathlib.Path:
  test_dir = _get_test_dir()
  test_file = test_dir.joinpath(name)

  if not test_file.exists():
    raise RuntimeError('Unable to find the file `%s` in the test execution dir '
                       '`%s`; are you missing a data dependency in the build '
                       'module?' % (name, test_dir))

  return test_file


def _get_test_dir() -> pathlib.Path:
  return pathlib.Path(__file__).parent


_DEVICE_SERIAL = None


def get_device_serial() -> Text:
  """Returns the serial of the connected device."""
  if not _DEVICE_SERIAL:
    raise RuntimeError(
        'Device serial is unset, did you call main in your test?')
  return _DEVICE_SERIAL


def main():
  global _DEVICE_SERIAL

  parser = argparse.ArgumentParser()
  parser.add_argument('-s', '--serial', help='the device serial')
  args, unknown = parser.parse_known_args(sys.argv)

  _DEVICE_SERIAL = args.serial

  # Setting verbosity is required to generate output that the TradeFed test
  # runner can parse.
  unittest.main(verbosity=3, argv=unknown)
