# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS gateway-build
WORKDIR /workspace
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
COPY docker/maven/settings.xml /root/.m2/settings.xml
COPY gateway-service/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline
COPY gateway-service/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=gateway-build /workspace/target/edunexus-gateway-0.0.1-SNAPSHOT.jar /app/edunexus-gateway.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/edunexus-gateway.jar"]
