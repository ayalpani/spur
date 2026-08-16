#!/bin/sh
set -eu

cd /opt/spur/source/deploy
docker compose -f compose.yaml exec -T items /usr/local/bin/spur-items backup
