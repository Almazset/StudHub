# База данных StudHub

В профилях `dev` и `prod` Flyway применяет версионируемые миграции PostgreSQL, после чего Hibernate проверяет соответствие схемы моделям. Настройки подключения задаются через `DB_URL`, `DB_USERNAME` и `DB_PASSWORD`. Для чистой установки выполните [локальный запуск](../README.md#локальный-запуск-с-postgresql) или [запуск на сервере](../README.md#запуск-на-сервере). Flyway создаст таблицы версией 11 и запишет результат в `public.flyway_schema_history`.

## Переход существующей базы на Flyway

Эта процедура предназначена для базы StudHub, созданной до этапа 16. В примерах используются PowerShell, контейнер `studhub-db`, база `studhub` и роль `studhub`. Подставьте свои значения, если настройки отличаются.

1. Остановите StudHub и другие процессы, которые записывают данные в базу. Оставьте PostgreSQL запущенным. Создайте копию базы и действующего ключа 2FA по [инструкции резервного копирования](RECOVERY.md#1-создать-копию).

2. Доведите схему до версии 11. Если с последнего обновления пропущены ручные миграции, примените только пропущенные файлы из [архива V001–V011](migrations/README.md) в порядке версий. Например, для базы этапа 14 примените миграцию новостей:

   ```powershell
   Get-Content -LiteralPath .\database\migrations\V011__news_editorial_workflow.sql -Raw |
       docker exec -i studhub-db psql -X -w -v ON_ERROR_STOP=1 -U studhub -d studhub
   ```

3. Проверьте структуру базы:

   ```powershell
   .\database\scripts\Test-FlywayBaseline.ps1 -Container studhub-db -Database studhub -User studhub
   ```

   Скрипт сравнивает таблицы, типы и свойства столбцов, ограничения и индексы с новой схемой. Он читает метаданные целевой базы и создаёт временную эталонную базу на том же PostgreSQL. Для этого роли нужны права `CREATEDB`. При расхождениях устраните их на копии и повторите проверку перед регистрацией базы.

4. В текущей сессии задайте `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` для той же базы согласно [инструкции запуска](../README.md#локальный-запуск-с-postgresql). После успешной проверки зарегистрируйте существующую схему в Flyway:

   ```powershell
   $env:FLYWAY_URL = $env:DB_URL
   $env:FLYWAY_USER = $env:DB_USERNAME
   $env:FLYWAY_PASSWORD = $env:DB_PASSWORD
   try {
       mvn.cmd -Penroll-database flyway:baseline
       if ($LASTEXITCODE -ne 0) { throw 'Регистрация базы во Flyway не завершилась.' }
   } finally {
       Remove-Item Env:FLYWAY_URL, Env:FLYWAY_USER, Env:FLYWAY_PASSWORD -ErrorAction SilentlyContinue
   }
   ```

   Команда фиксирует версию 11 в истории Flyway. После этого запустите StudHub обычным способом. При следующих запусках будут применяться только новые версии миграций.

## Резервные копии

[Инструкция копирования и восстановления](RECOVERY.md). Скрипты находятся в `scripts`, проверки отказов — в `tests`. Дампы и ключи хранятся вне репозитория.

[Результат проверки восстановления 16.11.2026](RECOVERY_CHECK.md).
