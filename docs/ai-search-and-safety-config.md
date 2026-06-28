# AI Search And Safety Configuration

Updated: 2026-06-28

This document explains the v2.0.0 AI web search and safety review configuration workflow.

## Web Search

AI web search lets chat and assistant flows fetch external or simulated search results when a question needs current technical context. It is disabled by default for safety and reproducibility.

Default `.env.example` values are placeholders only:

```env
SEARCH_ENABLED=false
SEARCH_PROVIDER=LOCAL_DEMO
SEARCH_ENDPOINT=
SEARCH_API_KEY=
SEARCH_TIMEOUT_MS=8000
SEARCH_RESULT_LIMIT=5
SEARCH_SAFE_MODE=true
```

Do not commit real API keys.

## Search Templates

The admin AI model page provides these templates:

| Template | Use Case | Network |
| --- | --- | --- |
| Disabled | Fully offline demo | No |
| Local Demo | Classroom and regression verification | No |
| OpenAI-compatible Search API | Custom API with `results[]` response | Yes |
| Tavily / SerpAPI / Bing-compatible | Common search aggregator gateway | Yes |
| Custom HTTP | SearXNG or internal search service | Optional |

The backend endpoint is:

```text
GET /api/admin/ai/search/templates
```

Search test endpoint:

```text
POST /api/admin/ai/search/test
```

Example:

```json
{
  "query": "Academic-Nexus Docker deployment",
  "scene": "ADMIN_TEST"
}
```

Successful local demo tests return sample results. Failed tests return a readable message, provider, latency, and empty results.

## Custom Search API Shape

The custom HTTP search adapter expects JSON like:

```json
{
  "results": [
    {
      "title": "Result title",
      "url": "https://example.com",
      "content": "Short snippet"
    }
  ]
}
```

Compatible aliases:

- `url` or `link`
- `content` or `summary`

## Safety Review

Safety review protects AI input, AI output, notices, search results, and student-submitted content. It records moderation logs when governance tables are available.

Templates:

| Template | Behavior |
| --- | --- |
| Strict | High-risk content blocks immediately |
| Balanced | Warns while preserving logs |
| Relaxed | Routes hits to review-style action |
| Log Only | Does not block, only records |
| Disabled | Disables configured strategy |

Template endpoint:

```text
GET /api/admin/ai/safety/templates
```

Safety test endpoint:

```text
POST /api/admin/ai/safety/test
```

Example:

```json
{
  "scene": "AI_INPUT",
  "content": "这里包含示例敏感词A"
}
```

The response tells the operator whether the content passed, was blocked, or was only logged.

## Troubleshooting

| Symptom | Likely Cause | Fix |
| --- | --- | --- |
| 401 Unauthorized | API key invalid or missing | Check `SEARCH_API_KEY` and endpoint auth mode |
| 403 Forbidden | Provider denied access | Check provider account, region, or permission |
| Timeout | Endpoint unreachable | Check DNS, proxy, firewall, and timeout |
| Empty results | Response field mismatch | Confirm response has `results[]` |
| CORS error in browser | Direct browser request | Use backend test endpoint, not frontend direct fetch |
| Sensitive query blocked | Safety pre-check matched personal data | Use non-personal query or log-only mode for diagnostics |

Recommended verification order:

1. Apply Local Demo template.
2. Run search test.
3. Apply Strict safety template.
4. Run safety test with sample sensitive text.
5. Switch to real provider only after local demo succeeds.
