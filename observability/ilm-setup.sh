#!/bin/bash
# team1-logs-* 인덱스 전용 ILM 정책 등록 스크립트
# 공용 ES 서버(s4.java21.net)에 최초 1회만 실행하면 됨 (다른 팀 정책에는 영향 없음)

set -e

if [ -z "${ES_USER:-}" ] || [ -z "${ES_PASSWORD:-}" ]; then
  echo "ES_USER, ES_PASSWORD 환경변수를 설정한 뒤 실행하세요." >&2
  exit 1
fi

ES_HOST="http://s4.java21.net:9200"

curl -sf -X PUT "${ES_HOST}/_ilm/policy/team1-logs-policy" \
  -u "${ES_USER}:${ES_PASSWORD}" \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "phases": {
        "delete": {
          "min_age": "30d",
          "actions": {
            "delete": {}
          }
        }
      }
    }
  }'

echo "team1-logs-policy 등록 완료 (30일 경과 인덱스 자동 삭제)"
