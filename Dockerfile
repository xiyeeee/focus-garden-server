# 构建 Spring Boot JAR，再装进精简 JRE 镜像，服务器 docker pull 后直接跑。
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests package \
    && mv target/focus-garden-server-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home garden \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/app.jar /app/app.jar
USER garden
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/api/health | grep -q UP
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

