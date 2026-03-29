# LogBoard

Платформа для сбора и анализа логов. Mono-repo с двумя сервисами:

- **core** — управление пользователями, проектами, авторизация (Spring Boot + PostgreSQL)
- **log** — приём, хранение и поиск логов (Spring Boot + ClickHouse + Elasticsearch)

## Требования

- Docker и Docker Compose
- JDK 21 (для локальной разработки без Docker)

## Быстрый старт (локальная разработка и тестирование)

```bash
git clone <repo-url>
cd LogBoard
docker compose up -d
```

`docker-compose.override.yml` содержит тестовые значения переменных окружения (тестовые пароли, dev JWT-секрет) и автоматически подхватывается Docker Compose. Этот файл используется **только для локальной разработки и тестирования** — на production он не применяется.

Сервисы будут доступны:
- Core API: http://localhost:8080
- PostgreSQL: localhost:5432

## Запуск на сервере (production)

1. Скопировать `env.example` в `.env` и заполнить настоящими значениями:

```bash
cp env.example .env
nano .env
```

2. Сгенерировать надёжные секреты:

```bash
# Пароль PostgreSQL
openssl rand -base64 32

# JWT-секрет (для HS512, минимум 64 символа)
openssl rand -base64 64
```

3. Запустить **без** override-файла, чтобы использовались значения из `.env`:

```bash
docker compose -f docker-compose.yml up -d
```

## Запуск тестов

```bash
# Unit-тесты
cd core && ./gradlew test

# Интеграционные тесты
cd core && ./gradlew integrationTest
```

## CI/CD

GitHub Actions автоматически запускает сборку и тесты при push в `main`/`develop` и при PR в `main`. При push в `main` после прохождения всех тестов происходит автоматический деплой на сервер.

## Структура проекта

```
LogBoard/
├── core/                # Core-сервис (Spring Boot + PostgreSQL)
├── log/                 # Log-сервис (Spring Boot + ClickHouse + Elasticsearch)
├── docs/                # Документация и OpenAPI-спецификация
├── docker-compose.yml           # Основная конфигурация (переменные окружения)
├── docker-compose.override.yml  # Тестовые значения переменных (только для разработки и тестов)
└── env.example                  # Шаблон .env для production
```
