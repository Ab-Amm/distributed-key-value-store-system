FROM openjdk:17-jdk-alpine

WORKDIR /app

COPY target/distributedKeyValue-0.0.1-SNAPSHOT.jar kv.jar

EXPOSE 8080 9870

ENTRYPOINT ["java", "-jar", "kv.jar"]

