# 构建阶段
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS="-Xmx512m -Xms256m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# 说明：基础镜像 eclipse-temurin:21-jre 基于 ubuntu:24.04（noble）。
#   noble 已改用 deb822 格式的 /etc/apt/sources.list.d/ubuntu.sources，
#   旧版 /etc/apt/sources.list 不再存在，因此这里对两种格式都做兼容处理。
#   字体包为 Tianai 滑块验证码（AWT 无头渲染）所必需，不可省略。
RUN set -eux; \
    for f in /etc/apt/sources.list /etc/apt/sources.list.d/ubuntu.sources; do \
        if [ -f "$f" ]; then \
            sed -i 's|archive.ubuntu.com|mirrors.ustc.edu.cn|g; s|security.ubuntu.com|mirrors.ustc.edu.cn|g' "$f"; \
        fi; \
    done; \
    apt-get update; \
    apt-get install -y --no-install-recommends fontconfig fonts-dejavu; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
EXPOSE 6365

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
