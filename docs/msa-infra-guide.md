# MSA 인프라 가이드

## 1. 도메인 & DNS 설정

### 1.1 도메인 등록 및 네임서버 위임
* **도메인**: `iunot.cloud` 및 `www.iunot.cloud`
* **DNS 관리**: 가비아에서 도메인을 구매한 후, 네임서버를 **Cloudflare**로 위임하여 관리. 이를 통해 Cloudflare의 CDN, 캐싱 및 보안 필터링(DDoS 방어 등) 혜택을 기본적으로 적용받을 수 있다.

### 1.2 포트 할당 범위 제약
모든 팀이 하나의 서버를 할당받아 사용함에 따라 상호 간의 포트 충돌을 방지하기 위해 우리 팀은 **`10400 ~ 10419`** 대역만 할당받아 사용한다.
* **Gateway-1 / Gateway-2**: `10400` / `10401`
* **Eureka-1 / Eureka-2**: `10402` / `10404`
* **Front**: `10403`

---

## 2. HTTP/HTTPS (보안 및 SSL/TLS 정책)

HTTPS 처리를 위해 서버 내에 직접 SSL 인증서를 설치하지 않고, **Cloudflare Flexible SSL** 모드를 적용한다.

```
클라이언트 요청 처리 순서

1. [클라이언트] 브라우저에 iunot.cloud 주소 입력 후 접속 요청
2. [Cloudflare] 도메인을 수신하여 HTTPS(443) 보안 연결 처리 및 유해 트래픽 필터링 수행
3. [Cloudflare -> Nginx] HTTPS를 종료(TLS Termination)하고 우리 서버의 Nginx에 HTTP로 다시 요청
4. [Nginx] upstream(team1_gateway) 설정에 따라 10400(team1-gateway-1) 또는 10401(team1-gateway-2) 포트로 균등 분산(Round-Robin)하여 요청 전달
5. [API Gateway] 들어온 요청의 경로(`/**`)와 라우팅 설정을 대조하여 lb://team1-front 타겟 확인
6. [Gateway -> Eureka] 로컬 Registry 캐시에서 team1-front 인스턴스 정보를 조회
7. [API Gateway] 조회한 컨테이너 IP 주소로 트래픽을 넘겨 최종적으로 team1-front 컨테이너가 요청을 처리하고 응답 반환
```

* 사용자와 Cloudflare 구간은 안전하게 HTTPS(443 포트)로 통신하지만, Cloudflare에서 TLS가 종료되고 우리 서버의 Nginx로 들어올 때는 HTTP(80 포트)로 트래픽을 넘겨준다.
* 추후 Cloudflare 인증서를 발급받아 Full(Strict) SSL/TLS 적용을 고려 중이지만, 주어진 서버는 모든 팀이 호스트 Nginx를 공유하는 구조라 443 포트 적용 시 다른 팀 설정과의 충돌 가능성이 존재한다. (실제로 충돌이 발생했고, 원인은 아직 파악하지 못했다.)

---

## 3. 리버스 프록시 (Nginx & Cloudflare)

### 3.1 Nginx 설정 (`site.conf`)
호스트 OS에 설치된 Nginx는 외부 트래픽을 최초로 맞이하는 단일 진입점(Reverse Proxy) 역할을 수행한다.

```nginx
upstream team1_gateway {
    # 도커 호스트 포트로 매핑된 두 대의 게이트웨이로 트래픽 로드밸런싱
    server 127.0.0.1:10400;
    server 127.0.0.1:10401;
}

server {
    listen 80;
    server_name iunot.cloud www.iunot.cloud;

    access_log /home/aiot3/aiot3-team1/infra/nginx/logs/access.log;
    error_log  /home/aiot3/aiot3-team1/infra/nginx/logs/error.log;

    location / {
        # Cloudflare 프록시 환경에서 실제 클라이언트 IP를 보존하기 위한 설정
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;    # 원본 클라이언트 IP 전달
        proxy_set_header X-Forwarded-Proto $scheme;                     # 원본 프로토콜 전달
        proxy_set_header Host $http_host;                               # 원래 요청의 Host 헤더(iunot.cloud) 전달

        # Upstream 정의로 요청 포워딩
        proxy_pass http://team1_gateway;
    }
}
```

* **게이트웨이 이중화**: `upstream team1_gateway` 설정을 통해 두 대의 Gateway 인스턴스로 트래픽을 분산한다.
* **클라이언트 IP 전달**: Cloudflare 프록시 체인을 거쳐 들어오므로, `$proxy_add_x_forwarded_for` 헤더 설정을 사용하여 클라이언트의 원래 접속 IP를 최종 백엔드 서비스까지 온전히 전달한다.

---

## 4. API Gateway & Eureka (MSA 라우팅)

```
마이크로서비스의 유레카 등록 및 감지 순서

1. [Front] 프론트엔드 컨테이너가 가동(docker compose up)된다.
2. [Front -> Eureka] application.yaml에 등록된 eureka service-url(team1-eureka-1, team1-eureka-2)에 자신의 인스턴스 ID와 health(UP) 등록 요청을 전송한다.
3. [Eureka Peering] team1-eureka-1과 team1-eureka-2가 서로 정보를 교환하여 양쪽 서버 모두에 team1-front 인스턴스 정보가 동기화된다.
4. [Gateway -> Eureka] 게이트웨이는 30초마다 유레카의 서비스 레지스트리를 Fetch하여 로컬 캐시에 저장한다.
5. [Client -> Gateway] 클라이언트가 Gateway로 접속 시, Gateway는 로컬 캐시에서 team1-front의 실제 컨테이너 IP를 찾아 로드밸런싱하여 트래픽을 전달한다.
```

```
[Nginx] -> [Gateway-1 / 2]
                |
                +-- (Discovery) --> [Eureka-1 / 2]
                |
                +-- (Route: lb://) -> 실제 서비스 (front, account, rule-engine, ...)
```

### 4.1 Eureka Server 클러스터링

2대의 유레카 서버가 서로 레지스트리를 교환(Peering)하도록 도커 네트워크상에서 서로를 바라보게 구성한다.
* **Eureka-1 Peer URL**: `http://team1-eureka-2:10404/eureka/`
* **Eureka-2 Peer URL**: `http://team1-eureka-1:10402/eureka/`
* Peer 동기화를 위해 각각 `register-with-eureka`와 `fetch-registry` 프로퍼티를 `true`로 설정한다.

### 4.2 Gateway 라우팅 및 정적 라우팅 전략
보안을 위해 서비스 디스커버리에 등록된 대상을 자동으로 노출하지 않고, 게이트웨이에 코드 형태로 라우팅 경로를 정적 선언하는 방식을 사용한다.

```java
// com.nhnacademy.gateway.config.RouteLocatorConfig

@Configuration
public class RouteLocatorConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("team1-front",
                        p -> p.path("/**").uri("lb://team1-front"))
                .build();
    }
}
```

* 현재는 백엔드 서비스 개발 단계이므로 게이트웨이로 들어오는 모든 경로(`/**`)를 프론트엔드 서비스(`lb://team1-front`)로 포워딩한다. 이후 서비스별 Prefix(`/api/accounts/**` 등) 기반 라우팅으로 확장할 예정이다.

### 4.3 Gateway & Eureka 캐시 갱신 주기와 배포 지연
- **유레카 서버 응답 캐시 (Response Cache)**: 유레카 서버가 레지스트리 정보를 반환할 때 사용하는 읽기 전용 캐시이며, 갱신 주기는 30초이다. (`eureka.server.response-cache-update-interval-ms`)
- **게이트웨이 로컬 캐시 (Client Cache)**: 게이트웨이가 유레카로부터 상태 정보를 가져와 보관하는 캐시이며, 폴링 주기는 30초이다. (`eureka.client.registry-fetch-interval-seconds`)
- 두 캐시의 갱신 타이밍이 완전히 엇갈릴 경우, 서비스 상태 변경 사실이 게이트웨이에 최종 도달하는 데 최대 60초가 소요될 수 있다.

---

## 5. Docker & 컨테이너 실행 환경

Dockerfile은 작성된 소스 코드를 컴파일하고, 구동에 필요한 런타임(JRE) 환경을 묶어 도커 이미지로 빌드하기 위한 설계도로, 서버 환경이나 개발자 PC 환경에 상관 없이 동일한 실행 결과(이식성)를 보장한다.

### 5.1 Dockerfile

```Dockerfile
# 1. 빌드 전용 이미지 (JDK 포함, 최종 이미지엔 안 들어감)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성만 먼저 복사해서 캐싱 (소스코드 변경돼도 의존성 안 바뀌면 이 레이어 재사용됨)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 소스코드 복사 후 빌드 (테스트는 CI에서 이미 돌리니 스킵)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# 2. 실행 전용 이미지 (JRE만, 빌드도구 없어서 이미지 크기 작음)
FROM eclipse-temurin:21-jre
WORKDIR /app

# 헬스체크/디버깅용 curl 설치, 이후 캐시 삭제해서 이미지 용량 절약
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 빌드 단계 결과물(jar)만 가져옴, 소스/빌드도구는 최종 이미지에 안 남음
COPY --from=build /app/target/*.jar app.jar

# root로 실행하지 않도록 비루트 사용자 지정 (보안)
USER 1000

ENTRYPOINT ["java", "-jar", "app.jar"]
```

* 빌드 단계(Eclipse Temurin JDK)와 실행 단계(Eclipse Temurin JRE)를 철저히 격리하여 최종 배포 이미지에 컴파일러나 불필요한 빌드 도구가 포함되지 않도록 경량화한다.
* 컨테이너 침해 시 호스트 OS 권한 취득을 방지하기 위해 컨테이너 내부 실행 권한을 루트가 아닌 `USER 1000`으로 고정하여 구동한다.

### 5.2 docker-compose 및 외부 네트워크 구성

Dockerfile이 개별 서비스를 실행할 이미지를 만드는 도구라면, Docker Compose는 만들어진 여러 개의 서비스 컨테이너들을 묶어서 한꺼번에 실행하고 관리하는 코디네이터 역할을 수행한다. 포트 번호 매핑, 환경변수 주입, 컨테이너 가동 순서, 컨테이너 간 통신을 위한 가상 네트워크 설정을 파일 하나(`compose.yaml`)에 코드로 적어두고 명령어 한 줄로 통합 제어하기 위해 사용한다.

팀별로 독립된 컨테이너 공간을 확보하고, 타 팀 서비스와의 간섭을 없애기 위해 격리 규칙을 적용한다.
* 모든 컨테이너와 네트워크 이름에 `team1-` 접두어를 사용한다.
* 도커 네트워크(`team1-network`)를 사전에 생성하여 공유한다.

```yaml
# compose.yaml

services:
  gateway-1:
    build: .                        # 같은 디렉토리의 Dockerfile로 이미지 빌드
    container_name: team1-gateway-1 # 도커 내부 DNS를 통해 team1-gateway-1 호스트명으로 접근 가능
    environment:                    # application.yaml에 주입할 환경변수
      SERVER_PORT: 10400
    ports:
      - "127.0.0.1:10400:10400"     # 호스트포트(컨테이너 외부로 노출할 포트):컨테이너포트(실제 서비스 포트)
    restart: unless-stopped         # 컨테이너 죽으면 자동재시작, 수동으로 stop한 경우엔 안 살림
    logging:
      driver: json-file             # 도커 기본 로깅 드라이버, 로그를 파일로 저장
      options:
        max-size: "10m"             # 로그파일 하나당 최대 10MB
        max-file: "3"               # 최대 3개까지만 보관 (넘으면 오래된 것부터 삭제)
    networks: [team1-network]

  # gateway-2도 환경변수, 포트번호만 변경해서 똑같이 생성

networks:
  team1-network:
    external: true
```

---

## 6. CI/CD 및 무중단 배포 (Rolling Update)

GitHub Actions를 통해 배포 자동화를 수행하며, 무중단 배포를 위해 쉘 스크립트 기반의 롤링 배포를 동작시킨다.

```
CI/CD 배포 파이프라인 순서 요약

[1. 빌드 및 테스트 검증 (GitHub Actions Runner)]
1. 코드 다운로드 (git checkout) 및 JDK 21 빌드 환경 세팅
2. ./mvnw clean verify 실행 (clean -> compile -> test -> verify 순서로 실행됨)
   - 만약 테스트 중 하나라도 실패하면 배포 단계로 넘어가지 않고 즉시 중단된다.

[2. 서버 원격 접속 및 코드 갱신 (GitHub Actions -> 실제 서버)]
3. GitHub Secrets 변수를 사용해 SSH로 실제 서버에 원격 로그인
4. 서버에서 저장된 프로젝트 디렉토리로 이동 후 `git pull origin main`으로 최신 코드 동기화

[3. 무중단 롤링 배포 수행 (실제 서버 쉘 스크립트)]
5. 롤링 배포 쉘 스크립트 실행
6. 배포할 대상 컨테이너(`team1-gateway-1`)의 유레카 상태를 OUT_OF_SERVICE로 전환 (새 트래픽 유입 차단)
7. 유레카와 게이트웨이의 캐시 갱신 지연을 감안하여 65초 대기 (무중단 배포가 목적이므로 최악의 경우 유레카 캐시 갱신 30초 + 게이트웨이 캐시 갱신 30초 + 여유 시간 5초 동안 대기)
8. docker compose up -d --build --force-recreate 명령어로 대상 컨테이너 새 이미지 빌드 및 재실행
9. 새 컨테이너의 헬스체크(`/actuator/health`)가 `UP`으로 확인되면 다음 컨테이너(`team1-gateway-2`)도 6~8번 과정 동일 반복
```

```yml
# gateway-ci.yml 간소화 버전

name: Gateway CI/CD

on:
  push:
    branches: [ main ]
    paths:
    - 'gateway/**'  # msa-infra는 gateway, eureka를 포함한 모노레포 -> gateway 디렉토리 변경 시에만 트리거되게 설정
  pull_request:
    branches: [ main ]
    paths:
    - 'gateway/**'

jobs:
  build-test:
    name: 빌드 및 테스트
    runs-on: ubuntu-latest
    defaults:
       run:
          working-directory: gateway
    steps:
      - name: 체크아웃 # Repository 코드를 러너로 클론
        uses: actions/checkout@v4

      - name: JDK 21 설정
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven # Maven 라이브러리 캐싱 -> 2회차부터 빌드 빨라짐

      - name: 빌드 및 테스트
        run: ./mvnw -B clean verify # clean -> compile -> test -> verify

  deploy:
    name: 배포
    needs: build-test # 빌드 및 테스트가 통과되어야 배포 단계가 시작된다.
    if: github.event_name == 'push' && github.ref == 'refs/heads/main' # main 직접 push에만 (PR 제외, PR Merge 시 push 트리거)
    runs-on: ubuntu-latest
    steps:
      - name: 배포
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          script: |
            set -e
            cd ~/msa-infra/gateway
            git pull origin main
            ./deploy/gateway-cd.sh
```

```sh
# gateway-cd.sh 간소화 버전

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

  # 유레카 및 라우터에서 해당 인스턴스 제외 (UP -> OUT_OF_SERVICE 상태 변경)
  curl -sf -X POST -H "Content-Type: application/json" \
    -d '{"status": "OUT_OF_SERVICE"}' \
    "http://127.0.0.1:${PORT}/actuator/serviceregistry"

  # 유레카에 인스턴스의 상태가 반영될 때까지 대기
  sleep 65;

  # 해당 컨테이너 재빌드 및 구동
  docker compose up -d --build --force-recreate "$SERVICE"

  # 해당 컨테이너가 정상적으로 구동되었는지 헬스체크 검증
  for attempt in {1..30}; do
    # 헬스체크 성공 시 루프 중단
    if curl -sf "http://127.0.0.1:${PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      break;
    fi

    # 30번(60초) 시도 동안 서버가 켜지지 않으면 배포 실패로 간주하고 스크립트 강제 종료
    if [ "$attempt" -eq 30 ]; then
      exit 1;
    fi

    # 서버가 뜰 때까지 2초 간격으로 재시도
    sleep 2;
  done
done

# 빌드 과정에서 생성된 더미 도커 이미지 정리
docker image prune -f

```

### 6.1 무중단 롤링 배포 흐름 (`gateway-cd.sh`)
두 대의 게이트웨이 인스턴스(`gateway-1`, `gateway-2`)를 순차적으로 업데이트하여 무중단을 보장한다.

```
[team1-gateway-1] OUT_OF_SERVICE 전환 -> (65초 대기) -> 재배포 -> 헬스체크 통과 확인 -> [team1-gateway-2] 동일 진행
```

1. **상태 전환 (`OUT_OF_SERVICE`)**:
   배포 대상 인스턴스로 향하는 신규 트래픽 유입을 즉시 차단하기 위해 게이트웨이 Actuator를 호출하여 유레카 서버 상에서 상태를 `OUT_OF_SERVICE`로 변경한다.
2. **대기 시간 `sleep 65`**:
   유레카 서버와 각 클라이언트들은 시스템 부하를 줄이기 위해 서비스 인스턴스 레지스트리를 즉시 동기화하지 않고 주기적으로 캐싱한다. 이 캐시가 갱신되기까지 약 30초 내외의 시간이 필요하다.
   따라서 스크립트에 최악의 경우를 대비해 `sleep 65` 대기 시간을 두어, 새로운 요청이 더 이상 해당 인스턴스로 라우팅되지 않을 때까지 안전하게 대기한 뒤 도커 재빌드 프로세스를 실행한다.
3. **헬스체크 검증**:
   최소 하나의 서비스가 살아있음을 보장하기 위해 재빌드 완료 후 `http://127.0.0.1:${PORT}/actuator/health` API를 주기적으로 폴링하여 상태가 `"UP"`인 것을 검증한 후에 다음 인스턴스의 배포 단계를 실행한다.

### 6.2 추후 검토 사항

- 배포 스크립트는 Eureka Discovery 기반으로 호출되는 백엔드 서비스 배포를 위한 재사용 템플릿으로 작성되었다. OUT_OF_SERVICE 전환은 게이트웨이가 해당 서비스를 discovery로 조회하는 경로에서 유효하다.
- 다만 게이트웨이 자신을 배포 대상으로 쓸 경우, 외부 트래픽의 진입 경로(Nginx -> Gateway)는 site.conf에 정적 upstream(127.0.0.1:10400/10401)으로 고정되어 있어 Eureka의 상태와 무관하다. 따라서 게이트웨이 배포 시에는 별도 스크립트와 Nginx 설정을 통해 무중단을 별도로 보장한다.
---
