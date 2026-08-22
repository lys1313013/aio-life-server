#!/usr/bin/env bash
# ============================================================
# test.sh —— 快速运行测试（aio-life-server）
#
# 用法:
#   ./test.sh                       运行全部测试（自动排除 external 组，见 pom.xml）
#   ./test.sh SomeTest              只跑某个测试类
#   ./test.sh SomeTest#method       只跑某个测试类中的某个方法
#   ./test.sh -Dtest=A,B -D...=...  透传任意 Maven/Surefire 参数
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

command -v mvn >/dev/null 2>&1 || { echo "[错误] 未找到 mvn，请先安装 Maven" >&2; exit 1; }

args=("$@")

# 裸参数（非 - 开头）视为测试类名/方法，转换为 -Dtest=...
if (( ${#args[@]} > 0 )) && [[ "${args[0]}" != -* ]]; then
  args=("-Dtest=${args[0]}")
fi

echo ">>> mvn test ${args[*]}"
exec mvn test "${args[@]}"