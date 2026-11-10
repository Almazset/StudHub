from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.section import WD_SECTION

OUTPUT = r"C:\Users\Almaz\Documents\Codex\2026-10-16\referenced-chatgpt-conversation-this-is-an\work\studhub\studhub\docs\reference\StudHub_Explanatory_Note_RU.docx"
BLUE = "1769E0"
NAVY = "17345F"
PALE = "EAF2FF"
INK = "202B3C"
MUTED = "66758A"

doc = Document()
sec = doc.sections[0]
sec.top_margin = Inches(.72)
sec.bottom_margin = Inches(.7)
sec.left_margin = Inches(.82)
sec.right_margin = Inches(.82)

styles = doc.styles
styles["Normal"].font.name = "Arial"
styles["Normal"]._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
styles["Normal"].font.size = Pt(10.5)
styles["Normal"].font.color.rgb = RGBColor.from_string(INK)
styles["Normal"].paragraph_format.space_after = Pt(6)
for name, size in (("Title", 30), ("Heading 1", 20), ("Heading 2", 14), ("Heading 3", 11)):
    style = styles[name]
    style.font.name = "Arial"
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = RGBColor.from_string(BLUE if name != "Title" else NAVY)
    style.paragraph_format.space_before = Pt(12 if name != "Title" else 0)
    style.paragraph_format.space_after = Pt(6)

def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)

def set_cell_text(cell, text, bold=False, color=INK, size=9.2):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(1)
    r = p.add_run(text)
    r.bold = bold
    r.font.name = "Arial"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Arial")
    r.font.size = Pt(size)
    r.font.color.rgb = RGBColor.from_string(color)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER

def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.style = "Table Grid"
    for i, label in enumerate(headers):
        set_cell_text(t.rows[0].cells[i], label, True, "FFFFFF", 9)
        shade(t.rows[0].cells[i], NAVY)
    for idx, row in enumerate(rows):
        cells = t.add_row().cells
        for i, value in enumerate(row):
            set_cell_text(cells[i], value, False, INK, 9)
            if idx % 2 == 1:
                shade(cells[i], "F4F7FB")
    if widths:
        for row in t.rows:
            for i, width in enumerate(widths):
                row.cells[i].width = Inches(width)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return t

def bullet(text):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_after = Pt(3)
    p.add_run(text)
    return p

def code(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(.18)
    p.paragraph_format.right_indent = Inches(.12)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), "F1F5FA")
    pPr.append(shd)
    r = p.add_run(text)
    r.font.name = "Consolas"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
    r.font.size = Pt(8.2)
    r.font.color.rgb = RGBColor.from_string(NAVY)
    return p

# Cover
p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(86)
r = p.add_run("STUDHUB")
r.bold = True
r.font.name = "Arial"
r.font.size = Pt(16)
r.font.color.rgb = RGBColor.from_string(BLUE)
p = doc.add_paragraph(style="Title")
p.paragraph_format.space_before = Pt(16)
p.add_run("Пояснительная записка")
p = doc.add_paragraph()
r = p.add_run("Система управления учебными курсами и заданиями")
r.font.size = Pt(17)
r.font.color.rgb = RGBColor.from_string(MUTED)
doc.add_paragraph("Проектная документация", style="Heading 2")
doc.add_paragraph("Учебный проект по Java Programming")
doc.add_paragraph("Технологический стек: Java 21 · Spring Boot · Thymeleaf · PostgreSQL")
doc.add_paragraph("2026")
doc.add_page_break()

doc.add_heading("Содержание", level=1)
for item in ["1. Введение", "2. Анализ требований", "3. Проектирование системы", "4. Реализация", "5. Результаты тестирования", "6. Заключение"]:
    bullet(item)
doc.add_paragraph("Документ описывает реализованную версию StudHub и её основные технические решения.")
doc.add_page_break()

