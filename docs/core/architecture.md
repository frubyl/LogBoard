# Core Service

Core Service отвечает за всю пользовательскую функциональность и управление проектами.

## Обязанности

- Аутентификация и авторизация пользователей
- Управление профилями пользователей
- Создание и управление проектами
- Управление участниками проекта (роли OWNER / ADMIN / READER)
- Генерация и управление API ключами

## Архитектура

Core Service следует традиционной слоистой архитектуре:

```
┌─────────────────┐
│   Представление │ ← REST контроллеры
├─────────────────┤
│     Сервисы     │ ← Бизнес-логика
├─────────────────┤
│   Репозитории   │ ← Доступ к данным
├─────────────────┤
│  Интеграция     │ ← Внешние сервисы
└─────────────────┘
```

## API эндпоинты

### Аутентификация
- `POST /register` — Регистрация пользователя
- `POST /login` — Вход пользователя, токены в HTTP-only cookies
- `POST /refresh` — Обновление access токена
- `POST /logout` — Выход, очистка cookies

### Проекты
- `POST /projects` — Создать проект (создатель становится OWNER)
- `GET /projects` — Список проектов пользователя
- `DELETE /projects/{id}` — Удалить проект (только OWNER)

### Участники проекта
- `POST /projects/{id}/members` — Добавить участника (OWNER добавляет ADMIN/READER, ADMIN добавляет только READER)
- `DELETE /projects/{id}/members/{userId}` — Удалить участника (OWNER удаляет ADMIN/READER, ADMIN удаляет только READER)
- `PATCH /projects/{id}/members/{userId}` — Изменить роль участника (только OWNER; нельзя изменить роль OWNER и назначить роль OWNER)

### API ключи
- `POST /api-keys` — Создать API ключ (только OWNER/ADMIN)
- `GET /api-keys?projectId=` — Список API ключей проекта (только OWNER/ADMIN)
- `DELETE /api-keys/{keyId}` — Отозвать API ключ (только OWNER/ADMIN)

## Публикация событий в Kafka

После успешного создания или отзыва API ключа `ApiKeyService` публикует событие в топик `logboard.api-keys`.

| Операция | Тип события | Поля события |
|---|---|---|
| `POST /api-keys` | `CREATED` | `keyId`, `projectId`, `keyHash`, `expiresAt` |
| `DELETE /api-keys/{keyId}` | `REVOKED` | `keyId`, `keyHash` |

`keyHash` включён в событие `REVOKED`, чтобы Log Service мог вытеснить запись из Caffeine-кэша без дополнительного запроса к базе данных.

Класс события: `com.github.logboard.core.event.ApiKeyEvent`  
Топик: `KafkaTopics.API_KEYS = "logboard.api-keys"`
