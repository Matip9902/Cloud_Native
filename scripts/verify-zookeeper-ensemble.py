#!/usr/bin/env python3
"""Read-only check: three ZooKeeper voters, one leader, two followers."""
import argparse
import subprocess
import sys

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--project', default='cloud-native-aws')
a = p.parse_args()
roles = []
try:
    for name in ['zookeeper', 'zookeeper-2', 'zookeeper-3']:
        ids = subprocess.check_output(['docker', 'ps', '--filter', 'label=com.docker.compose.project=' + a.project,
            '--filter', 'label=com.docker.compose.service=' + name, '--format', '{{.ID}}'], text=True).split()
        if len(ids) != 1:
            raise RuntimeError('Expected exactly one running container for ' + name)
        result = subprocess.check_output(['docker', 'exec', ids[0], 'bash', '-c',
            'echo srvr | nc -w 3 localhost 2181'], text=True, timeout=15)
        role = next((line.split(':', 1)[1].strip() for line in result.splitlines() if line.startswith('Mode:')), '')
        if role not in ['leader', 'follower']:
            raise RuntimeError(name + ': not an active quorum voter: ' + repr(role))
        print(name + ': ' + role)
        roles.append(role)
    if sorted(roles) != ['follower', 'follower', 'leader']:
        raise RuntimeError('Expected one leader and two followers.')
    print('PASS: three ZooKeeper nodes, one leader and two followers.')
except (RuntimeError, subprocess.SubprocessError) as e:
    print('FAIL:', e, file=sys.stderr)
    sys.exit(1)