doc.add_heading("1. Введение", level=1)
doc.add_heading("1.1. Цель проекта", level=2)
doc.add_paragraph("Цель StudHub — создать веб-приложение, которое помогает студенту хранить учебные курсы, планировать задания и видеть ближайшие сроки в одном кабинете. Для преподавателя и администратора предусмотрено управление учебными данными и учётными записями в рамках назначенных ролей.")
doc.add_heading("1.2. Актуальность", level=2)
doc.add_paragraph("Учебные материалы и дедлайны часто распределены по разным каналам: системе обучения, почте, заметкам и чатам. Из-за этого студенту сложнее быстро понять, что изучать и что нужно выполнить в первую очередь. StudHub объединяет базовые сведения о курсах и заданиях и показывает ближайший план работ.")
doc.add_heading("1.3. Объект и границы проекта", level=2)
doc.add_paragraph("Объект автоматизации — учёт учебных курсов и индивидуальных заданий. Система включает серверный интерфейс, REST API, аутентификацию и ролевую авторизацию. Чат-помощник интерфейса реализован как FAQ с заранее заданными ответами и не является генеративным AI-сервисом.")

doc.add_heading("2. Анализ требований", level=1)
doc.add_heading("2.1. Функциональные требования", level=2)
table(["ID", "Требование", "Реализация"], [
    ("FR-01", "Регистрация и вход по email и паролю", "Spring Security form login; серверная регистрация"),
    ("FR-02", "Просмотр и ведение курсов", "Страницы и REST CRUD для курсов"),
    ("FR-03", "Создание и контроль заданий", "Задание связано с курсом; дата срока и статус выполнения"),
    ("FR-04", "Панель учебного прогресса", "Счётчики, процент выполнения, ближайшие сроки"),
    ("FR-05", "Ролевое администрирование пользователей", "USER и ADMIN; управление пользователями ограничено ADMIN"),
    ("FR-06", "Подсказки по функциям", "Клиентский FAQ-помощник отвечает на типовые вопросы")
], [0.55, 2.6, 3.7])
doc.add_heading("2.2. Нефункциональные требования", level=2)
table(["Область", "Требование / характеристика"], [
    ("Безопасность", "Защищённые маршруты; BCrypt для кодирования паролей; ролевой контроль доступа."),
    ("Надёжность данных", "JPA-сущности и PostgreSQL; обязательные поля и ограничения валидации."),
    ("Удобство", "Адаптивные страницы, фильтры, поиск, светлая и тёмная темы, доступные подписи."),
    ("Сопровождаемость", "Разделение на web/API, service и repository слои; локализуемые сообщения."),
    ("Совместимость", "Java 21, Spring Boot 3.5, браузерный HTML/CSS/JavaScript.")
], [1.45, 5.4])

doc.add_heading("3. Проектирование системы", level=1)
doc.add_heading("3.1. ER-модель", level=2)
doc.add_paragraph("Основные сущности — пользователь, курс и задание. У курса может быть много заданий (1:N). Каждое задание относится ровно к одному курсу; оно может быть назначено пользователю (N:1, связь необязательная). Роль пользователя хранится как перечисление USER или ADMIN.")
table(["Сущность", "Ключевые поля", "Связи"], [
    ("users", "id (PK), full_name, email (unique), password, role", "Один пользователь может быть назначен на несколько заданий."),
    ("courses", "id (PK), title, description, teacher_name", "Один курс содержит много заданий."),
    ("assignments", "id (PK), title, description, due_date, completed, course_id (FK), student_id (FK nullable)", "Обязательная связь с courses; необязательная связь с users.")
], [1.1, 3.2, 2.55])
doc.add_paragraph("Кардинальности: courses 1 — N assignments; users 1 — 0..N assignments. Таблица users содержит уникальный email.")
doc.add_heading("3.2. Архитектура", level=2)
table(["Уровень", "Назначение", "Основные компоненты"], [
    ("Presentation", "HTML-страницы и REST-вход", "Spring MVC controllers, Thymeleaf templates"),
    ("Application", "Правила предметной области и транзакции", "CourseService, AssignmentService, RegistrationService"),
    ("Persistence", "Доступ к данным", "Spring Data JPA repositories, Hibernate"),
    ("Database", "Постоянное хранение", "PostgreSQL: users, courses, assignments"),
    ("Security", "Аутентификация и роли", "Spring Security, UserDetailsService, BCrypt")
], [1.2, 2.5, 3.15])
doc.add_paragraph("Поток запроса: Browser / REST client → Controller → Service → Repository → PostgreSQL. Для HTML-ответа контроллер возвращает Thymeleaf-шаблон; для API контроллер сериализует DTO/JSON.")
doc.add_heading("3.3. Основные сценарии", level=2)
bullet("Регистрация: пользователь отправляет данные → выполняется Bean Validation и проверка совпадения паролей → RegistrationService кодирует пароль и сохраняет пользователя с ролью USER → перенаправление на страницу входа.")
bullet("Вход: Spring Security проверяет email и пароль через UserDetailsService и PasswordEncoder → успешная аутентификация открывает личный кабинет.")
bullet("Задания: пользователь создаёт задание для курса → указывает срок → интерфейс отображает его в списке и на панели ближайших сроков.")

