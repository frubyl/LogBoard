# Log Service

Log Service отвечает за приём, хранение, поиск и аналитику логов.

## Обязанности

- Приём пакетных логов от клиентских приложений через API ключ
- Асинхронная индексация логов в Elasticsearch
- Полнотекстовый поиск логов с фильтрацией и cursor-based пагинацией
- Построение временного таймлайна через Elasticsearch агрегации
- Отслеживание статуса каждой загрузки (`ingestion_status`)

## Архитектура

```
┌─────────────────────┐
│     Контроллеры     │ ← LogIngestionController, LogController
├─────────────────────┤
│      Сервисы        │ ← LogIngestionService, LogSearchService, ApiKeyValidationService
├─────────────────────┤
│    Репозитории      │ ← ElasticsearchLogRepository, SharedApiKeyRepository,
│                     │   SharedProjectMemberRepository, IngestionStatusRepository
├─────────────────────┤
│    Хранилища        │ ← Elasticsearch (логи), PostgreSQL (ingestion_status, api_keys*, project_members*)
└─────────────────────┘
* read-only таблицы Core Service
```

## Безопасность — два цепочки фильтров Spring Security

Log Service настраивает два отдельных `SecurityFilterChain` с разными механизмами аутентификации:

| `@Order` | Пути | Механизм | Principal |
|---|---|---|---|
| 1 | `/logs/ingest/**` | `X-API-Key` header → HMAC-SHA256 → `api_keys` в БД | `ApiKeyPrincipal(keyId, projectId)` |
| 2 | `/logs/**` | JWT cookie `access_token` (shared secret с Core) | `JwtPrincipal(userId)` |

### Валидация API ключа

1. `ApiKeyAuthFilter` читает заголовок `X-API-Key`
2. `ApiKeyValidationService.validate()` вычисляет HMAC-SHA256 хэш
3. Ищет совпадение в таблице `api_keys` через `SharedApiKeyRepository`
4. Проверяет срок действия (`expires_at`)
5. Устанавливает `ApiKeyPrincipal` в `SecurityContext`

### Валидация JWT

1. `JwtAuthFilter` читает cookie `access_token`
2. `JwtUtil.isTokenValid()` / `extractUserId()` парсят токен (HS512, shared secret)
3. Устанавливает `JwtPrincipal(userId)` в `SecurityContext`

## Асинхронная загрузка логов

```
POST /logs/ingest
  │
  ├─ Проверка: projectId совпадает с ключом
  ├─ Создание IngestionStatus (PENDING) в PostgreSQL
  ├─ Возврат { ingestionId } немедленно (200 OK)
  │
  └─ @Async("logIngestionExecutor") ─────────────────────────────────┐
       │                                                              │
       ├─ Статус → PROCESSING                                         │
       ├─ Создание LogDocument[] (timestamp → epoch millis)          │
       ├─ ElasticsearchLogRepository.bulkIndex()                     │
       ├─ Статус → COMPLETED / FAILED                                │
       └───────────────────────────────────────────────────────────────
```

Пул потоков `logIngestionExecutor`: corePoolSize=4, maxPoolSize=8, queueCapacity=500.

Статус загрузки можно опросить: `GET /logs/ingest/{ingestionId}`

## Хранение логов в Elasticsearch

Индекс `logs`, документ `LogDocument`:

| Поле | ES тип | Описание |
|---|---|---|
| `id` | keyword | UUID документа |
| `project_id` | keyword | UUID проекта |
| `ingestion_id` | keyword | UUID пакета загрузки |
| `level` | keyword | TRACE / DEBUG / INFO / WARN / ERROR |
| `message` | text | Текст лога (анализируется для full-text поиска) |
| `timestamp` | date (epoch_millis) | Время события в миллисекундах UTC |

Индекс создаётся автоматически в `@PostConstruct initIndex()`.

## Поиск логов

`POST /logs/search` → `ElasticsearchLogRepository.search()`:
- `term` по `project_id`
- `range` по `timestamp` (`from`..`to`)
- `terms` по `level` (если указан)
- `match` по `message` (если указан)
- `range lt cursor` (cursor-based пагинация)
- Сортировка по `timestamp DESC`

**Cursor-based пагинация:** запрашивается `size + 1` документов. Если пришло больше `size` — в ответе есть `nextCursor` (timestamp последнего из `size` документов).

## Таймлайн

`POST /logs/timeline` → `ElasticsearchLogRepository.timeline()`:

Elasticsearch `date_histogram` агрегация с фиксированным интервалом:

| Диапазон | Интервал |
|---|---|
| ≤ 1 час | 60 сек |
| ≤ 24 часа | 3600 сек (1 ч) |
| ≤ 7 дней | 86400 сек (1 день) |
| > 7 дней | 604800 сек (1 неделя) |

Каждый бакет содержит `totalCount`, `errorCount` (filter sub-aggregation по `level=ERROR`), `warnCount` (filter sub-aggregation по `level=WARN`).

## API эндпоинты

### Загрузка логов (аутентификация: API ключ)

- `POST /logs/ingest` — пакетная загрузка логов, возвращает `ingestionId`
- `GET /logs/ingest/{ingestionId}` — статус загрузки (pending / processing / completed / failed)

### Работа с логами (аутентификация: JWT cookie)

- `POST /logs/search` — поиск с фильтрацией, поддержка пагинации
- `POST /logs/timeline` — агрегированный таймлайн

## База данных — таблица ingestion_status

```sql
CREATE TABLE ingestion_status (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    status      VARCHAR(20) NOT NULL,   -- PENDING | PROCESSING | COMPLETED | FAILED
    accepted    INT NOT NULL DEFAULT 0,
    processed   INT NOT NULL DEFAULT 0,
    failed      INT NOT NULL DEFAULT 0,
    started_at  TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error       TEXT
);
```

Миграция: `db/changelog/001-create-ingestion-status-table.yaml` (Liquibase).

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SERVER_PORT` | `8081` | Порт сервиса |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/logboard` | JDBC URL (общая БД с Core) |
| `DATABASE_USERNAME` | `logboard` | Пользователь БД |
| `DATABASE_PASSWORD` | `logboard` | Пароль БД |
| `ELASTICSEARCH_URIS` | `http://localhost:9200` | Адрес Elasticsearch |
| `ELASTICSEARCH_USERNAME` | _(пусто)_ | Пользователь ES (если включена аутентификация) |
| `ELASTICSEARCH_PASSWORD` | _(пусто)_ | Пароль ES |
| `API_KEY_HMAC_SECRET` | `myDefaultHmacSecret...` | HMAC-SHA256 секрет (должен совпадать с Core) |
| `JWT_SECRET` | `mySecretKey...` | JWT секрет (должен совпадать с Core) |

## Тестирование

- **Unit тесты** (`src/test/`) — Kotest DescribeSpec + Mockito-Kotlin, покрытие ≥ 65%
- **Интеграционные тесты** (`src/integration-test/`) — Testcontainers (PostgreSQL 15 + Elasticsearch 8.12)

```bash
# Запуск unit тестов
./gradlew test

# Запуск интеграционных тестов (требуется Docker)
./gradlew integrationTest
```
