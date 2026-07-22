#!/bin/bash
# 에러 발생 시 스크립트 실행을 즉시 중단
set -e

git pull origin main
cd ~/msa-infra/gateway

# 배포할 서비스의 이름과 포트
SERVICES=("gateway-1" "gateway-2")
PORTS=("10400" "10401")

# 등록된 서비스 개수만큼 순차적으로 롤링 배포 진행
for i in "${!SERVICES[@]}"; do
  SERVICE="${SERVICES[$i]}"
  PORT="${PORTS[$i]}"

  # nginx가 게이트웨이로 라우팅할 때 유레카를 바라보지 않음 (OUT_OF_SERVICE 전환 불필요)
  # nginx 기본 passive failover(max_fails/fail_timeout, proxy_next_upstream)가 실패한 요청을 살아있는 인스턴스로 자동 전환

  # 해당 컨테이너 재빌드 및 구동
  echo "${SERVICE} 재배포"
  docker compose up -d --build --force-recreate "$SERVICE"

  # 해당 컨테이너가 정상적으로 구동되었는지 헬스체크 검증
  for attempt in {1..30}; do
    if curl -sf "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "${SERVICE} 배포 성공"
      break;
    fi

    # 30번(60초) 시도 동안 서버가 켜지지 않으면 배포 실패로 간주하고 스크립트 강제 종료
    if [ "$attempt" -eq 30 ]; then
      echo "${SERVICE} 배포 실패 (타임아웃)"
      exit 1;
    fi

    # 서버가 뜰 때까지 2초 간격으로 재시도
    sleep 2;
  done
done

# 빌드 과정에서 생성된 더미 도커 이미지 정리
docker image prune -f
