#!/usr/bin/env bash
# ============================================================
# start.sh —— 快速启动 aio-life-server（本地开发）
#
# 不拉起任何容器，直接使用现有代码与配置（application.yml + AIO_LIFE_* 环境变量）。
#
# 用法:
#   ./start.sh                  校验 MySQL/Redis 连通后启动服务
#   ./start.sh --clean          mvn clean 后再启动（清除编译残留）
#
# 可选环境变量:
#   SKIP_DEPS_CHECK=true         跳过 MySQL/Redis/Neo4j 连通性校验（远程库或非常规环境）
#   AIO_LIFE_DB_URL=...         数据库地址 host:port，默认 127.0.0.1:3306
#   AIO_LIFE_REDIS_HOST/PORT    Redis 地址与端口，默认 127.0.0.1:6379
#   AIO_LIFE_NEO4J_ENABLED=true Neo4j 为可选模块，仅开启时校验其连通性
#   AIO_LIFE_NEO4J_URI=...      Neo4j 地址 bolt://host:port，默认 bolt://localhost:7687
#   其余 AIO_LIFE_* 环境变量     直接透传给 Spring Boot（数据库密码、MinIO 等）
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

# ---------- 校验硬依赖：MySQL / Redis / 可选 Neo4j ----------
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

  echo ">>> 检查 MySQL / Redis / Neo4j 连通性"
  wait_port "$DB_HOST" "$DB_PORT" "MySQL"
  wait_port "$REDIS_HOST" "$REDIS_PORT" "Redis"

  # Neo4j 为可选模块：仅当开启时才校验，否则跳过
  if [[ "${AIO_LIFE_NEO4J_ENABLED:-false}" == "true" ]]; then
    NEO4J_URI="${AIO_LIFE_NEO4J_URI:-bolt://localhost:7687}"
    NEO4J_ADDR="${NEO4J_URI##*://}"   # 兼容 bolt:// 前缀
    NEO4J_HOST="${NEO4J_ADDR%%:*}"
    NEO4J_PORT="${NEO4J_ADDR##*:}"
    wait_port "$NEO4J_HOST" "$NEO4J_PORT" "Neo4j"
  else
    echo "    - Neo4j 未启用，跳过（设 AIO_LIFE_NEO4J_ENABLED=true 后启用）"
  fi
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