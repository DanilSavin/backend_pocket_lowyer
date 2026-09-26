# Карманный юрист

Сервис для первичной проверки договоров. Пользователь загружает файл в React-интерфейсе, а Java API передаёт его в GigaChat и выдаёт краткое резюме, сильные стороны и условия, требующие внимания.

> Результат носит информационный характер и не заменяет консультацию юриста.

## Запуск

Требуются JDK 17+ и Node.js 20+.

```powershell
cd backend
# One time: copy .env.example to .env, then set GIGACHAT_AUTH_KEY in .env
mvn spring-boot:run
```

В другом окне:

```powershell
cd frontend
npm install
npm run dev
```

Откройте адрес, показанный Vite (обычно `http://localhost:5173`). Запросы `/api` проксируются на Java-приложение по порту 8080.

## Поддерживаемые форматы

`PDF`, `DOCX`, `TXT`. Ограничение размера — 10 МБ.

## API

`POST /api/documents/analyze` — multipart-поле `file`; возвращает имя файла, краткое резюме, сильные стороны, общий уровень риска и список замечаний.

## GigaChat

1. Создайте проект GigaChat API в личном кабинете Сбера и скопируйте **Authorization key**.
2. Скопируйте `backend/.env.example` в `backend/.env` и вставьте новый ключ в `GIGACHAT_AUTH_KEY`. Файл `.env` автоматически загружается при запуске и исключён из Git.
3. Backend получает OAuth-токен, отправляет исходный файл в приватное хранилище GigaChat, запрашивает анализ с приложением и после ответа пытается удалить файл из хранилища.

По умолчанию проект использует `GigaChat-3-Ultra` для scope `GIGACHAT_API_PERS` — эта модель доступна физическим лицам в freemium-режиме. При необходимости можно сменить модель через переменную `GIGACHAT_MODEL`, например на `GigaChat-2-Pro` для сложного анализа и суммаризации.

## Деплой на VPS

На сервере должны быть установлены Docker Engine и Docker Compose, а домен должен иметь A-запись на публичный IP сервера.

1. Скопируйте репозиторий на сервер.
2. Создайте файл `secrets/gigachat_auth_key.txt` и поместите в него только новый Authorization Key GigaChat. Этот файл не коммитится.
3. Укажите домен и запустите production-конфигурацию:

```bash
export DOMAIN=example.ru
docker compose -f compose.yaml -f compose.production.yaml up -d --build
```

Контейнер `caddy` автоматически выпустит и обновит HTTPS-сертификат. Откройте в firewall только порты 22, 80 и 443; порт Java-приложения наружу не публикуется. Для просмотра логов используйте `docker compose logs -f backend`.

При обновлении кода на сервере выполните ту же команду с `--build`.

По документации GigaChat, токен действует 30 минут, а для личного доступа используется scope `GIGACHAT_API_PERS`. API поддерживает добавление текстовых документов в хранилище и их передачу модели через `attachments`. [Авторизация GigaChat](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api), [работа с файлами](https://developers.sber.ru/docs/ru/gigachat/guides/working-with-files).
