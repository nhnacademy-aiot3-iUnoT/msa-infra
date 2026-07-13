#!/bin/bash
# 에러 발생 시 스크립트 실행을 즉시 중단
set -e

# 배포할 서비스의 이름과 포트
SERVICES=("gateway-1" "gateway-2")
PORTS=("10400" "10401")

# 등록된 서비스 개수만큼 순차적으로 롤링 배포 진행
for i in "${!SERVICES[@]}"; do
  SERVICE="${SERVICES[$i]}"
  PORT="${PORTS[$i]}"

  # 유레카 및 라우터에서 해당 인스턴스 제외
  echo "${SERVICE} 유레카 상태 변경 (OUT_OF_SERVICE)"
  curl -sf -X PUT -H "Content-Type: application/json" \
    -d '{"status": "OUT_OF_SERVICE"}' \
    "http://127.0.0.1:${PORT}/actuator/service-registry"

  # 유레카에 인스턴스의 상태가 반영될 때까지 대기
  echo "${SERVICE} 유레카 갱신 대기 (35초)"
  sleep 35;

  # 해당 컨테이너 재빌드 및 구동
  echo "${SERVICE} 재배포"
  docker compose up -d --build "$SERVICE"

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
