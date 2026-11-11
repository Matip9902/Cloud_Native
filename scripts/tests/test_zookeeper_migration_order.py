"""Regression: mounted application volumes must be measured before stopping them."""
import ast
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch


class VolumeSizingOrderTests(unittest.TestCase):
    def test_application_with_volume_is_sized_before_stop(self):
        source = Path(__file__).parents[1] / 'migrate-zookeeper-ensemble.py'
        tree = ast.parse(source.read_text())
        migration = next(node for node in tree.body if isinstance(node, ast.Try))
        begin = next(i for i, node in enumerate(migration.body)
                     if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'sizes' for t in node.targets))
        end = next(i for i, node in enumerate(migration.body)
                   if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call)
                   and isinstance(node.value.func, ast.Name) and node.value.func.id == 'print'
                   and any(isinstance(a, ast.Constant) and a.value == 'Backup:' for a in node.value.args))
        # Include the earlier freeze block if it appears before sizing (the regression).
        apps = next(i for i, node in enumerate(migration.body)
                    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'apps' for t in node.targets))
        block = ast.Module(body=migration.body[min(begin, apps):end], type_ignores=[])
        active = {'analytics-service': True, 'kafka-1': True}
        calls = []

        def run(command):
            if command[:2] == ['docker', 'exec'] and 'du' in command:
                name = command[2]
                if not active[name]:
                    raise RuntimeError('container is not running')
                calls.append(('size', name))
                return '100 /app/data'
            return 'GROUP TOPIC PARTITION CURRENT-OFFSET'

        def compose(*command):
            if command[0] == 'stop':
                for name in command[3:]:
                    active[name] = False
                    calls.append(('stop', name))

        with tempfile.TemporaryDirectory() as tmp:
            scope = dict(running=list(active), container=lambda s: s,
                         inspect=lambda *_: json.dumps([{'Type': 'volume', 'Destination': '/app/data'}]),
                         run=run, compose=compose, json=json, backup=Path(tmp), brokers=['kafka-1'],
                         shutil=types.SimpleNamespace(disk_usage=lambda _: types.SimpleNamespace(free=10**10)))
            exec(compile(block, str(source), 'exec'), scope)
            self.assertLess(calls.index(('size', 'analytics-service')), calls.index(('stop', 'analytics-service')))
            self.assertFalse(active['analytics-service'])
            self.assertTrue((Path(tmp) / 'groups-before.txt').exists())


if __name__ == '__main__':
    unittest.main()
