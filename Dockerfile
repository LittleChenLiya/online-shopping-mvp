FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend-java/pom.xml backend-java/pom.xml
COPY backend-java/src backend-java/src
COPY 01_前端界面 01_前端界面
RUN mvn -B -f backend-java/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
ENV SHOP_HOST=0.0.0.0 SHOP_PORT=8000 SHOP_DB_PATH=/data/shop.db
WORKDIR /app
RUN mkdir -p /data && chown 10001:10001 /data /app
COPY --from=build /build/backend-java/target/online-shopping.jar /app/online-shopping.jar
USER 10001:10001
VOLUME ["/data"]
EXPOSE 8000
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/online-shopping.jar"]
