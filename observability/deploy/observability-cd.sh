#!/bin/bash

set -e

cd ~/msa-infra/observability

docker compose up -d --build --force-recreate

for attempt in {1..15}; do
  if curl -sf http://127.0.0.1:10410/api/health >/dev/null 2>&1; then
    echo "그라파나 정상 가동"
    break;
  fi

  if [ "$attempt" -eq 15 ]; then
    echo "그라파나 배포 실패 (타임아웃)"
    exit 1
  fi

  sleep 2
done