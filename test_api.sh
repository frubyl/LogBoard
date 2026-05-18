#!/bin/bash

BASE_URL="${1:-http://localhost:8080}"
LOG_URL="${2:-http://localhost:8081}"
COOKIE_JAR=$(mktemp)
TS=$(date +%s)
USERNAME="apitest_${TS}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[OK]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

request() {
    local method=$1 url=$2; shift 2
    curl -s -w "\n__STATUS:%{http_code}" --max-time 15 -X "$method" "$url" "$@"
}

parse()  { echo "$1" | sed 's/__STATUS:.*//' | tr -d '\n'; }
status() { echo "$1" | grep -o '__STATUS:[0-9]*' | cut -d: -f2; }

echo "=============================="
echo "  LogBoard API Test"
echo "  Core: $BASE_URL"
echo "  Log:  $LOG_URL"
echo "=============================="
echo ""

# 1. Register
info "1. Регистрация ($USERNAME)..."
RESP=$(request POST "$BASE_URL/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"TestPass123\"}")
CODE=$(status "$RESP")
if [ "$CODE" = "201" ]; then
  ok "Регистрация: 201 Created"
else
  fail "Регистрация: $CODE — $(parse "$RESP")"
  rm -f "$COOKIE_JAR"; exit 1
fi

# 2. Login
info "2. Вход..."
RESP=$(request POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"TestPass123\"}" \
  -c "$COOKIE_JAR")
CODE=$(status "$RESP")
if [ "$CODE" = "200" ]; then
  ok "Вход: 200 OK"
else
  fail "Вход: $CODE — $(parse "$RESP")"
  rm -f "$COOKIE_JAR"; exit 1
fi

ACCESS_TOKEN=$(grep "access_token" "$COOKIE_JAR" | awk '{print $NF}')
info "   Токен: ${ACCESS_TOKEN:0:40}..."

# 3. Create project
info "3. Создание проекта..."
RESP=$(request POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=$ACCESS_TOKEN" \
  -d '{"name":"Test Project","description":"API test"}')
CODE=$(status "$RESP")
BODY=$(parse "$RESP")
if [ "$CODE" = "201" ]; then
  PROJECT_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  ok "Создание проекта: 201 Created — id=$PROJECT_ID"
else
  fail "Создание проекта: $CODE — $BODY"
  rm -f "$COOKIE_JAR"; exit 1
fi

# 4. Create API key
info "4. Создание API-ключа..."
RESP=$(request POST "$BASE_URL/api-keys" \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=$ACCESS_TOKEN" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"name\":\"test-key-${TS}\"}")
CODE=$(status "$RESP")
BODY=$(parse "$RESP")
if [ "$CODE" = "201" ]; then
  API_KEY=$(echo "$BODY" | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
  KEY_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  ok "API-ключ создан: 201 Created"
  info "   id=$KEY_ID"
  info "   apiKey=$API_KEY"
else
  fail "Создание API-ключа: $CODE — $BODY"
  rm -f "$COOKIE_JAR"; exit 1
fi

# 5. Wait for Kafka to propagate API key
info "5. Ожидание синхронизации API-ключа через Kafka (5 сек)..."
sleep 5

# 6. Ingest logs
info "6. Отправка логов в log-service..."

ingest() {
  local level=$1 msg=$2
  RESP=$(request POST "$LOG_URL/logs/ingest" \
    -H "Content-Type: application/json" \
    -H "X-Api-Key: $API_KEY" \
    -d "{\"projectId\":\"$PROJECT_ID\",\"entries\":[{\"level\":\"$level\",\"message\":\"$msg\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"serviceName\":\"test-service\",\"environment\":\"test\"}]}")
  CODE=$(status "$RESP")
  BODY=$(parse "$RESP")
  if [ "$CODE" = "202" ]; then
    ok "Лог [$level]: 202 Accepted"
  else
    fail "Лог [$level]: $CODE — $BODY"
  fi
}

ingest "INFO"  "Application started"
ingest "ERROR" "NullPointerException in handler"
ingest "WARN"  "Memory usage above 80%"
ingest "DEBUG" "Request received: GET /health"

# 7. Wait for ES replication
info "7. Ожидание индексации в Elasticsearch (8 сек)..."
sleep 8

# 8. Search all logs
info "8. Поиск всех логов..."
RESP=$(request POST "$LOG_URL/logs/search" \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=$ACCESS_TOKEN" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"from\":\"2024-01-01T00:00:00Z\",\"to\":\"2030-01-01T00:00:00Z\"}")
CODE=$(status "$RESP")
BODY=$(parse "$RESP")
if [ "$CODE" = "200" ]; then
  ok "Поиск всех логов: 200 OK"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  fail "Поиск всех логов: $CODE — $BODY"
fi

# 9. Search ERROR logs only
info "9. Поиск только ERROR логов..."
RESP=$(request POST "$LOG_URL/logs/search" \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=$ACCESS_TOKEN" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"level\":[\"ERROR\"],\"from\":\"2024-01-01T00:00:00Z\",\"to\":\"2030-01-01T00:00:00Z\"}")
CODE=$(status "$RESP")
BODY=$(parse "$RESP")
if [ "$CODE" = "200" ]; then
  ok "Поиск ERROR логов: 200 OK"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  fail "Поиск ERROR логов: $CODE — $BODY"
fi

# 10. Timeline
info "10. Таймлайн логов..."
RESP=$(request POST "$LOG_URL/logs/timeline" \
  -H "Content-Type: application/json" \
  -H "Cookie: access_token=$ACCESS_TOKEN" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"from\":\"2024-01-01T00:00:00Z\",\"to\":\"2030-01-01T00:00:00Z\"}")
CODE=$(status "$RESP")
BODY=$(parse "$RESP")
if [ "$CODE" = "200" ]; then
  ok "Таймлайн: 200 OK"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  fail "Таймлайн: $CODE — $BODY"
fi

echo ""
echo "=============================="
echo "  Готово"
echo "=============================="
rm -f "$COOKIE_JAR"
