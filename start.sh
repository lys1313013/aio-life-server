#!/usr/bin/env bash
# ============================================================
# start.sh —— 一键启动 aio-life-server（本地开发）
#
# 用法:
#   ./start.sh                  默认：拉起 MinIO，校验 MySQL/Redis 后启动服务
#   ./start.sh --clean          mvn clean 后再启动（清除编译残留）
#
# 可选环境变量（均带默认值，按需覆盖）:
#   SKIP_MINIO=true             跳过 MinIO 容器（本机已有 MinIO 时使用）
#   START_NEO4J=true            同时拉起 Neo4j（默认关闭，需配合 AIO_LIFE_NEO4J_ENABLED=true）
#   SKIP_DEPS_CHECK=true         跳过 MySQL/Redis 连通性校验（远程库或非常规环境）
#   MINIO_PORT=1300              MinIO 服务端口（对应 application.yml 中 1300）
#   MINIO_CONSOLE_PORT=1301      MinIO 控制台端口
#   MINIO_USER=... MINIO_PASSWORD=...   MinIO 账号密码，默认 aio_life / aio_life
#   AIO_LIFE_DB_URL=...         数据库地址 host:port，也作为连通性校验目标，默认 127.0.0.1:3306
#   AIO_LIFE_REDIS_HOST/PORT    Redis 地址与端口，默认 127.0.0.1:6379
#   其余 AIO_LIFE_* 环境变量     直接透传给 Spring Boot（密码、MinIO 等）
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 解析参数 ----------
CLEAN=false
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=true ;;
    *) echo "[错误] 未知参数: $arg（仅支持 --clean）" >&2; exit 1 ;;
  esac
done

# ---------- 工具检查 ----------
command -v mvn >/dev/null 2>&1 || { echo "[错误] 未找到 mvn，请先安装 Maven（要求 Java 21）" >&2; exit 1; }

# ---------- 启动基础依赖容器 ----------
if [[ "${SKIP_MINIO:-}" != "true" ]]; then
  command -v docker >/dev/null 2>&1 || { echo "[错误] 启动 MinIO 需要 docker，可设 SKIP_MINIO=true 跳过" >&2; exit 1; }
  echo ">>> 拉起 MinIO 容器 ..."
  export MINIO_PORT="${MINIO_PORT:-1300}"
  export MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-1301}"
  export MINIO_USER="${MINIO_USER:-aio_life}"
  export MINIO_PASSWORD="${MINIO_PASSWORD:-aio_life}"
  # docker compose 引用 .env 文件，缺失会导致报错，补充空占位（已 gitignore）
  if [[ ! -f docker/.env ]]; then
    touch docker/.env
  fi
  docker compose -f docker/docker-compose-minio.yml up -d
fi

if [[ "${START_NEO4J:-}" == "true" ]]; then
  command -v docker >/dev/null 2>&1 || { echo "[错误] 启动 Neo4j 需要 docker" >&2; exit 1; }
  echo ">>> 拉起 Neo4j 容器 ..."
  docker compose -f docker/docker-compose-neo4j.yml up -d
fi

# ---------- 校验硬依赖：MySQL / Redis ----------
port_open() {
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 2 "$1" "$2" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/$1/$2") >/dev/null 2>&1
  fi
}

wait_port() { # $1 host, $2 port, $3 名称
  local host="$1" port="$2" name="$3" t=0
  while ! port_open "$host" "$port"; do
    t=$((t + 2))
    if (( t >= 60 )); then
      echo "[错误] $name ($host:$port) 60 秒内未就绪，请确认已启动" >&2
      exit 1
    fi
    echo "    ⏳ 等待 $name ($host:$port) 就绪 ... ${t}s"
    sleep 2
  done
  echo "    ✔ $name ($host:$port) 就绪"
}

if [[ "${SKIP_DEPS_CHECK:-}" != "true" ]]; then
  # 解析数据库地址，兼容 jdbc:mysql://host:port 与 host:port 两种写法
  DB_ADDR="${AIO_LIFE_DB_URL:-127.0.0.1:3306}"
  DB_ADDR="${DB_ADDR##*://}"
  DB_HOST="${DB_ADDR%%:*}"
  DB_PORT="${DB_ADDR##*:}"
  REDIS_HOST="${AIO_LIFE_REDIS_HOST:-127.0.0.1}"
  REDIS_PORT="${AIO_LIFE_REDIS_PORT:-6379}"

  echo ">>> 检查 MySQL / Redis 连通性"
  wait_port "$DB_HOST" "$DB_PORT" "MySQL"
  wait_port "$REDIS_HOST" "$REDIS_PORT" "Redis"
fi

# ---------- 启动服务 ----------
echo ">>> 环境版本:"
mvn -v | grep -E "Apache Maven|Java version"
echo ">>> 启动 aio-life-server（端口 45678，context-path: /api）..."
echo "    首次启动若数据库为空，请先按 README 执行 sql/ 目录下的建表与初始化脚本"

MVN_ARGS=()
[[ "$CLEAN" == true ]] && MVN_ARGS+=(clean)
MVN_ARGS+=(spring-boot:run)

exec mvn "${MVN_ARGS[@]}"