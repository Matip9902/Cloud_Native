#!/usr/bin/env python3
"""Exercise Kafka's real 1 -> 2 -> 1 group membership on an isolated temporary topic."""
import argparse
import json
import re
import subprocess
import time
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--project', default='github_upload', help='Existing Compose project containing kafka-1')
args = parser.parse_args()

def run(*command, check=True):
    result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
    if check and result.returncode:
        raise RuntimeError(result.stdout[-3000:])
    return result.stdout

broker = run('docker', 'compose', '-p', args.project, '-f', 'docker-compose.yml',
             '-f', 'docker-compose.kafka.yml', 'ps', '-q', 'kafka-1').strip()
if not broker:
    raise SystemExit('Inicie primero los tres brokers del proyecto indicado.')
metadata = json.loads(run('docker', 'inspect', broker))[0]
network = args.project + '_default'
if network not in metadata['NetworkSettings']['Networks']:
    raise SystemExit('No se encontró la red Compose esperada: ' + network)
image = metadata['Config']['Image']
identifier = 'rubrica-' + uuid.uuid4().hex[:12]
topic, group = identifier + '-topic', identifier + '-group'
containers = [identifier + '-one', identifier + '-two']
created = []

def kafka(tool, *options, check=True):
    return run('docker', 'exec', broker, tool, '--bootstrap-server', 'kafka-1:9092', *options, check=check)

def member_snapshot(expected):
    deadline = time.monotonic() + 120
    last = ''
    while time.monotonic() < deadline:
        last = kafka('kafka-consumer-groups', '--group', group, '--describe', '--members', '--verbose', check=False)
        rows = [line.split() for line in last.splitlines() if line.startswith(group + ' ')]
        # Each row has GROUP CONSUMER-ID HOST CLIENT-ID #PARTITIONS ASSIGNMENT.
        assignments = []
        if len(rows) == expected and all(len(row) >= 6 for row in rows):
            for row in rows:
                found = re.search(re.escape(topic) + r'\(([0-9, ]+)\)', ' '.join(row[5:]))
                if found:
                    assignments.extend(int(value) for value in found.group(1).split(','))
            if sorted(assignments) == [0, 1, 2] and all(int(row[4]) > 0 for row in rows):
                print(f'\nPASS: {expected} miembro(s), particiones 0/1/2 asignadas una sola vez.', flush=True)
                print(last, flush=True)
                return
        time.sleep(3)
    raise RuntimeError('No se obtuvo la asignación esperada:\n' + last)

def start(name):
    run('docker', 'run', '-d', '--name', name, '--network', network, '--entrypoint', 'kafka-console-consumer',
        image, '--bootstrap-server', 'kafka-1:9092', '--topic', topic, '--group', group,
        '--consumer-property', 'session.timeout.ms=10000', '--timeout-ms', '300000')
    created.append(name)

try:
    print('Tópico temporal:', topic, 'Grupo temporal:', group, flush=True)
    kafka('kafka-topics', '--create', '--topic', topic, '--partitions', '3', '--replication-factor', '3')
    start(containers[0]); member_snapshot(1)
    start(containers[1]); member_snapshot(2)
    run('docker', 'rm', '-f', containers[1]); created.remove(containers[1]); member_snapshot(1)
    print('PASS: reparto y reasignación con brokers reales. No se publicaron eventos de negocio.', flush=True)
finally:
    for name in created:
        run('docker', 'rm', '-f', name, check=False)
    kafka('kafka-consumer-groups', '--delete', '--group', group, check=False)
    kafka('kafka-topics', '--delete', '--topic', topic, check=False)
