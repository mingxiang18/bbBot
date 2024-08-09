% 自用windows开发环境用的打包上传镜像脚本，可替换绝对路径后使用 %

% 设置jdk环境变量 %
set JAVA_HOME=C:\Users\ren\.jdks\corretto-17.0.8.1

% 输入docker镜像的打包版本 %
set /p bbVersion=please enter bb-version:
echo %bbVersion%

% 执行maven打包 %
call D:\develop\apache-maven-3.9.2\bin\mvn clean -f pom.xml -s D:\develop\apache-maven-3.9.2\conf\settings.xml -Dmaven.repo.local=D:\develop\apache-maven-3.9.2\repository -DskipTests=true  -P prod
call D:\develop\apache-maven-3.9.2\bin\mvn package -f pom.xml -s D:\develop\apache-maven-3.9.2\conf\settings.xml -Dmaven.repo.local=D:\develop\apache-maven-3.9.2\repository -DskipTests=true -P prod

% 建立镜像并上传到docker服务器 %
cd bb-bot-server/
docker build -t misuaa/bb-bot:%bbVersion% . -f Dockerfile
docker push misuaa/bb-bot:%bbVersion%
