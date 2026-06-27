FROM maven:3.9-eclipse-temurin-17 AS ai-build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=ai-build /workspace/target/tianshi-ai-service-0.0.1-SNAPSHOT.jar /app/academic-nexus-ai-service.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/academic-nexus-ai-service.jar"]
