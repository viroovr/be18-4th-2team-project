# ===============================
# Stage 1: Build
# ===============================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 프로젝트 전체 복사
COPY ./ /app

# Gradle 빌드 (테스트 제외)
RUN ./gradlew clean build -x test

# ===============================
# Stage 2: Run
# ===============================
FROM openjdk:21-jdk-slim
WORKDIR /app

# Build 단계에서 생성된 Jar 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 앱 포트
EXPOSE 8080

# Spring Boot 실행
ENTRYPOINT ["java","-jar","app.jar"]
