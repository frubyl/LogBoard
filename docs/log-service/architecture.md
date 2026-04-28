# Log Service

Log Service отвечает за приём, хранение, поиск и аналитику логов.

## Обязанности

- Потребление Kafka-событий об API ключах от Core Service
- Хранение локальных реплик API ключей для автономной валидации
- Кэширование API ключей в памяти (Caffeine) для минимальной латентности на горячем пути

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│   Kafka Consumer                                        │
│   ApiKeyEventConsumer                                   │
│   (logboard.api-keys → CREATED / REVOKED)               │
├─────────────────────────────────────────────────────────┤
│   Сервис кэша                                           │
│   LocalApiKeyCacheService                               │
│   (@Cacheable / @CachePut / @CacheEvict)                │
├─────────────────────────────────────────────────────────┤
│   Репозиторий                                           │
│   LocalApiKeyRepository                                 │
│   (findByKeyHash)                                       │
├─────────────────────────────────────────────────────────┤
│   Хранилища                                             │
│   Caffeine (in-process) · PostgreSQL (local_api_keys)   │
└─────────────────────────────────────────────────────────┘
```

## Поток событий

### Событие CREATED

```
Core Service
  └─ kafkaTemplate.send("logboard.api-keys", ApiKeyEvent(CREATED, keyId, projectId, keyHash, expiresAt))
       │
       ▼
ApiKeyEventConsumer.handle()
  ├─ localApiKeyRepository.save(LocalApiKey(id, projectId, keyHash, expiresAt))
  └─ localApiKeyCacheService.put(entity)          ← @CachePut("apiKeys", key = keyHash)
```

### Событие REVOKED

```
Core Service
  └─ kafkaTemplate.send("logboard.api-keys", ApiKeyEvent(REVOKED, keyId, keyHash))
       │
       ▼
ApiKeyEventConsumer.handle()
  ├─ localApiKeyCacheService.evict(keyHash)        ← @CacheEvict("apiKeys", key = keyHash)
  └─ localApiKeyRepository.deleteById(keyId)
```

## Caffeine-кэш

Кэш `apiKeys` управляется через Spring Cache абстракцию:

| Метод | Аннотация | Поведение |
|---|---|---|
| `findByKeyHash(keyHash)` | `@Cacheable` | Возвращает из кэша, при промахе — из PostgreSQL |
| `put(entity)` | `@CachePut` | Заносит запись в кэш (вызывается при CREATED) |
| `evict(keyHash)` | `@CacheEvict` | Удаляет запись из кэша (вызывается при REVOKED) |

Конфигурация (CacheConfig):
- `maximumSize = 50 000` записей
- `expireAfterWrite = 24 часа` — страховочный TTL на случай пропущенного REVOKED-события

PostgreSQL получает нагрузку только от Kafka-консьюмера (редкие записи), не от валидации на горячем пути.

## База данных — таблица local_api_keys

```sql
CREATE TABLE local_api_keys (
    id          UUID PRIMARY KEY,
    project_id  UUID NOT NULL,
    key_hash    VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP
);
CREATE INDEX idx_local_api_keys_key_hash ON local_api_keys (key_hash);
```

Миграция: `db/changelog/001-create-local-api-keys.yaml` (Liquibase).

## Переменные окружения

| Переменная | Описание |
|---|---|
| `SERVER_PORT` | Порт сервиса (по умолчанию `8081`) |
| `DATABASE_URL` | JDBC URL базы данных Log Service |
| `DATABASE_USERNAME` | Пользователь БД |
| `DATABASE_PASSWORD` | Пароль БД |
| `KAFKA_BOOTSTRAP_SERVERS` | Адрес Kafka-брокера (по умолчанию `localhost:9092`) |

## Тестирование

- **Unit тесты** (`src/test/`) — Kotest DescribeSpec + Mockito-Kotlin
  - `LocalApiKeyCacheServiceTest` — проверяет кэширование, `put()` и `evict()` через Spring test context с `CaffeineCacheManager`

```bash
./gradlew test
```
