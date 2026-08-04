#!/bin/bash
# team1-logs-* 인덱스가 새로 생길 때 자동으로 team1-logs-policy(ILM 정책)를 따르도록 템플릿 등록
# 공용 ES 서버에 최초 1회만 실행

set -e

if [ -z "${ES_HOST:-}" ] || [ -z "${ES_USER:-}" ] || [ -z "${ES_PASSWORD:-}" ]; then
  echo "ES_HOST, ES_USER, ES_PASSWORD 환경변수를 설정한 뒤 실행하세요." >&2
  exit 1
fi

curl -sf -X PUT "${ES_HOST}/_index_template/team1-logs" \
  -u "${ES_USER}:${ES_PASSWORD}" \
  -H "Content-Type: application/json" \
  -d '{
    "index_patterns": ["team1-logs-*"],
    "template": {
      "settings": {
        "index.lifecycle.name": "team1-logs-policy"
      }
    }
  }'

echo "team1-logs 인덱스 템플릿 등록 완료 (신규 인덱스에 ILM 정책 자동 적용)"