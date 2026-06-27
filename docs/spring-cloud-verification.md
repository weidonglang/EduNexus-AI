# Academic-Nexus v1.4.0 Spring Cloud Verification

Updated: 2026-06-27

This document records the Spring Cloud/Nacos/OpenFeign implementation and the commands used to verify it for the v1.4.0 closure release.

## Implemented Boundary

The project now has a real service boundary between the main academic system and the AI service.

```text
academic-main:8080
  -> Spring Cloud OpenFeign
  -> Spring Cloud LoadBalancer
  -> Nacos service name: academic-ai-service
  -> academic-ai-service:8090
```

The fixed URL mode is still available for simple local development. In Spring Cloud mode, the main system resolves `academic-ai-service` through Nacos instead of calling only `http://localhost:8090`.

## Main System Configuration

Relevant files:

- `pom.xml`
- `src/main/java/weidonglang/tianshiwebside/TianshiwebsideApplication.java`
- `src/main/java/weidonglang/tianshiwebside/ai/AiServiceFeignClient.java`
- `src/main/java/weidonglang/tianshiwebside/ai/AiRemoteClient.java`
- `src/main/resources/application.properties`
- `src/main/resources/application-demo.properties`

Required dependencies:

```text
spring-cloud-starter-openfeign
spring-cloud-starter-loadbalancer
spring-cloud-starter-alibaba-nacos-discovery
```

Required annotations and client:

```text
@EnableFeignClients
@FeignClient(name = "${app.ai-service.name:academic-ai-service}")
```

Important properties:

```properties
spring.application.name=academic-main
app.ai-service.base-url=${AI_SERVICE_URL:http://localhost:8090}
app.ai-service.name=${AI_SERVICE_NAME:academic-ai-service}
app.ai-service.discovery-enabled=${AI_SERVICE_DISCOVERY_ENABLED:${NACOS_DISCOVERY_ENABLED:false}}
spring.cloud.nacos.discovery.server-addr=${NACOS_ADDR:127.0.0.1:8848}
spring.cloud.nacos.discovery.enabled=${NACOS_DISCOVERY_ENABLED:false}
spring.cloud.nacos.discovery.register-enabled=${NACOS_REGISTER_ENABLED:${NACOS_DISCOVERY_ENABLED:false}}
```

## AI Service Configuration

Relevant files:

- `ai-service/pom.xml`
- `ai-service/src/main/resources/application.properties`
- `ai-service/src/main/java/weidonglang/tianshi/ai/TianshiAiServiceApplication.java`

Important properties:

```properties
spring.application.name=academic-ai-service
server.port=${SERVER_PORT:8090}
spring.cloud.nacos.discovery.server-addr=${NACOS_ADDR:127.0.0.1:8848}
spring.cloud.nacos.discovery.enabled=${NACOS_DISCOVERY_ENABLED:false}
spring.cloud.nacos.discovery.register-enabled=${NACOS_REGISTER_ENABLED:${NACOS_DISCOVERY_ENABLED:false}}
```

## Docker Compose Mode

`docker-compose.yml` now starts:

- `nacos`
- `mysql`
- `redis`
- `academic-ai-service`
- `academic-main`
- `frontend`

In Compose mode, these variables are enabled:

```text
NACOS_DISCOVERY_ENABLED=true
NACOS_REGISTER_ENABLED=true
AI_SERVICE_DISCOVERY_ENABLED=true
AI_SERVICE_NAME=academic-ai-service
```

This means both Java services register with Nacos, and the main system calls the AI service by service name.

## Local Verification Commands

Validated on 2026-06-27 for v1.2, with v1.3.0 adding an AI service-discovery fallback regression test:

```powershell
docker compose config
```

Result: passed.

```powershell
docker compose up -d nacos
Invoke-WebRequest http://localhost:8848/nacos/actuator/health
docker compose stop nacos
```

Result: Nacos started with `nacos/nacos-server:v2.4.3`; health endpoint returned `{"status":"UP"}`. The temporary container was stopped after verification.

```powershell
.\mvnw.cmd test
```

Expected v1.3.0 result: the full backend suite passes, including `QaClosureHttpRegressionTests`.

The test suite includes:

- Feign client registration check.
- AI service name and Nacos configuration check.
- AI safety strategy tests.
- Student class privacy and teacher homeroom class authorization tests.
- Course selection and system interoperability regression tests.

```powershell
cd ai-service
..\mvnw.cmd test
```

Result: build success, no test sources in the AI service module.

```powershell
cd frontend
npm audit
npm run build
```

Result: `npm audit` found 0 vulnerabilities, production build passed.

```powershell
.\scripts\build-release.ps1 -Version 1.4.0
```

Expected result: `release/Academic-Nexus-1.4.0.zip` is generated successfully.

```powershell
java -jar ai-service\target\tianshi-ai-service-0.0.1-SNAPSHOT.jar --server.port=18090 --ollama.enabled=false
java -jar target\tianshiwebside-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=18080 --app.ai-service.base-url=http://localhost:18090
```

Result: AI service returned 200 from `/internal/ai/status`; the main web jar returned the built Vue application from `/`. The temporary Java processes were stopped after verification.

## Manual Nacos Check

Start the infrastructure:

```powershell
docker compose up -d nacos mysql redis
```

Start AI service with discovery enabled:

```powershell
$env:SERVER_PORT="8090"
$env:NACOS_ADDR="127.0.0.1:8848"
$env:NACOS_DISCOVERY_ENABLED="true"
$env:NACOS_REGISTER_ENABLED="true"
$env:OLLAMA_ENABLED="false"
cd ai-service
..\mvnw.cmd spring-boot:run
```

Start the main service with discovery enabled:

```powershell
$env:SPRING_PROFILES_ACTIVE="demo"
$env:NACOS_ADDR="127.0.0.1:8848"
$env:NACOS_DISCOVERY_ENABLED="true"
$env:NACOS_REGISTER_ENABLED="true"
$env:AI_SERVICE_DISCOVERY_ENABLED="true"
$env:AI_SERVICE_NAME="academic-ai-service"
$env:AI_SERVICE_URL="http://localhost:8090"
.\mvnw.cmd spring-boot:run
```

Open:

```text
http://localhost:8848/nacos
```

Expected service list:

```text
academic-main
academic-ai-service
```

The main application log should show that the Feign client uses the service name `academic-ai-service`. If discovery is disabled, the system falls back to `AI_SERVICE_URL`.
