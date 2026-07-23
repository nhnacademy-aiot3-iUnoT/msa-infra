#!/bin/bash
# 에러 발생 시 스크립트 실행을 즉시 중단
set -e

git pull origin main
cd ~/msa-infra/eureka

export GHCR_OWNER="nhnacademy-aiot3-iunot"
NEW_TAG="${IMAGE_TAG:-latest}"
LAST_GOOD_FILE=".last-good-tag"
OLD_TAG=$(cat "$LAST_GOOD_FILE" 2>/dev/null || echo "latest")

SERVICES=("eureka-1" "eureka-2")
PORTS=("10402" "10403")

deploy_tag() {
  local tag="$1"
  export IMAGE_TAG="$tag"
  docker compose pull

  for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"

    echo "${SERVICE} 재배포 (tag=${tag})"
    docker compose up -d --force-recreate "$SERVICE"

    for attempt in {1..30}; do
      if curl -sf "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "${SERVICE} 헬스체크 성공"
        break
      fi

      if [ "$attempt" -eq 30 ]; then
        echo "${SERVICE} 배포 실패 (타임아웃, tag=${tag})"
        return 1
      fi

      sleep 2
    done

    # 유레카 peer 동기화 대기
    echo "${SERVICE} 피어 동기화 대기 (10초)"
    sleep 10
  done

  return 0
}

if deploy_tag "$NEW_TAG"; then
  echo "$NEW_TAG" > "$LAST_GOOD_FILE"
  docker image prune -f
else
  echo "새 버전(${NEW_TAG}) 배포 실패, 이전 버전(${OLD_TAG})으로 롤백"
  deploy_tag "$OLD_TAG" || echo "롤백도 실패함, 수동 확인 필요"
  docker image prune -f
  exit 1
fi
