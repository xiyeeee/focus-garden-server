"""Exercise deployment failure paths with command doubles, without real servers."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('deploy.sh').resolve()
REGISTRY = 'crpi-ow7mi2apt624v3c8.cn-heyuan.personal.cr.aliyuncs.com/xiye-docker/'
MOCK_DOCKER = r'''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
with open(os.environ['MOCK_LOG'], 'a') as log:
    log.write(json.dumps(args) + '\n')
if args[0] == 'inspect':
    template = args[2]
    if '.Config.Image' in template:
        print('previous-image:stable')
    elif '.State.Health' in template:
        print('healthy')
    else:
        print('exited' if os.environ.get('FAIL_HEALTH') else 'running')
elif args[:3] == ['compose', 'ps', '-q']:
    print('fixture-container')
elif args[:2] == ['compose', 'pull'] and os.environ.get('FAIL_PULL'):
    sys.exit(1)
elif args[:4] == ['compose', 'exec', '-T', 'mysql']:
    if os.environ.get('FAIL_BACKUP'):
        sys.exit(1)
    print('-- independent fixture backup, no real data')
'''


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.bin = self.directory / 'bin'
        self.bin.mkdir()
        self.write_command('docker', MOCK_DOCKER)
        self.write_command('flock', '#!/bin/sh\nexit 0\n')
        self.write_command('curl', '#!/bin/sh\nexit "${FAIL_PROXY:-0}"\n')
        self.write_command('sleep', '#!/bin/sh\nexit 0\n')
        self.original = '# preserve comment\nUNCHANGED=fixture-value\nIMAGE_SERVER=old-server\nIMAGE_WEB=old-web\n'
        (self.directory / '.env').write_text(self.original)
        (self.directory / 'docker-compose.yml').write_text('services: {}\n')
        self.environment = {
            **os.environ,
            'PATH': str(self.bin) + os.pathsep + os.environ['PATH'],
            'MOCK_LOG': str(self.directory / 'calls.jsonl'),
        }

    def write_command(self, name, content):
        path = self.bin / name
        path.write_text(content)
        path.chmod(0o755)

    def deploy(self, service='server', **flags):
        self.image = REGISTRY + 'focus-garden-' + service + '@sha256:' + 'a' * 64
        return subprocess.run(
            ['bash', str(SCRIPT), str(self.directory), service, self.image],
            env={**self.environment, **flags}, capture_output=True, text=True,
        )

    def calls(self):
        return [json.loads(line) for line in (self.directory / 'calls.jsonl').read_text().splitlines()]

    def test_backend_success_pins_digest_backs_up_database_and_reloads_nginx(self):
        result = self.deploy()
        self.assertEqual(0, result.returncode, result.stderr)
        content = (self.directory / '.env').read_text()
        self.assertIn('IMAGE_SERVER=' + self.image, content)
        self.assertIn('IMAGE_WEB=old-web', content)
        self.assertIn('UNCHANGED=fixture-value', content)
        self.assertEqual(0o600, (self.directory / '.env').stat().st_mode & 0o777)
        self.assertTrue(list((self.directory / 'deploy/backups').glob('*.sql')))
        self.assertIn(['compose', 'up', '-d', '--no-deps', '--no-build', 'server'], self.calls())
        self.assertIn(['compose', 'exec', '-T', 'web', 'nginx', '-s', 'reload'], self.calls())

    def test_frontend_success_preserves_backend_and_does_not_touch_mysql(self):
        result = self.deploy('web')
        self.assertEqual(0, result.returncode, result.stderr)
        content = (self.directory / '.env').read_text()
        self.assertIn('IMAGE_WEB=' + self.image, content)
        self.assertIn('IMAGE_SERVER=old-server', content)
        self.assertFalse(list((self.directory / 'deploy/backups').glob('*.sql')))
        self.assertFalse(any('mysql' in args for args in self.calls()))

    def test_backup_failure_prevents_container_update(self):
        result = self.deploy(FAIL_BACKUP='1')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.original, (self.directory / '.env').read_text())
        self.assertFalse(any(args[:2] == ['compose', 'up'] for args in self.calls()))

    def test_pull_failure_preserves_recorded_version(self):
        result = self.deploy(FAIL_PULL='1')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.original, (self.directory / '.env').read_text())
        self.assertFalse(any(args[:2] == ['compose', 'up'] for args in self.calls()))

    def test_health_failure_does_not_record_success(self):
        result = self.deploy(FAIL_HEALTH='1')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.original, (self.directory / '.env').read_text())
        self.assertFalse(list((self.directory / 'deploy/releases').glob('*.txt')))

    def test_proxy_failure_does_not_record_success(self):
        result = self.deploy(FAIL_PROXY='1')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.original, (self.directory / '.env').read_text())


if __name__ == '__main__':
    unittest.main()
