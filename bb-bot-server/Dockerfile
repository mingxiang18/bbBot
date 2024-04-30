# 设置本镜像需要使用的基础镜像
FROM openjdk:17-jdk-alpine

# 设定工作目录
WORKDIR /bot/

# 把jar包添加到镜像中
ADD target/*.jar /bot/application.jar
ADD src/main/resources/static /bot/static

# 安装alpine镜像缺少的字体
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
RUN set -xe && apk --no-cache add ttf-dejavu fontconfig

# 容器启动命令
ENTRYPOINT ["java", "-jar","/bot/application.jar", "--spring.profiles.active=prod"]

# 设置时区
# RUN /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone