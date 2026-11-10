#!/usr/bin/env bash
# Sequential builds share Maven's BuildKit cache; stop immediately on a failure.
set -euo pipefail
cd "$(dirname "$0")/.."
mirror="${1:-central}"
case "$mirror" in
  central|google) ;;
  *) echo 'Uso: bash scripts/build-local.sh [central|google]' >&2; exit 2 ;;
esac
project="${COMPOSE_PROJECT_NAME:-github_upload}"
compose=(docker compose -p "$project" -f docker-compose.yml -f docker-compose.kafka.yml)
for service in eureka-server inventory-service customer-service supplier-service notification-service audit-service rabbit-admin-service kafka-admin-service analytics-service api-gateway; do
  echo "Construyendo $service (repositorio: $mirror)"
  "${compose[@]}" build --build-arg "MAVEN_CENTRAL_MIRROR=$mirror" "$service"
done
"${compose[@]}" build frontend
"${compose[@]}" up -d --no-build
