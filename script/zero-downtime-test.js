import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const failureRate = new Rate('failed_requests');
const TARGET_URL = __ENV.TARGET_URL || 'https://iunot.cloud/api/account/v1/test';

export const options = {
  scenarios: {
    constant_pacing: {
      executor: 'constant-arrival-rate',  // 실제 사용자 트래픽처럼 일정 속도로 계속 들어오는 k6 전략
      rate: 3,            // timeUnit마다 실행할 요청 수
      timeUnit: '1s',     // rate만큼의 요청 실행 주기
      duration: '180s',   // 테스트 진행 시간 (3분)
      preAllocatedVUs: 5, // 테스트 시작 전 미리 준비해두는 가상유저 수
      maxVUs: 20,         // 추가로 늘릴 수 있는 가상 유저 상한
    },
  },
  thresholds: {
    // 4xx 응답을 가용 범위로 인정하므로, 5xx/연결 단절만 측정하는 커스텀 메트릭(failed_requests)을 기준으로 판정
    failed_requests: ['rate == 0'],
  },
};

export default function zeroDowntimeTest () {
  const params = {
    timeout: '10s',  // nginx 재시도(5s)보다 여유있게 설정
  };

  const res = http.get(TARGET_URL, params);

  // 응답 코드가 2xx~4xx (500 미만)이면 서버가 정상적으로 응답한 것으로 판단
  const isServerAlive = res.status > 0 && res.status < 500;

  const success = check(res, {
    '서버 응답 정상 작동 중 (< 500)': () => isServerAlive,
  });

  if (!success) {
    failureRate.add(1);
    const errType = res.status === 0 ? '서버 연결 끊김 (타임아웃/연결 거부)' : `서버 내부 오류 (HTTP ${res.status})`;
    console.error(`[서비스 중단 발생] 시간: ${new Date().toISOString()} | 유형: ${errType} | 상태코드: ${res.status} | 에러코드: ${res.error_code || '없음'} | 상세내용: ${res.error || '서버 오류 발생'}`);
  } else {
    failureRate.add(0);
  }
}