#!/usr/bin/env python3
"""Migrate an existing standalone ZooKeeper to three voters, preserving its volumes."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--project', default='cloud-native-aws')
parser.add_argument('--compose', action='append', help='Compose files in merge order; repeat as needed')
parser.add_argument('--backup-dir', type=Path)
args = parser.parse_args()
files = args.compose or ['docker-compose.yml', 'docker-compose.kafka.yml',
                        'docker-compose.aws-backend.yml', 'docker-compose.aws-dashboards.yml']
base = ['docker', 'compose', '-p', args.project]
for f in files:
    base += ['-f', f]

def run(cmd, timeout=300):
    p = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    if p.returncode:
        raise RuntimeError('Command failed: ' + ' '.join(cmd[:8]) + '\n' + p.stdout[-5000:])
    return p.stdout.strip()

def compose(*cmd):
    return run(base + list(cmd), timeout=600)

def container(service):
    return compose('ps', '-a', '-q', service)

def inspect(cid, template):
    return run(['docker', 'inspect', '--format', template, cid])

def cluster_id(broker):
    return run(['docker', 'exec', broker, 'bash', '-c',
                'cat /var/lib/kafka/data/meta.properties | grep "^cluster.id="'])

stopped = False
backup = None
try:
    compose('config', '--quiet')
    mem = Path('/proc/meminfo').read_text()
    available = int(next(line.split()[1] for line in mem.splitlines() if line.startswith('MemAvailable:'))) * 1024
    if available < 768 * 1024 * 1024:
        raise RuntimeError('Need at least 768 MiB available RAM before adding two ZooKeeper processes.')
    services = compose('config', '--services').splitlines()
    if not all(s in services for s in ['zookeeper', 'zookeeper-2', 'zookeeper-3', 'kafka-1', 'kafka-2', 'kafka-3']):
        raise RuntimeError('Compose must declare three ZooKeeper nodes and three Kafka brokers.')
    zk = container('zookeeper')
    if not zk or inspect(zk, '{{.State.Running}}') != 'true':
        raise RuntimeError('The original zookeeper must be running before migration.')
    env = json.loads(inspect(zk, '{{json .Config.Env}}'))
    if any(e.startswith('ZOOKEEPER_SERVERS=') for e in env):
        raise RuntimeError('Original node already declares an ensemble; inspect its state rather than migrate twice.')
    for s in ['zookeeper-2', 'zookeeper-3']:
        if container(s):
            raise RuntimeError(s + ' already exists; inspect before migrating.')
    volumes = compose('config', '--format', 'json')
    cfg = json.loads(volumes)
    for v in ['zookeeper_2_data', 'zookeeper_2_log', 'zookeeper_3_data', 'zookeeper_3_log']:
        name = cfg['volumes'][v]['name']
        p = subprocess.run(['docker', 'volume', 'inspect', name], capture_output=True)
        if p.returncode == 0:
            raise RuntimeError('New member volume already exists: ' + name + '; no reuse without inspection.')
    brokers = [container(s) for s in ['kafka-1', 'kafka-2', 'kafka-3']]
    if not all(brokers) or not all(inspect(c, '{{.State.Running}}') == 'true' for c in brokers):
        raise RuntimeError('All three existing brokers must be running.')
    ids = [cluster_id(b) for b in brokers]
    if len(set(ids)) != 1:
        raise RuntimeError('Broker cluster IDs do not agree.')
    image = inspect(zk, '{{.Config.Image}}')
    # Resolve images before stopping any running service.
    compose('pull', 'zookeeper', 'zookeeper-2', 'zookeeper-3')
    backup = (args.backup_dir or Path.home() / ('cloud-native-zk-backup-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'))).resolve()
    backup.mkdir(mode=0o700, parents=True, exist_ok=False)
    for f in files:
        shutil.copy2(f, backup / Path(f).name)
    (backup / 'docker-compose.kafka.standalone.yml').write_text(run(['git', 'show', 'b10296d:docker-compose.kafka.yml']) + '\n')
    (backup / 'cluster-id.txt').write_text(ids[0] + '\n')
    (backup / 'topics-before.txt').write_text(run(['docker', 'exec', brokers[0], 'kafka-topics', '--bootstrap-server', 'kafka-1:9092', '--describe']))
    (backup / 'groups-before.txt').write_text(run(['docker', 'exec', brokers[0], 'kafka-consumer-groups', '--bootstrap-server', 'kafka-1:9092', '--all-groups', '--describe']))
    running = []
    for s in ['inventory-service', 'analytics-service', 'kafka-admin-service', 'kafka-ui', 'kafka-1', 'kafka-2', 'kafka-3', 'zookeeper']:
        if s not in services:
            continue
        cid = container(s)
        if cid and inspect(cid, '{{.State.Running}}') == 'true':
            running.append(s)
    (backup / 'running-services.json').write_text(json.dumps(running))
    mounts = []
    for s in running:
        for m in json.loads(inspect(container(s), '{{json .Mounts}}')):
            if m['Type'] == 'volume' and m['Name'] not in [n for n, _ in mounts]:
                mounts.append((m['Name'], s))
    # Measure volumes while their owning containers are still running.
    sizes = 0
    for service in running:
        for m in json.loads(inspect(container(service), '{{json .Mounts}}')):
            if m['Type'] == 'volume':
                sizes += int(run(['docker', 'exec', container(service), 'du', '-sk', m['Destination']]).split()[0]) * 1024
    if shutil.disk_usage(backup).free < sizes + 512 * 1024 * 1024:
        raise RuntimeError('Not enough free space for volume backups plus 512 MiB reserve.')
    # Freeze writers first so offsets stop advancing before the baseline is captured.
    apps = [s for s in running if s not in ['kafka-1', 'kafka-2', 'kafka-3', 'zookeeper']]
    if apps:
        compose('stop', '-t', '60', *apps)
    stopped = True
    (backup / 'groups-before.txt').write_text(run(['docker', 'exec', brokers[0], 'kafka-consumer-groups', '--bootstrap-server', 'kafka-1:9092', '--all-groups', '--describe']))
    print('Backup:', backup, flush=True)
    compose('stop', '-t', '60', *[s for s in running if s not in apps])
    command = ['docker', 'run', '--rm', '--user', '0']
    for index, (name, _) in enumerate(mounts):
        command += ['-v', name + f':/sources/{index}:ro']
    command += ['-v', str(backup) + ':/backup', '--entrypoint', 'bash', image, '-ec']
    command += ['; '.join(f'tar -cf /backup/volume-{i}.tar -C /sources/{i} .' for i in range(len(mounts)))]
    run(command)
    for index in range(len(mounts)):
        run(['tar', '-tf', str(backup / f'volume-{index}.tar')])
    (backup / 'volumes.json').write_text(json.dumps([{'name': n, 'service': s, 'archive': f'volume-{i}.tar'} for i, (n, s) in enumerate(mounts)], indent=2))
    print('Backups verified. Starting original voter with exactly one fresh peer first.', flush=True)
    # Never let two empty peers form a majority before the original member joins.
    compose('up', '-d', '--no-deps', '--no-build', '--wait', '--wait-timeout', '180', 'zookeeper', 'zookeeper-2')
    compose('up', '-d', '--no-deps', '--no-build', '--wait', '--wait-timeout', '180', 'zookeeper-3')
    print(run([sys.executable, str(Path(__file__).with_name('verify-zookeeper-ensemble.py')), '--project', args.project]), flush=True)
    compose('up', '-d', '--no-deps', '--no-build', '--wait', '--wait-timeout', '240', 'kafka-1', 'kafka-2', 'kafka-3')
    after = [cluster_id(container(s)) for s in ['kafka-1', 'kafka-2', 'kafka-3']]
    if after != ids:
        raise RuntimeError('Cluster ID changed; do not resume writers. Restore using the verified backup.')
    broker = container('kafka-1')
    after_topics = run(['docker', 'exec', broker, 'kafka-topics', '--bootstrap-server', 'kafka-1:9092', '--describe'])
    (backup / 'topics-after.txt').write_text(after_topics)
    (backup / 'groups-after.txt').write_text(run(['docker', 'exec', broker, 'kafka-consumer-groups', '--bootstrap-server', 'kafka-1:9092', '--all-groups', '--describe']))
    import re
    def topic_identity(text):
        result = []
        for line in text.splitlines():
            if 'PartitionCount:' in line:
                result.append(tuple(re.findall(r'(?:Topic|TopicId|PartitionCount|ReplicationFactor):\s*(\S+)', line)))
        return sorted(result)
    def offsets(text):
        result = []
        for line in text.splitlines():
            fields = line.split()
            if len(fields) >= 4 and fields[2].isdigit() and (fields[3].isdigit() or fields[3] == '-'):
                result.append(tuple(fields[:4]))
        return sorted(result)
    if topic_identity((backup / 'topics-before.txt').read_text()) != topic_identity(after_topics):
        raise RuntimeError('Topic identities changed. Writers remain stopped; inspect the backup.')
    if offsets((backup / 'groups-before.txt').read_text()) != offsets((backup / 'groups-after.txt').read_text()):
        raise RuntimeError('Committed offsets changed. Writers remain stopped; inspect the backup.')
    apps = [s for s in running if s not in ['kafka-1', 'kafka-2', 'kafka-3', 'zookeeper']]
    if apps:
        compose('up', '-d', '--no-deps', '--no-build', *apps)
    print('PASS: three ZooKeeper members and three brokers healthy; original cluster ID preserved.', flush=True)
    print('Spring applications may need several minutes. Verify data, lag and functional requests.', flush=True)
except Exception as error:
    print('STOP:', error, file=sys.stderr)
    if stopped:
        print('Migration interrupted. Do not delete volumes or repeat blindly. Backup: ' + str(backup), file=sys.stderr)
    sys.exit(1)
