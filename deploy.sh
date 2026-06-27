#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Deploy Airport Management System — Docker production stack
# ============================================================
# Prerequisites:
#   1. Docker & Docker Compose plugin installed on the host
#   2. PostgreSQL 18 running natively on the host, port 5432
#   3. acme.sh SSL certificates at:
#        /root/.acme.sh/45.133.74.67_ecc/fullchain.cer
#        /root/.acme.sh/45.133.74.67_ecc/45.133.74.67.key
#   4. acme-challenge webroot: mkdir -p /var/www/acme-challenge
#   5. Run this script from the project root (airport-system/)
# ============================================================

echo "=== [1/3] Rebuilding Docker images (no cache) ==="
docker compose build --no-cache

echo "=== [2/3] Stopping old containers ==="
docker compose down --remove-orphans

echo "=== [3/3] Starting production stack ==="
docker compose up -d

echo "=== Done ==="
echo "  Backend:    https://45.133.74.67/api/v1/airport"
echo "  Dispatcher: https://45.133.74.67/dispatcher/"
echo "  Board:      https://45.133.74.67/board/"
echo ""
echo "=== Useful commands ==="
echo "  Logs:          docker compose logs -f"
echo "  Backend logs:  docker compose logs -f backend"
echo "  Nginx logs:    docker compose logs -f nginx"
echo "  Stop:          docker compose down"
echo "  Rebuild:       docker compose build --no-cache && docker compose up -d"
