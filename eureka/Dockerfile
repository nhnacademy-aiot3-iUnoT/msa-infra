# 1. 빌드 전용 이미지 (JDK 포함, 최종 이미지엔 안 들어감)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성만 먼저 복사해서 캐싱 (소스코드 변경돼도 의존성 안 바뀌면 이 레이어 재사용됨)
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# 소스코드 복사 후 빌드 (여기서부터 캐시 깨짐, 테스트는 CI에서 이미 돌리니 스킵)
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

EXPOSE 10402
ENTRYPOINT ["java", "-jar", "app.jar"]