#!/usr/bin/env python3
"""Store RDS settings locally without putting passwords in shell history or output."""
import getpass
import os
from pathlib import Path
import re
import tempfile

root = Path(__file__).resolve().parent.parent
env = root / '.env'
cert = root / 'infra/rds/global-bundle.pem'
if not cert.is_file():
    raise SystemExit('Copie primero ~/global-bundle.pem a infra/rds/global-bundle.pem.')
host = input('Endpoint RDS (sin puerto): ').strip()
if not re.fullmatch(r'[A-Za-z0-9.-]+\.rds\.amazonaws\.com', host):
    raise SystemExit('Endpoint RDS no válido.')
user = input('Usuario RDS [cloudadmin]: ').strip() or 'cloudadmin'
password = getpass.getpass('Contraseña RDS (oculta): ')
if not password or any(c in password + user for c in '\r\n\x00'):
    raise SystemExit('Credenciales vacías o con caracteres no válidos.')
private_ip = input('IP privada del backend: ').strip()
import ipaddress
ipaddress.IPv4Address(private_ip)
values = dict(RDS_HOST=host, RDS_USERNAME=user, RDS_PASSWORD=password, BACKEND_PRIVATE_IP=private_ip)
# Compose single-quoted values preserve literal dollars; escape apostrophes.
def quote(value):
    return "'" + value.replace("'", "\\'") + "'"
old = env.read_text() if env.exists() else ''
lines = [line for line in old.splitlines() if not re.match(r'\s*(?:export\s+)?(?:' + '|'.join(values) + r')\s*=', line)]
lines.extend(key + '=' + quote(value) for key, value in values.items())
fd, temporary = tempfile.mkstemp(prefix='.rds-env-', dir=root)
try:
    with os.fdopen(fd, 'w') as output:
        output.write('\n'.join(lines) + '\n')
    os.replace(temporary, env)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
print('Configuración guardada en .env con permisos 600. No comparta ese archivo.')
