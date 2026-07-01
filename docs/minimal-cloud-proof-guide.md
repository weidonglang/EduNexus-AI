# EduNexus AI v2.0.2 Minimal Cloud Proof Guide

This guide explains the minimal Spring Cloud proof layer added for teacher verification. The goal is screenshotable, runnable evidence for Gateway, Nacos, Sentinel, Seata and OpenFeign without splitting the existing academic business into risky extra services.

## Scope

Included:

- Nacos service discovery for `academic-main`, `academic-ai-service`, and `edunexus-gateway`.
- Spring Cloud Gateway service on host port `9000`.
- Gateway route `/api/** -> lb://academic-main`.
- Sentinel flow control on the real login endpoint `/api/auth/login`, default QPS `3`.
- Dynamic Sentinel login QPS update endpoints for live classroom demonstration.
- OpenFeign proof endpoint calling `academic-ai-service`.
- Seata proof transaction using demo-only tables `cloud_tx_demo_main` and `cloud_tx_demo_ai`.

Not included:

- Splitting courses, grades, exams or applications into new microservices.
- Applying Seata to core academic transactions.
- Replacing existing local IDEA startup on `localhost:8080`.

## Start With Docker Compose

```powershell
copy .env.example .env
docker compose config
docker compose up -d --build
docker compose ps
```

Important host URLs:

| Component | URL |
| --- | --- |
| Gateway | `http://localhost:9000` |
| Main service | `http://localhost:8088` |
| AI service | `http://localhost:18090/internal/ai/status` |
| Nacos | `http://localhost:18848/nacos` |
| Seata console | `http://localhost:7091` |

## Three-Person Demo Split

| Presenter | Spring Cloud content | Live operation |
| --- | --- | --- |
| Student 1 | Nacos + Gateway | Show `academic-main`, `academic-ai-service`, and `edunexus-gateway` in Nacos, then call `GET http://localhost:9000/api/auth/login` and explain the main-service `401` response. |
| Student 2 | Sentinel | Call `POST /api/cloud-proof/sentinel/login-rule?qps=1`, rapidly call login until `429`, then call `POST /api/cloud-proof/sentinel/login-rule?qps=10` to show the rule can be changed live. |
| Student 3 | OpenFeign + Seata | Call `GET /api/cloud-proof/feign/ai-status`, `POST /api/cloud-proof/seata/commit`, and `POST /api/cloud-proof/seata/rollback`. |

## Nacos Screenshot

Open:

```text
http://localhost:18848/nacos
```

Expected services:

```text
academic-main
academic-ai-service
edunexus-gateway
```

## Gateway And OpenFeign Proof

```powershell
curl http://localhost:9000/api/cloud-proof/feign/ai-status
```

Expected evidence:

```text
"transport":"OpenFeign"
"targetService":"academic-ai-service"
```

This proves the host calls Gateway `9000`, Gateway routes to `academic-main`, and the main service calls `academic-ai-service` by OpenFeign.

## Sentinel Login Flow Control Proof

View current login flow rule:

```powershell
curl http://localhost:9000/api/cloud-proof/sentinel/login-rule
```

Lower the rule to QPS 1 for an obvious live demo:

```powershell
curl -X POST "http://localhost:9000/api/cloud-proof/sentinel/login-rule?qps=1"
```

PowerShell example:

```powershell
1..8 | ForEach-Object {
  curl -s -X POST http://localhost:9000/api/auth/login `
    -H "Content-Type: application/json" `
    -d '{"username":"demo","password":"wrong"}'
}
```

Expected evidence after the QPS threshold is exceeded:

```text
登录请求过于频繁，请稍后再试
```

Relax the rule to QPS 10 after the demonstration:

```powershell
curl -X POST "http://localhost:9000/api/cloud-proof/sentinel/login-rule?qps=10"
```

The protected resource is the real login method, not a fake demo endpoint. The startup default can be adjusted with:

```env
SENTINEL_LOGIN_ENABLED=true
SENTINEL_LOGIN_QPS=3
```

The runtime rule can be adjusted without restarting by the `/api/cloud-proof/sentinel/login-rule` endpoint.

## Apipost Collection

Create an Apipost project named:

```text
EduNexus-AI Spring Cloud Final Demo
```

Suggested environment variables:

```text
gateway_base = http://localhost:9000
nacos_base = http://localhost:18848
```

Add these requests:

| Name | Method | URL | Expected evidence |
| --- | --- | --- | --- |
| Gateway route main service | GET | `{{gateway_base}}/api/auth/login` | HTTP `401`, proving Gateway forwarded to main auth logic |
| Nacos service list | GET | `{{nacos_base}}/nacos/v1/ns/service/list?pageNo=1&pageSize=100` | `academic-main`, `academic-ai-service`, `edunexus-gateway` |
| Sentinel current rule | GET | `{{gateway_base}}/api/cloud-proof/sentinel/login-rule` | `resource=authLogin`, current QPS |
| Sentinel set QPS 1 | POST | `{{gateway_base}}/api/cloud-proof/sentinel/login-rule?qps=1` | `qps=1.0` |
| Login repeated | POST | `{{gateway_base}}/api/auth/login` | HTTP `429` after rapid repeated sends |
| Sentinel set QPS 10 | POST | `{{gateway_base}}/api/cloud-proof/sentinel/login-rule?qps=10` | `qps=10.0` |
| OpenFeign AI status | GET | `{{gateway_base}}/api/cloud-proof/feign/ai-status` | `transport=OpenFeign`, `targetService=academic-ai-service` |
| Seata commit | POST | `{{gateway_base}}/api/cloud-proof/seata/commit` | `mainExists=true`, `aiExists=true` |
| Seata rollback | POST | `{{gateway_base}}/api/cloud-proof/seata/rollback` | `mainExists=false`, `aiExists=false` |

Login repeated request body:

```json
{
  "username": "sentinel-test",
  "password": "wrong-password-123"
}
```

## Seata Distributed Transaction Proof

Commit proof:

```powershell
curl -X POST http://localhost:9000/api/cloud-proof/seata/commit
```

Expected data:

```json
{
  "action": "commit",
  "seataEnabled": true,
  "mainExists": true,
  "aiExists": true
}
```

Rollback proof:

```powershell
curl -X POST http://localhost:9000/api/cloud-proof/seata/rollback
```

Expected data:

```json
{
  "action": "rollback",
  "seataEnabled": true,
  "mainExists": false,
  "aiExists": false
}
```

The transaction writes:

- `academic-main` -> `cloud_tx_demo_main`
- `academic-ai-service` -> `cloud_tx_demo_ai`

The rollback endpoint intentionally throws after both writes. Seata should roll back both tables, and the response keeps the intentional error message so the screenshot can explain why rollback was triggered.

## Local IDEA Mode

For normal local development, keep using:

```text
http://localhost:8080
```

If Seata Server is not running, disable Seata:

```powershell
$env:SEATA_ENABLED="false"
```

Nacos discovery is also optional in local development. If disabled, the main system falls back to direct AI service URL mode where applicable.
