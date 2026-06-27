FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS app-build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./frontend/dist
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=app-build /workspace/target/tianshiwebside-0.0.1-SNAPSHOT.jar /app/academic-nexus-web.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/academic-nexus-web.jar"]
