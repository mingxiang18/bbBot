# bb-sandbox：独立常驻沙箱 pod 的镜像（标准 registry / buildx 用）。
# 自带 python（exec 服务用），额外装 bash（ShellExecTool 走 bash -c）与 bubblewrap
# （非特权 user-namespace 隔离）。不装 JRE。
# 钉 3.12-alpine：scipy/matplotlib/reportlab/PyMuPDF 等重库的 musl wheel 在 3.12 覆盖更全。
FROM python:3.12-alpine

# libstdc++/libgomp：PyMuPDF/scipy/matplotlib 等的 musl wheel 链接 C++ 运行时与 OpenMP，alpine 默认无
RUN set -xe && apk --no-cache add bash bubblewrap coreutils libstdc++ libgomp

# 预装文档/数据处理库：沙箱运行时禁网（bwrap --unshare-net），pip install 装不了，
# 故构建期（有网）预装好，运行时离线可用（装在 /usr/local，bwrap 绑 /usr 后沙箱可见）。
# --only-binary 只用预编译 wheel，缺 musl wheel 直接报错而非源码编译卡住；aliyun 源加速。
RUN pip install --no-cache-dir --only-binary=:all: \
        -i https://mirrors.aliyun.com/pypi/simple/ \
        openpyxl python-docx python-pptx xlsxwriter xlrd xlwt \
        pypdf pdfplumber reportlab PyMuPDF \
        pandas numpy scipy matplotlib \
        pillow qrcode \
        beautifulsoup4 lxml markdown html2text PyYAML tabulate \
        chardet charset-normalizer Jinja2 \
        python-dateutil pytz cryptography orjson regex sympy

# 非 root 运行；uid 1000 需与 NFS /bb-bot/agent-files 的写权限对齐（见 k8s 清单注释）
RUN adduser -D -u 1000 sandbox

COPY exec_server.py /app/exec_server.py
WORKDIR /app
USER 1000
EXPOSE 8080
ENTRYPOINT ["python3", "/app/exec_server.py"]
