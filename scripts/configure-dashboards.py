#!/usr/bin/env python3
"""Configure the frontend dashboards; never save the Kafka UI password in plaintext."""
import getpass
from pathlib import Path
import re
import subprocess


def main():
    path = Path('.env')
    if not path.exists():
        raise SystemExit('Ejecutar desde ~/Cloud_Native en la EC2 frontend, con su .env existente.')
    original = path.read_text()
    values = {}
    for line in original.splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip("\"'")
    domain = values.get('FRONTEND_DOMAIN', '')
    upstream = values.get('BACKEND_UPSTREAM', '')
    if not re.fullmatch(r'[a-zA-Z0-9.-]+', domain) or not re.fullmatch(r'[0-9.]+:[0-9]+', upstream):
        raise SystemExit('FRONTEND_DOMAIN o BACKEND_UPSTREAM IPv4 ausentes o invalidos; no se modifico .env.')
    username = input('Usuario para Kafka UI [profesor]: ').strip() or 'profesor'
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', username):
        raise SystemExit('Usuario: utilizar letras, numeros, guion o guion bajo.')
    password = getpass.getpass('Contrasena nueva para Kafka UI (oculta, minimo 12 caracteres): ')
    if len(password) < 12:
        raise SystemExit('Se requieren al menos 12 caracteres.')
    if password != getpass.getpass('Confirmar contrasena: '):
        raise SystemExit('Las contrasenas no coinciden.')
    result = subprocess.run(
        ['docker', 'run', '--rm', '-i', 'caddy:2-alpine', 'caddy', 'hash-password'],
        input=password, text=True, capture_output=True, check=True,
    )
    password_hash = result.stdout.strip()
    if not re.fullmatch(r'\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}', password_hash):
        raise SystemExit('Caddy no devolvio un hash bcrypt valido; no se modifico .env.')
    backend_ip = upstream.rsplit(':', 1)[0]
    updates = {
        'KAFKA_UI_DOMAIN': 'kafka.' + domain,
        'RABBIT_UI_DOMAIN': 'rabbit.' + domain,
        'KAFKA_UI_UPSTREAM': backend_ip + ':8090',
        'RABBIT_UI_UPSTREAM': backend_ip + ':15672',
        'KAFKA_UI_USERNAME': username,
        'KAFKA_UI_PASSWORD_HASH': password_hash,
    }
    lines = [line for line in original.splitlines() if line.split('=', 1)[0].strip() not in updates]
    lines.extend(f"{key}='{value}'" for key, value in updates.items())
    path.chmod(0o600)
    path.write_text('\n'.join(lines) + '\n')
    print('Configuracion guardada; la contrasena Kafka no se guardo en texto plano.')
    print('Kafka: https://' + updates['KAFKA_UI_DOMAIN'])
    print('RabbitMQ: https://' + updates['RABBIT_UI_DOMAIN'])
    print('RabbitMQ utiliza su usuario y contrasena existentes del backend.')


if __name__ == '__main__':
    main()
