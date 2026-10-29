# 与 Dockerfile.runtime 同步固定 Agent 版本和多架构镜像摘要；仅复制文件，不继承其 JVM 配置。
FROM apache/skywalking-java-agent:9.8.0-alpine@sha256:805086f04157b5e9046854fe385f73928e802a16be7606e67eb6d6e602941d2a AS skywalking-agent

# ================================
# Stage 1: Build
# ================================
# Java 字节码与目标 CPU 无关，使用构建机原生平台，避免 QEMU 模拟编译。
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 层缓存，源码不变时跳过依赖下载）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# ================================
# Stage 2: Runtime
# ================================
FROM amazoncorretto:21.0.13-alpine3.24@sha256:d942d2e3e0f480c076ec10d03deac7444dc717bc25bb3e2bc5373676b7b6c406

# 时区
RUN apk add --no-cache tzdata fontconfig ttf-dejavu \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

ENV TZ=Asia/Shanghai

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1001 -S appgroup \
    && adduser -u 1001 -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar
COPY --from=skywalking-agent --chown=appuser:appgroup /skywalking/agent/ /opt/skywalking-agent/

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 45678

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
# Agent 默认不加载；部署时通过 JAVA_TOOL_OPTIONS 添加 -javaagent。
ENV SW_LOGGING_OUTPUT=CONSOLE

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
