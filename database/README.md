# Database

Настройки подключения находятся в `src/main/resources/application.properties` и используют переменные окружения `DB_URL`, `DB_USERNAME` и `DB_PASSWORD`.

Папка `migrations` зарезервирована под версионируемые миграции. Сейчас схема обновляется Hibernate через `spring.jpa.hibernate.ddl-auto=update`, поэтому миграции не добавлялись искусственно.
