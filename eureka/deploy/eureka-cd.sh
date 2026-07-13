#!/bin/bash
# 에러 발생 시 스크립트 실행을 즉시 중단
set -e

# 배포할 서비스의 이름과 포트
SERVICES=("eureka-1" "eureka-2")
PORTS=("10402" "10404")

git pull origin main

# 등록된 서비스 개수만큼 순차적으로 배포 진행
for i in "${!SERVICES[@]}"; do
  SERVICE="${SERVICES[$i]}"
  PORT="${PORTS[$i]}"

  # 해당 컨테이너 재빌드 및 구동
  echo "${SERVICE} 재배포"
  docker compose up -d --build "$SERVICE"

  # 해당 컨테이너가 정상적으로 구동되었는지 헬스체크 검증
  for attempt in {1..30}; do
    if curl -sf "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "${SERVICE} 헬스체크 성공"
      break
    fi

    # 30번(60초) 시도 동안 서버가 켜지지 않으면 배포 실패로 간주하고 스크립트 강제 종료
    if [ "$attempt" -eq 30 ]; then
      echo "${SERVICE} 배포 실패 (타임아웃)"
      exit 1
    fi

    # 서버가 뜰 때까지 2초 간격으로 재시도
    sleep 2
  done

  # 유레카 peer 동기화 대기
  echo "${SERVICE} 피어 동기화 대기 (10초)"
  sleep 10
done

# 빌드 과정에서 생성된 더미 도커 이미지 정리
docker image prune -f
