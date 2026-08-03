#!/bin/bash

set -e

cd ~/msa-infra/eureka

set -a
source ~/infra/common.env
set +a

docker compose -f compose.yaml pull
docker compose -f compose.yaml up -d --force-recreate

for port in 10402 10403; do
  for attempt in {1..30}; do
    if curl -sf "http://127.0.0.1:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "포트 ${port} 배포 성공"
      break
    fi
    if [ "$attempt" -eq 30 ]; then
      echo "포트 ${port} 배포 실패 (타임아웃)"
      exit 1
    fi
    sleep 2
  done
done