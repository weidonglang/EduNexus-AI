#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"
NACOS_URL="${NACOS_URL:-http://localhost:8848/nacos}"
VERSION="${VERSION:-v1.4.0-final-polish}"
REPORT_DIR="${REPORT_DIR:-reports}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/$REPORT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
MD_PATH="$ROOT/$REPORT_DIR/health-check-$STAMP.md"
JSON_PATH="$ROOT/$REPORT_DIR/health-check-$STAMP.json"
ROWS=()
FAIL_COUNT=0
WARN_COUNT=0

add_check() {
  local name="$1"
  local status="$2"
  local detail="$3"
  local suggestion="${4:-}"
  ROWS+=("$name|$status|$detail|$suggestion")
  if [[ "$status" == "FAIL" ]]; then FAIL_COUNT=$((FAIL_COUNT + 1)); fi
  if [[ "$status" == "WARN" ]]; then WARN_COUNT=$((WARN_COUNT + 1)); fi
}

test_http() {
  local name="$1"
  local url="$2"
  local code
  code="$(curl -k -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" || true)"
  if [[ "$code" =~ ^[23][0-9][0-9]$ || "$code" == "401" || "$code" == "403" ]]; then
    add_check "$name" "PASS" "HTTP $code $url"
  else
    add_check "$name" "FAIL" "Cannot reach $url, HTTP $code" "Start the service or adjust the URL."
  fi
}

test_port() {
  local name="$1"
  local host="$2"
  local port="$3"
  if command -v nc >/dev/null 2>&1 && nc -z "$host" "$port" >/dev/null 2>&1; then
    add_check "$name" "PASS" "$host:$port is reachable"
  else
    add_check "$name" "WARN" "$host:$port is not reachable" "Start the related Docker service if this environment needs it."
  fi
}

test_http "Frontend" "$FRONTEND_URL"
test_http "Backend API" "$BACKEND_URL/api/ai/status"
test_http "Admin health endpoint" "$BACKEND_URL/api/admin/system-health"
test_http "Nacos console" "$NACOS_URL"
test_port "MySQL" "localhost" "3306"
test_port "Redis" "localhost" "6379"
test_port "Nacos" "localhost" "8848"

if [[ -f "$ROOT/docker-compose.yml" ]]; then
  add_check "Docker Compose file" "PASS" "docker-compose.yml exists"
else
  add_check "Docker Compose file" "FAIL" "docker-compose.yml missing" "Restore the compose file before release."
fi

mkdir -p "$ROOT/uploads"
if echo "$STAMP" > "$ROOT/uploads/.health-check" && rm -f "$ROOT/uploads/.health-check"; then
  add_check "Upload directory" "PASS" "$ROOT/uploads is writable"
else
  add_check "Upload directory" "FAIL" "$ROOT/uploads is not writable" "Grant write permission or set UPLOAD_ROOT."
fi

if [[ -f "$ROOT/release/Academic-Nexus-1.4.0.zip" ]]; then
  add_check "Release zip" "PASS" "$ROOT/release/Academic-Nexus-1.4.0.zip"
else
  add_check "Release zip" "WARN" "$ROOT/release/Academic-Nexus-1.4.0.zip not found" "Run scripts/build-release.ps1 -Version 1.4.0 before publishing."
fi

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  OVERALL="FAIL"
elif [[ "$WARN_COUNT" -gt 0 ]]; then
  OVERALL="WARN"
else
  OVERALL="PASS"
fi

{
  echo "# Academic-Nexus Health Check"
  echo
  echo "- Version: $VERSION"
  echo "- Generated: $(date -Iseconds)"
  echo "- Overall: $OVERALL"
  echo
  echo "| Check | Status | Detail | Suggestion |"
  echo "| --- | --- | --- | --- |"
  for row in "${ROWS[@]}"; do
    IFS='|' read -r name status detail suggestion <<< "$row"
    echo "| $name | $status | ${detail//|//} | ${suggestion//|//} |"
  done
} > "$MD_PATH"

{
  echo "{"
  echo "  \"version\": \"$VERSION\","
  echo "  \"generatedAt\": \"$(date -Iseconds)\","
  echo "  \"overall\": \"$OVERALL\","
  echo "  \"checks\": ["
  for i in "${!ROWS[@]}"; do
    IFS='|' read -r name status detail suggestion <<< "${ROWS[$i]}"
    comma=","
    [[ "$i" == "$((${#ROWS[@]} - 1))" ]] && comma=""
    printf '    {"name":"%s","status":"%s","detail":"%s","suggestion":"%s"}%s\n' \
      "$name" "$status" "${detail//\"/\\\"}" "${suggestion//\"/\\\"}" "$comma"
  done
  echo "  ]"
  echo "}"
} > "$JSON_PATH"

echo "Health check overall: $OVERALL"
echo "Markdown report: $MD_PATH"
echo "JSON report: $JSON_PATH"
