# bb-sandbox：独立常驻沙箱 pod 的镜像（标准 registry / buildx 用）。
# python:3-alpine 自带 python（exec 服务用），额外装 bash（ShellExecTool 走 bash -c）
# 与 bubblewrap（非特权 user-namespace 隔离）。不装 JRE。
FROM python:3-alpine

RUN set -xe && apk --no-cache add bash bubblewrap coreutils

# 非 root 运行；uid 1000 需与 NFS /bb-bot/agent-files 的写权限对齐（见 k8s 清单注释）
RUN adduser -D -u 1000 sandbox

COPY exec_server.py /app/exec_server.py
WORKDIR /app
USER 1000
EXPOSE 8080
ENTRYPOINT ["python3", "/app/exec_server.py"]
