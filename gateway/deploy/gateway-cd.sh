#!/bin/bash
# 에러 발생 시 스크립트 실행을 즉시 중단
set -e

git pull origin main
cd ~/msa-infra/gateway

set -a
source ~/infra/common.env
set +a
NEW_TAG="${IMAGE_TAG:-latest}"
LAST_GOOD_FILE=".last-good-tag"
OLD_TAG=$(cat "$LAST_GOOD_FILE" 2>/dev/null || echo "latest")

SERVICES=("gateway-1" "gateway-2")
PORTS=("10400" "10401")

# 지정한 태그로 전체 인스턴스 롤링 배포, 실패하면 false 반환
deploy_tag() {
  local tag="$1"
  export IMAGE_TAG="$tag"
  docker compose -f compose.yaml pull

  for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"

    # nginx가 게이트웨이로 라우팅할 때 유레카를 바라보지 않음 (OUT_OF_SERVICE 전환 불필요)
    # nginx 기본 passive failover(max_fails/fail_timeout, proxy_next_upstream)가 실패한 요청을 살아있는 인스턴스로 자동 전환

    echo "${SERVICE} 재배포 (tag=${tag})"
    docker compose -f compose.yaml up -d --force-recreate "$SERVICE"

    for attempt in {1..30}; do
      if curl -sf "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "${SERVICE} 배포 성공"
        break;
      fi

      if [ "$attempt" -eq 30 ]; then
        echo "${SERVICE} 배포 실패 (타임아웃, tag=${tag})"
        return 1;
      fi

      sleep 2;
    done
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
