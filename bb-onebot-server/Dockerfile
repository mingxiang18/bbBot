# 设置本镜像需要使用的基础镜像
FROM openjdk:8-jre

# 设定工作目录
WORKDIR /bot/

# 把jar包添加到镜像中
ADD target/*.jar /bot/application.jar
ADD src/main/resources/static /bot/static

# 容器启动命令
ENTRYPOINT ["java", "-jar","/bot/application.jar", "--spring.profiles.active=prod"]

# 设置时区
RUN /bin/cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo 'Asia/Shanghai' >/etc/timezone