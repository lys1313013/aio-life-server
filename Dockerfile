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
FROM eclipse-temurin:21-jre-alpine

# 时区
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1001 -S appgroup \
    && adduser -u 1001 -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 45678

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
