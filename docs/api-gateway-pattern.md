# API Gateway 패턴

<img src="img/architecture.png" />

## 문제

MSA에서는 데이터와 기능이 여러 서비스로 나뉘어 있다. 클라이언트가 이 서비스들을 하나하나 직접 호출하면 다음과 같은 문제가 발생한다.

- 클라이언트가 어떤 기능이 어느 서비스에 있는지 알아야 한다.
- 서비스의 실제 위치(IP/포트)는 배포, 스케일링에 따라 계속 바뀌는데, 클라이언트가 이를 직접 추적해야 한다.

### 해결 방법

클라이언트와 내부 서비스들 사이에 단일 진입점 역할을 하는 Gateway를 둔다.

Gateway는 서비스 디스커버리(Eureka)와 로드밸런서를 이용해 적절한 서비스 인스턴스로 요청을 전달한다.

### 장점

- 클라이언트가 서버 내부 구조를 몰라도 된다.
- 인증, CORS 등 클라이언트 공통 관심사를 일원화할 수 있다.
- 웹, 모바일, 외부 API 등 클라이언트별 진입점을 일관되게 제공할 수 있다.
- 서비스를 추가/분리/재배포해도 클라이언트에 미치는 영향이 최소화된다.

### 단점
- Gateway 자체도 관리해야 하므로 운영 복잡도가 증가한다.
- 요청이 Gateway를 거쳐야 하므로 네트워크 지연이 소폭 증가한다.

---

## API 트래픽의 진입점을 Gateway로 통합한 이유

### 1. 클라이언트 종류와 무관하게 진입점을 하나로 통일

Nginx가 최상단에서 경로 기준(`/api/**` vs 나머지(`/**`)으로 트래픽을 분기하고, 그 중 API 트래픽에 한해 Gateway가 클라이언트 종류와 무관한 단일 진입점 역할을 한다.

모바일 앱, 외부 API 등 다양한 클라이언트가 `/api/**`를 통해 Gateway에 접근하도록 진입점을 통일한다. 클라이언트별 차이가 필요한 경우에도 Gateway에서 공통 정책과 라우팅 규칙을 일관되게 적용할 수 있다.

### 2. Front가 프록시 역할까지 수행

Front를 모든 요청의 진입점으로 두면 화면 렌더링뿐만 아니라 프록시 역할까지 함께 맡게 된다.

Front는 이제 Nginx로부터 직접 트래픽을 받아 렌더링만 전담하고, Gateway는 API 라우팅만 전담해 역할이 명확히 분리된다.

### 3. 횡단 관심사의 일원화

인증(JWT 검증 및 전달), 라우팅, Rate Limiting, CORS, 요청 로깅 등 클라이언트와 공통 관심사를 Gateway에서 일관적으로 처리할 수 있다.

하지만 브라우저의 최초 Front 진입(SSR 페이지 요청)은 Nginx가 직접 처리하므로 이 구간에는 위 정책들이 적용되지 않는다. 현재 요청 로깅/트레이싱 공백이 남아 있다. (추후 개선 과제)

### 4. SPA로 전환해도 진입점이 유지된다

API 호출이 항상 `/api/**` 경로로 Gateway를 거치는 컨벤션만 유지하면, Front를 SPA(React, Vue 등)로 전환해도 이 진입점 구조는 그대로 재사용 가능하다.

```
현재 (SSR): 브라우저 -> Front -> Gateway -> Inventory/Account/RuleEngine

SPA(React/Vue) 전환: 브라우저(JS)가 Gateway의 API를 호출하고, Gateway가 각 내부 서비스로 요청을 라우팅
    브라우저 -> Gateway -> Inventory
    브라우저 -> Gateway -> Account
```

브라우저가 여러 서비스의 서로 다른 오리진을 직접 호출하면 CORS 설정이 필요하지만, Gateway를 동일 오리진의 API 진입점으로 사용하면 브라우저는 Gateway만 호출하므로 서비스별 CORS 설정을 별도로 관리할 필요가 없다.

---

## Front와 Gateway

### Front는 다른 서비스에 어떻게 요청을 보내는가?

```
1) 브라우저 -> Front
   (ex: 의약품 재고 목록 요청)

2) Front -> Gateway -> Inventory / Account / RuleEngine
  (Front가 화면을 완성하는 데 필요한 데이터를 서버 대 서버로 조회)

3) Front가 받아온 데이터를 Thymeleaf 템플릿에 채워 완성된 HTML을 만듦

4) Front -> 브라우저
  (완성된 HTML을 응답으로 돌려줌 - 브라우저는 별도 API 호출을 하지 않아도 완성된 화면을 내려받을 수 있음)
```

페이지 이동 등으로 새로운 화면이 필요할 때마다 이 1~4단계가 반복된다.

### Front가 서버 대 서버로 호출하면 Gateway가 필요 없는 것이 아닌가?

Front가 Eureka로부터 서비스를 찾은 후 돌아온 주소로 직접 API를 호출하는 것도 가능하다.

- 직접 호출: Front -> Eureka에서 Inventory 인스턴스 주소 조회 -> 조회된 주소로 Inventory 직접 호출
- Gateway 경유: Front -> Gateway -> Inventory

그럼에도 Front가 Gateway를 거쳐야 하는 이유가 존재한다.

1. 서비스 디스커버리와 로드밸런싱 로직을 별도로 구현해야 한다.

Eureka는 등록된 인스턴스 목록만 제공하는 레지스트리이다. 여러 인스턴스 중 이번 요청을 어디로 보낼지 결정하는 로드밸런싱 로직은 별도로 필요하다.

Gateway는 Eureka + Spring Cloud LoadBalancer로 이 과정을 처리하고 있지만, Front가 직접 API를 호출하면 서비스 디스커버리와 로드밸런싱을 이용해 적절한 인스턴스를 선택하는 로직을 Front에도 별도로 구현해야 한다.

2. Front가 모든 서비스의 존재를 알아야 한다.

직접 호출 방식이면 Front가 어떤 기능을 어떤 서비스가 제공하는지 모두 알아야 하고, 서비스가 추가/변경될 때마다 Front 코드도 같이 수정하고 재배포 해야 한다.

Gateway를 거치면 Front는 API 계약(URL, 요청/응답 형식)만 알고, 실제 서비스의 위치(IP/포트)나 인스턴스 수는 알 필요가 없다.

---

### 결론

Gateway는 모든 API/백엔드 호출 트래픽의 단일 진입점이다.
Nginx가 경로 기준으로 Front(SSR)와 Gateway(API)를 분기하고, Front는 화면 렌더링에, Gateway는 API 라우팅과 횡단 관심사에 집중한다.

## 참고
- [API 게이트웨이 패턴 - microservices.io](https://microservices.io/patterns/apigateway.html)