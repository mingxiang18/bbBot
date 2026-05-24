#!/usr/bin/env bash
# 构建 bb-bot 镜像并推送到 Docker Hub（misuaa/bb-bot）。
#
#   scripts/build/build-push-bb-bot.sh 0.0.1
# 或：
#   VERSION=0.0.1 PLATFORMS=linux/amd64 scripts/build/build-push-bb-bot.sh
#
# 需先 docker login。本仓库 mvn 不在 PATH 上时用 MVN / MAVEN_REPO 环境变量覆盖。
set -euo pipefail

VERSION="${1:-${VERSION:-}}"
if [[ -z "${VERSION}" ]]; then
  echo "Usage: $0 <version>"
  echo "Example: $0 0.0.1"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MVN="${MVN:-/Users/renyuming/Documents/develop/maven/apache-maven-3.6.3/bin/mvn}"
MAVEN_REPO="${MAVEN_REPO:-/Users/renyuming/Documents/develop/maven/repository}"
MODULE="bb-bot-server"
IMAGE="misuaa/bb-bot:${VERSION}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"

cd "${ROOT_DIR}"
"${MVN}" clean package -pl "${MODULE}" -am -f pom.xml \
  -Dmaven.repo.local="${MAVEN_REPO}" -Dmaven.test.skip=true -P prod

docker buildx build --platform "${PLATFORMS}" -t "${IMAGE}" --push \
  "${MODULE}" -f "${MODULE}/Dockerfile"

echo "Pushed ${IMAGE}"
