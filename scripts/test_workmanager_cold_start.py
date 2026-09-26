#!/usr/bin/env python3
"""Exercise persisted WorkManager jobs after process death on a rooted API-24 emulator.

Does not clear app data or install APKs. Use the isolated package by default;
--package com.jeerovan.comfer explicitly selects a signed release smoke test.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', choices=['com.jeerovan.comfer', 'com.jeerovan.comfer.notificationtest'],
                        default='com.jeerovan.comfer.notificationtest')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--rounds', type=int, default=3)
    args = parser.parse_args()
    assert 1 <= args.rounds <= 10
    adb = ['adb', '-s', args.serial]

    def read(*command):
        return subprocess.check_output(adb + list(command), text=True, timeout=30).strip()

    def pid():
        result = subprocess.run(adb + ['shell', 'pidof', args.package], capture_output=True, text=True, timeout=10)
        return result.stdout.strip()

    assert read('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Emulator required'
    assert read('shell', 'getprop', 'ro.build.version.sdk') == '24', 'This harness targets API 24 job dump syntax'
    assert read('shell', 'su', '0', 'id', '-u') == '0', 'Root shell required'
    args.output.mkdir(parents=True, exist_ok=True)
    log_path = args.output / 'runtime.log'
    with log_path.open('w') as log:
        capture = subprocess.Popen(adb + ['logcat', '-v', 'threadtime', '-T', '1'], stdout=log, stderr=subprocess.STDOUT)
        try:
            for round_number in range(1, args.rounds + 1):
                # A forced early periodic job may be rejected before its due time.
                # Reseed scheduling each round; the measured death below uses SIGKILL.
                read('shell', 'am', 'force-stop', args.package)
                read('shell', 'am', 'start', '-W', '-n', args.package + '/com.jeerovan.comfer.MainActivity')
                jobs = []
                for _ in range(100):
                    dump = read('shell', 'dumpsys', 'jobscheduler')
                    jobs = re.findall(r'JOB #u\d+a\d+/(\d+):[^\n]* ' + re.escape(args.package) +
                                      r'/androidx\.work\.impl\.background\.systemjob\.SystemJobService', dump)
                    if jobs:
                        break
                    time.sleep(.1)
                assert jobs, 'No persisted wallpaper job'
                read('shell', 'am', 'start', '-W', '-a', 'android.settings.SETTINGS')
                old_pid = pid()
                assert old_pid.isdigit(), 'Expected one live application process'
                read('shell', 'su', '0', 'kill', '-9', old_pid)
                for _ in range(50):
                    if not pid():
                        break
                    time.sleep(.1)
                assert not pid(), 'Process death was not established'
                result = read('shell', 'cmd', 'jobscheduler', 'run', '-f', args.package, jobs[0])
                assert 'Running job' in result, result
                new_pid = ''
                for _ in range(100):
                    new_pid = pid()
                    if new_pid and new_pid != old_pid:
                        break
                    time.sleep(.1)
                assert new_pid and new_pid != old_pid, 'Scheduled job did not create a new process'
                time.sleep(10)
                log.flush()
                assert 'Process: ' + args.package + ',' not in log_path.read_text(), 'Application crashed; see runtime.log'
                assert pid() == new_pid, 'Job-started process died or restarted'
                print(f'PASS round {round_number}: job {jobs[0]}, process {old_pid} -> {new_pid}', flush=True)
        finally:
            capture.terminate()
            capture.wait(timeout=10)
    read('shell', 'am', 'start', '-W', '-n', args.package + '/com.jeerovan.comfer.MainActivity')


if __name__ == '__main__':
    main()