doc.add_heading("4. Реализация", level=1)
doc.add_heading("4.1. Технологии", level=2)
table(["Технология", "Назначение"], [
    ("Java 21 / Spring Boot 3.5", "Запуск серверного приложения и внедрение компонентов."),
    ("Spring MVC / Thymeleaf", "Маршрутизация страниц и серверный HTML."),
    ("Spring Security", "Форма входа, сессия и разграничение ролей."),
    ("Spring Data JPA / Hibernate", "ORM и репозитории для сущностей."),
    ("PostgreSQL", "Реляционная база данных."),
    ("Bean Validation", "Проверка входных полей и ограничений модели."),
    ("JUnit 5 / Mockito", "Модульные тесты сервисного слоя."),
    ("Bootstrap / CSS / JavaScript", "Адаптивный интерфейс и взаимодействия клиента.")
], [2.35, 4.5])
doc.add_heading("4.2. Пример серверной валидации", level=2)
code("@NotBlank @Email @Size(max = 120)\nprivate String email;\n\n@NotBlank @Size(min = 8, max = 100)\nprivate String password;")
doc.add_paragraph("Ограничения формы регистрации описаны декларативно и проверяются до вызова сервиса. RegistrationService отвечает за создание пользователя и кодирование пароля.")
doc.add_heading("4.3. Пример ролевой защиты", level=2)
code('.requestMatchers("/api/users/**").hasRole("ADMIN")\n.requestMatchers("/users/**").hasRole("ADMIN")\n.anyRequest().authenticated()')
doc.add_paragraph("Страницы входа и регистрации доступны без сессии. Остальные маршруты требуют аутентификации, а управление пользователями ограничено ролью ADMIN.")
doc.add_heading("4.4. Интерфейс", level=2)
doc.add_paragraph("Пользовательский интерфейс построен на Thymeleaf, Bootstrap и локальных CSS/JavaScript-файлах. Панель показывает число курсов и заданий, процент выполнения и ближайшие сроки. Для мобильного экрана предусмотрена боковая навигация. FAQ-помощник даёт справочные ответы о курсах, заданиях, профиле и входе.")

doc.add_heading("5. Результаты тестирования", level=1)
doc.add_paragraph("В имеющемся отчёте Maven Surefire для RegistrationServiceTest указаны следующие результаты:")
table(["Метрика", "Результат"], [
    ("Всего тестов", "2"), ("Успешно", "2"), ("Ошибки", "0"), ("Падения", "0"), ("Пропуски", "0")
], [2.3, 4.5])
doc.add_paragraph("Проверенные сценарии: регистрация кодирует пароль и сохраняет пользователя; повторный email отклоняется. Отчёт подтверждает только эти unit-тесты сервиса регистрации и не является результатом полного интеграционного или нагрузочного тестирования.")
doc.add_paragraph("Источник результата: target/surefire-reports/TEST-kz.studhub.service.RegistrationServiceTest.xml (отчёт проекта, 16 октября 2026 г.).")

doc.add_heading("6. Заключение", level=1)
doc.add_paragraph("StudHub реализует базовый цикл управления учебным процессом: регистрация и вход, каталог курсов, задания со сроками и статусами, сводная панель и административные функции. Архитектура разделяет веб-слой, бизнес-логику и доступ к данным; Spring Security обеспечивает проверку входа и ролевой контроль.")
doc.add_paragraph("В качестве дальнейшего развития можно добавить расширенные интеграционные тесты с PostgreSQL, восстановление пароля, уведомления о сроках, миграции схемы базы данных через Flyway/Liquibase и более гибкие отчёты по успеваемости.")

# Simple page footer
footer = sec.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
fr = footer.add_run("StudHub  |  Пояснительная записка")
fr.font.name = "Arial"
fr.font.size = Pt(8)
fr.font.color.rgb = RGBColor.from_string(MUTED)

doc.save(OUTPUT)
print(OUTPUT)
