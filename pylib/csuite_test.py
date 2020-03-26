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
"""CSuite-specific automated unit test functionality."""

import argparse
import sys
from typing import Text
import unittest

# Export the TestCase class to reduce the number of imports tests have to list.
TestCase = unittest.TestCase

_DEVICE_SERIAL = None


def get_device_serial() -> Text:
  """Returns the serial of the connected device."""
  if not _DEVICE_SERIAL:
    raise RuntimeError(
        'Device serial is unset, did you call main in your test?')
  return _DEVICE_SERIAL


def main():
  global _DEVICE_SERIAL

  # The below flags are passed in by the TF Python test runner.
  parser = argparse.ArgumentParser()
  parser.add_argument('-s', '--serial', help='the device serial')
  parser.add_argument(
      '--test-output-file',
      help='the file in which to store the test results',
      required=True)
  args, unknown = parser.parse_known_args(sys.argv)

  _DEVICE_SERIAL = args.serial

  with open(args.test_output_file, 'w') as test_output_file:

    # Note that we use a type and not an instance for 'testRunner' since
    # TestProgram forwards its constructor arguments when creating an instance
    # of the runner type. Not doing so would require us to make sure that the
    # parameters passed to TestProgram are aligned with those for creating a
    # runner instance.
    class TestRunner(unittest.TextTestRunner):
      """A test runner that writes test results to the TF-provided file."""

      def __init__(self, *args, **kwargs):
        super(TestRunner, self).__init__(
            stream=test_output_file, *args, **kwargs)

    # Setting verbosity is required to generate output that the TradeFed test
    # runner can parse.
    unittest.TestProgram(verbosity=3, testRunner=TestRunner, argv=unknown)
