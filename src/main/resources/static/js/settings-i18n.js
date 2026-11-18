(() => {
  'use strict';
  const languages = ['ru', 'en', 'kk'];
  const labels = {
    settings: ['Настройки', 'Settings', 'Баптаулар'],
    subtitle: ['Сделайте StudHub удобным для себя.', 'Make StudHub work your way.', 'StudHub жүйесін өзіңізге ыңғайлаңыз.'],
    appearance: ['Внешний вид', 'Appearance', 'Сыртқы көрініс'],
    appearanceHint: ['Тема, размер текста и язык интерфейса.', 'Theme, text size and interface language.', 'Тақырып, мәтін өлшемі және интерфейс тілі.'],
    profile: ['Профиль', 'Profile', 'Профиль'],
    profileHint: ['Ваше имя, фото и данные для входа.', 'Your name, photo and sign-in details.', 'Атыңыз, фотосуретіңіз және кіру деректері.'],
    privacy: ['Приватность и безопасность', 'Privacy & security', 'Құпиялық және қауіпсіздік'],
    privacyHint: ['Видимость профиля, защита входа и ваши устройства.', 'Profile visibility, sign-in protection and your devices.', 'Профильдің көрінуі, кіруді қорғау және құрылғыларыңыз.'],
    about: ['О сайте', 'About StudHub', 'Сайт туралы'],
    aboutHint: ['Ответы на вопросы, поддержка и информация о сервисе.', 'Answers, support and service information.', 'Сұрақтарға жауаптар, қолдау және сервис туралы ақпарат.'],
    theme: ['Тема оформления', 'Color theme', 'Көрініс тақырыбы'],
    light: ['Светлая', 'Light', 'Ашық'], dark: ['Тёмная', 'Dark', 'Қараңғы'],
    fontSize: ['Размер шрифта', 'Text size', 'Қаріп өлшемі'],
    small: ['Меньше', 'Smaller', 'Кішірек'], normal: ['Обычный', 'Default', 'Қалыпты'], large: ['Крупнее', 'Larger', 'Үлкенірек'],
    language: ['Язык интерфейса', 'Interface language', 'Интерфейс тілі'],
    liveHint: ['Изменения применяются сразу и сохраняются автоматически.', 'Changes apply immediately and save automatically.', 'Өзгерістер бірден қолданылып, автоматты түрде сақталады.'],
    sample: ['Так будет выглядеть текст', 'This is how your text will look', 'Мәтін осылай көрінеді'],
    sampleBody: ['Курсы, задания и ближайшие сроки — всё под рукой.', 'Courses, tasks and upcoming deadlines, all within reach.', 'Курстар, тапсырмалар және жақын мерзімдер — барлығы қолжетімді.'],
    saved: ['Сохранено', 'Saved', 'Сақталды'], saving: ['Сохраняем…', 'Saving…', 'Сақталуда…'],
    save: ['Сохранить изменения', 'Save changes', 'Өзгерістерді сақтау'],
    photo: ['Фото профиля', 'Profile photo', 'Профиль фотосуреті'],
    photoHint: ['JPG или PNG, до 2 МБ. Фото будет обрезано до квадрата.', 'JPG or PNG, up to 2 MB. Your photo will be cropped to a square.', 'JPG немесе PNG, 2 МБ дейін. Фото шаршы етіп қиылады.'],
    upload: ['Загрузить фото', 'Upload photo', 'Фото жүктеу'], removePhoto: ['Удалить фото', 'Remove photo', 'Фотосуретті жою'],
    name: ['Имя', 'Name', 'Аты'], nickname: ['Никнейм', 'Nickname', 'Лақап ат'], nicknameHint: ['Например, donk. Если оставить поле пустым, будет показано имя. До 32 символов.', 'For example, donk. Leave blank to display your name. Up to 32 characters.', 'Мысалы, donk. Бос қалдырсаңыз, атыңыз көрсетіледі. 32 таңбаға дейін.'], email: ['Email', 'Email', 'Email'],
    emailHint: ['При смене email подтвердите пароль. Затем потребуется войти снова.', 'Confirm your password to change your email, then sign in again.', 'Email өзгерту үшін құпиясөзді растаңыз. Содан кейін қайта кіріңіз.'],
    currentPassword: ['Текущий пароль', 'Current password', 'Қазіргі құпиясөз'],
    newPassword: ['Новый пароль', 'New password', 'Жаңа құпиясөз'],
    confirmPassword: ['Повторите новый пароль', 'Confirm new password', 'Жаңа құпиясөзді қайталаңыз'],
    changePassword: ['Смена пароля', 'Change password', 'Құпиясөзді өзгерту'],
    passwordHint: ['Не менее 8 символов. После смены пароля остальные сеансы завершатся.', 'At least 8 characters. Changing your password signs out other sessions.', 'Кемінде 8 таңба. Құпиясөзді өзгерткенде басқа сеанстар аяқталады.'],
    updatePassword: ['Изменить пароль', 'Update password', 'Құпиясөзді жаңарту'],
    visibility: ['Кто видит мой профиль', 'Who can see my profile', 'Профилімді кім көре алады'],
    private: ['Только я', 'Only me', 'Тек мен'], members: ['Участники StudHub', 'StudHub members', 'StudHub қатысушылары'],
    visibilityHint: ['Эта настройка определяет доступ к вашему имени и фото. Email и данные для входа другим участникам не показываются.', 'This controls access to your name and photo. Your email and sign-in details are not shown to other members.', 'Бұл баптау атыңыз бен фотоңыздың көрінуін анықтайды. Email және кіру деректері басқа қатысушыларға көрсетілмейді.'],
    twoFactor: ['Двухфакторная защита', 'Two-factor authentication', 'Екі факторлы қорғаныс'],
    twoFactorHint: ['При входе понадобится пароль и код из приложения-аутентификатора.', 'Sign in with your password and a code from an authenticator app.', 'Кіру үшін құпиясөз және аутентификатор қолданбасындағы код қажет.'],
    enabled: ['Включена', 'Enabled', 'Қосылған'], disabled: ['Не включена', 'Not enabled', 'Қосылмаған'],
    enable: ['Настроить защиту', 'Set up protection', 'Қорғанысты баптау'], disable: ['Отключить защиту', 'Disable protection', 'Қорғанысты өшіру'],
    authenticate: ['Подтвердить пароль', 'Confirm password', 'Құпиясөзді растау'],
    otp: ['Код из аутентификатора или резервный код', 'Authenticator or recovery code', 'Аутентификатор немесе резервтік код'],
    sixDigits: ['Шестизначный код', 'Six-digit code', 'Алты таңбалы код'],
    setupHint: ['Добавьте аккаунт вручную в приложении-аутентификаторе. Выберите ключ по времени (TOTP), введите email и этот секретный ключ, затем подтвердите код.', 'Add an account manually in your authenticator app. Choose a time-based key (TOTP), enter your email and this secret key, then confirm the code.', 'Аутентификаторға аккаунтты қолмен қосыңыз. Уақытқа негізделген кілтті (TOTP) таңдап, email мен осы құпия кілтті енгізіңіз. Содан кейін кодты растаңыз.'],
    secret: ['Секретный ключ', 'Secret key', 'Құпия кілт'],
    confirmEnable: ['Подтвердить и включить', 'Confirm and enable', 'Растап қосу'],
    cancel: ['Отмена', 'Cancel', 'Бас тарту'],
    recovery: ['Сохраните резервные коды', 'Save your recovery codes', 'Резервтік кодтарды сақтаңыз'],
    recoveryHint: ['Каждый код работает один раз. Они помогут войти при потере телефона. После закрытия окна коды больше не показываются.', 'Each code works once. Use them if you lose your phone. Codes will not be shown again after you close this window.', 'Әр код бір рет қолданылады. Телефон жоғалса, олар кіруге көмектеседі. Терезе жабылғаннан кейін кодтар қайта көрсетілмейді.'],
    downloadCodes: ['Скачать коды', 'Download codes', 'Кодтарды жүктеп алу'], done: ['Коды сохранены', 'I saved my codes', 'Кодтарды сақтадым'],
    sessions: ['Активные сеансы', 'Active sessions', 'Белсенді сеанстар'],
    sessionsHint: ['Устройства, на которых выполнен вход в ваш аккаунт.', 'Devices currently signed in to your account.', 'Аккаунтыңызға кірген құрылғылар.'],
    currentSession: ['Этот сеанс', 'This session', 'Осы сеанс'],
    lastActive: ['Последняя активность', 'Last active', 'Соңғы белсенділік'],
    revoke: ['Завершить', 'Sign out', 'Аяқтау'], revokeOthers: ['Завершить остальные сеансы', 'Sign out other sessions', 'Басқа сеанстарды аяқтау'],
    noOtherSessions: ['Других активных сеансов нет.', 'No other active sessions.', 'Басқа белсенді сеанстар жоқ.'],
    faq: ['Помощь и частые вопросы', 'Help & FAQ', 'Көмек және жиі қойылатын сұрақтар'],
    faqCourseQ: ['Как добавить курс и задание?', 'How do I add a course and an assignment?', 'Курс пен тапсырманы қалай қосамын?'],
    faqCourseA: ['Откройте «Курсы» → «Новый курс». Затем откройте курс и нажмите «Новое задание»: курс уже будет выбран.', 'Open Courses → New course. Then open your course and select New assignment; the course is already selected.', '«Курстар» → «Жаңа курс» бөлімін ашыңыз. Содан кейін курсты ашып, «Жаңа тапсырма» батырмасын басыңыз: курс автоматты түрде таңдалады.'],
    faqSaveQ: ['Где сохраняются мои изменения?', 'Where are my changes saved?', 'Өзгерістерім қайда сақталады?'],
    faqSaveA: ['В приложении данные сохраняются в вашем аккаунте. В предпросмотре используются отдельные демонстрационные данные этого браузера.', 'The application saves data to your account. The preview uses separate demonstration data in this browser.', 'Қолданбада деректер аккаунтыңызда сақталады. Алдын ала қарау осы браузерде бөлек демонстрациялық деректерді пайдаланады.'],
    faqFactorQ: ['Что делать при потере аутентификатора?', 'What if I lose my authenticator?', 'Аутентификатор жоғалса не істеймін?'],
    faqFactorA: ['Войдите с одним из сохранённых резервных кодов. Каждый код можно использовать только один раз. Если кодов нет, обратитесь к администратору сервиса.', 'Sign in with a saved recovery code. Each code can only be used once. If you have no codes, contact your service administrator.', 'Сақталған резервтік кодтың бірімен кіріңіз. Әр код бір рет қолданылады. Кодтар болмаса, сервис әкімшісіне хабарласыңыз.'],
    support: ['Связаться с поддержкой', 'Contact support', 'Қолдау қызметіне хабарласу'],
    supportHint: ['Опишите проблему. Обращение появится у администратора StudHub. Не указывайте пароли и секретные коды.', 'Describe your issue. Your request will appear in the StudHub administrator inbox. Do not include passwords or secret codes.', 'Мәселені сипаттаңыз. Өтініш StudHub әкімшісінің тізіміне түседі. Құпиясөздер мен құпия кодтарды жазбаңыз.'],
    subject: ['Тема обращения', 'Subject', 'Өтініш тақырыбы'], message: ['Сообщение', 'Message', 'Хабарлама'],
    send: ['Отправить обращение', 'Submit request', 'Өтініш жіберу'],
    myRequests: ['Мои обращения', 'My requests', 'Менің өтініштерім'],
    noRequests: ['Обращений пока нет.', 'No requests yet.', 'Әзірге өтініштер жоқ.'],
    open: ['Открыто', 'Open', 'Ашық'], closed: ['Закрыто', 'Closed', 'Жабық'],
    adminInbox: ['Обращения пользователей', 'Support inbox', 'Қатысушылардың өтініштері'],
    closeRequest: ['Закрыть обращение', 'Close request', 'Өтінішті жабу'],
    version: ['Версия', 'Version', 'Нұсқа'],
    terms: ['Условия использования', 'Terms of use', 'Пайдалану шарттары'],
    termsIntro: ['StudHub — учебный сервис для организации курсов и заданий.', 'StudHub is an educational service for organizing courses and assignments.', 'StudHub — курстар мен тапсырмаларды ұйымдастыруға арналған оқу сервисі.'],
    termsContent: ['Добавляйте только материалы, которыми вы вправе делиться. Уважайте других участников и не размещайте чужие персональные данные без согласия.', 'Only add materials you have permission to share. Respect other members and do not publish personal information about others without permission.', 'Тек бөлісуге құқығыңыз бар материалдарды қосыңыз. Басқа қатысушыларды құрметтеңіз және олардың келісімінсіз жеке деректерін жарияламаңыз.'],
    termsSecurity: ['Храните пароль и резервные коды в безопасном месте. О проблемах с доступом сообщайте администратору. Доступ к управлению пользователями и обращениям имеет администратор.', 'Keep your password and recovery codes safe. Report access problems to the administrator. Administrators manage users and support requests.', 'Құпиясөз бен резервтік кодтарды қауіпсіз сақтаңыз. Кіру мәселелері туралы әкімшіге хабарлаңыз. Қатысушылар мен өтініштерді әкімші басқарады.'],
    demo: ['Предпросмотр', 'Preview', 'Алдын ала қарау'],
    demoSecurity: ['Настройки защиты доступны в запущенном приложении StudHub. Предпросмотр не создаёт защищённых сеансов и не меняет пароли.', 'Security settings are available in the running StudHub application. This preview does not create authenticated sessions or change passwords.', 'Қауіпсіздік баптаулары іске қосылған StudHub қолданбасында қолжетімді. Алдын ала қарау қорғалған сеанстар құрмайды және құпиясөздерді өзгертпейді.'],
    demoSupport: ['В предпросмотре обращение сохранится только в этом браузере. В приложении оно будет доступно администратору.', 'Preview requests are saved only in this browser. In the application, requests are delivered to the administrator inbox.', 'Алдын ала қарауда өтініш тек осы браузерде сақталады. Қолданбада ол әкімшіге қолжетімді болады.'],
    loading: ['Загружаем настройки…', 'Loading settings…', 'Баптаулар жүктелуде…'],
    retry: ['Повторить', 'Try again', 'Қайталау'],
    failed: ['Не удалось сохранить изменения. Попробуйте ещё раз.', 'Could not save changes. Please try again.', 'Өзгерістерді сақтау мүмкін болмады. Қайта көріңіз.'],
    reauthenticate: ['Данные изменены. Войдите с новым email.', 'Details updated. Sign in with your new email.', 'Деректер өзгертілді. Жаңа email арқылы кіріңіз.'],
    passwordUpdated: ['Пароль изменён. Остальные сеансы завершены.', 'Password changed. Other sessions have been signed out.', 'Құпиясөз өзгертілді. Басқа сеанстар аяқталды.'],
    ticketCreated: ['Обращение №{id} создано.', 'Request #{id} created.', '№{id} өтініш құрылды.'],
    errorPassword: ['Текущий пароль указан неверно.', 'The current password is incorrect.', 'Қазіргі құпиясөз қате енгізілді.'],
    errorEmail: ['Этот email уже используется.', 'This email is already in use.', 'Бұл email қолданыста.'],
    errorMismatch: ['Новые пароли не совпадают.', 'New passwords do not match.', 'Жаңа құпиясөздер сәйкес келмейді.'],
    errorLength: ['Нужно от 8 символов, не более 72 байт.', 'Use at least 8 characters, up to 72 bytes.', 'Кемінде 8 таңба, 72 байттан аспауы тиіс.'],
    errorSame: ['Новый пароль должен отличаться от текущего.', 'Choose a password different from your current one.', 'Жаңа құпиясөз қазіргі құпиясөзден өзгеше болуы керек.'],
    errorCode: ['Код неверен, уже использован или истёк. После нескольких ошибок подождите 5 минут.', 'The code is invalid, used or expired. After repeated failures, wait 5 minutes.', 'Код қате, қолданылған немесе мерзімі өткен. Бірнеше қатеден кейін 5 минут күтіңіз.'],
    errorPhotoSize: ['Выберите изображение до 2 МБ и 4096 × 4096 пикселей.', 'Choose an image up to 2 MB and 4096 × 4096 pixels.', '2 МБ және 4096 × 4096 пиксельден аспайтын суретті таңдаңыз.'],
    errorPhotoFormat: ['Поддерживаются только изображения JPG и PNG.', 'Only JPG and PNG images are supported.', 'Тек JPG және PNG суреттері қолданылады.'],
    errorExpired: ['Время настройки истекло. Начните подключение заново.', 'Setup has expired. Please start again.', 'Баптау уақыты аяқталды. Қайта бастаңыз.'],
    errorValidation: ['Проверьте заполненные поля.', 'Please check the form fields.', 'Толтырылған өрістерді тексеріңіз.'],
    errorRateLimit: ['Слишком много попыток. Повторите через {seconds} сек.', 'Too many attempts. Try again in {seconds} seconds.', 'Әрекеттер тым көп. {seconds} секундтан кейін қайталаңыз.'],
    errorSupportLimit: ['Можно создать до 5 обращений в сутки.', 'You can submit up to 5 requests per day.', 'Тәулігіне 5 өтінішке дейін жіберуге болады.'],
    sessionExpired: ['Сеанс завершён. Войдите снова.', 'Your session has ended. Please sign in again.', 'Сеанс аяқталды. Қайта кіріңіз.']
  };
  const phrases = [
    ['Личный кабинет', 'Dashboard', 'Жеке кабинет'], ['Курсы', 'Courses', 'Курстар'], ['Задания', 'Assignments', 'Тапсырмалар'],
    ['Пользователи', 'Users', 'Қатысушылар'], ['Учебное пространство', 'Study workspace', 'Оқу кеңістігі'],
    ['Система', 'System', 'Жүйе'], ['Документация API', 'API documentation', 'API құжаттамасы'], ['Выйти', 'Sign out', 'Шығу'],
    ['Поиск', 'Search', 'Іздеу'], ['Уведомления', 'Notifications', 'Хабарландырулар'], ['Все задания', 'All assignments', 'Барлық тапсырмалар'],
    ['Где искать?', 'Where to search?', 'Қайдан іздеу керек?'], ['Название задания или курса', 'Assignment or course name', 'Тапсырма немесе курс атауы'],
    ['В заданиях', 'In assignments', 'Тапсырмалардан'], ['В курсах', 'In courses', 'Курстардан'],
    ['Ближайших дедлайнов нет.', 'No upcoming deadlines.', 'Жақын мерзімдер жоқ.'],
    ['Настройки профиля', 'Profile settings', 'Профиль баптаулары'], ['Сохранить', 'Save', 'Сақтау'], ['Изменить', 'Edit', 'Өзгерту'],
    ['Удалить', 'Delete', 'Жою'], ['Подтвердить', 'Confirm', 'Растау'], ['Закрыть', 'Close', 'Жабу'],
    ['Новый курс', 'New course', 'Жаңа курс'], ['Новое задание', 'New assignment', 'Жаңа тапсырма'],
    ['МОЁ ОБУЧЕНИЕ', 'MY STUDIES', 'МЕНІҢ ОҚУЫМ'], ['Мои курсы', 'My courses', 'Менің курстарым'],
    ['Предметы, преподаватели и задания по каждому курсу.', 'Subjects, teachers and assignments for each course.', 'Әр курстың пәндері, оқытушылары және тапсырмалары.'],
    ['Соберите учебные предметы в одном месте.', 'Keep your subjects in one place.', 'Оқу пәндерін бір жерге жинаңыз.'],
    ['Найти курс или преподавателя', 'Find a course or teacher', 'Курс немесе оқытушыны табу'],
    ['КУРС', 'COURSE', 'КУРС'], ['Задания курса', 'Course assignments', 'Курс тапсырмалары'],
    ['Обзор курса', 'Course overview', 'Курсқа шолу'], ['Задания этого курса', 'Assignments in this course', 'Осы курстың тапсырмалары'],
    ['В этом курсе пока нет заданий.', 'This course has no assignments yet.', 'Бұл курста әлі тапсырмалар жоқ.'],
    ['Изменить курс', 'Edit course', 'Курсты өзгерту'], ['Преподаватель', 'Teacher', 'Оқытушы'],
    ['Название курса', 'Course title', 'Курс атауы'], ['Название', 'Title', 'Атауы'], ['Описание', 'Description', 'Сипаттама'],
    ['Описание не добавлено', 'No description added', 'Сипаттама қосылмаған'], ['К курсу', 'Back to course', 'Курсқа оралу'],
    ['МОЙ ПЛАН', 'MY PLAN', 'МЕНІҢ ЖОСПАРЫМ'], ['Учебные задания', 'Study assignments', 'Оқу тапсырмалары'],
    ['Следите за сроками и отмечайте выполненное.', 'Track deadlines and mark work as complete.', 'Мерзімдерді бақылап, орындалғанын белгілеңіз.'],
    ['Все', 'All', 'Барлығы'], ['В процессе', 'In progress', 'Орындалуда'], ['Выполнено', 'Completed', 'Орындалды'],
    ['Просрочено', 'Overdue', 'Мерзімі өткен'], ['Срок выполнения', 'Due date', 'Орындау мерзімі'],
    ['Найти задание', 'Find an assignment', 'Тапсырманы табу'], ['Найти задание или курс', 'Find an assignment or course', 'Тапсырма немесе курсты табу'],
    ['Название задания', 'Assignment title', 'Тапсырма атауы'], ['Курс', 'Course', 'Курс'],
    ['Срок и курс помогают держать учебный план в порядке.', 'A deadline and course keep your study plan organized.', 'Мерзім мен курс оқу жоспарын реттеуге көмектеседі.'],
    ['Календарь', 'Calendar', 'Күнтізбе'], ['Календарь заданий', 'Assignment calendar', 'Тапсырмалар күнтізбесі'], ['Дедлайны по выбранным фильтрам.', 'Deadlines for the selected filters.', 'Таңдалған сүзгілерге сай мерзімдер.'],
    ['Откройте задание прямо из дня его выполнения.', 'Open an assignment from its due date.', 'Тапсырманы орындалатын күнінен ашыңыз.'],
    ['Сегодня', 'Today', 'Бүгін'], ['Предыдущий месяц', 'Previous month', 'Алдыңғы ай'], ['Следующий месяц', 'Next month', 'Келесі ай'],
    ['Учебная статистика', 'Study statistics', 'Оқу статистикасы'], ['Осталось', 'Remaining', 'Қалғаны'],
    ['В работе', 'In progress', 'Орындалуда'], ['Выполнение заданий', 'Assignment progress', 'Тапсырмалардың орындалуы'],
    ['Следующие задания в вашем плане.', 'Next assignments in your plan.', 'Жоспарыңыздағы келесі тапсырмалар.'],
    ['Эта неделя', 'This week', 'Осы апта'], ['Дедлайны по дням', 'Daily deadlines', 'Күндер бойынша мерзімдер'],
    ['Ближайшие сроки', 'Upcoming deadlines', 'Жақын мерзімдер'], ['Ваши следующие задания', 'Your next assignments', 'Келесі тапсырмаларыңыз'],
    ['Ваш учебный план и ближайшие дела — на одной странице.', 'Your study plan and upcoming work on one page.', 'Оқу жоспарыңыз бен жақын істеріңіз — бір бетте.'],
    ['Добавить пользователя', 'Add user', 'Қатысушы қосу'], ['Редактирование пользователя', 'Edit user', 'Қатысушыны өңдеу'],
    ['Изменить пользователя', 'Edit user', 'Қатысушыны өзгерту'], ['Учётные записи и роли участников.', 'Member accounts and roles.', 'Қатысушылардың аккаунттары мен рөлдері.'],
    ['Поиск по имени или email', 'Search by name or email', 'Аты немесе email бойынша іздеу'], ['Поиск пользователей', 'Search users', 'Қатысушыларды іздеу'],
    ['Полное имя', 'Full name', 'Толық аты'], ['Электронная почта', 'Email address', 'Электрондық пошта'], ['Пароль', 'Password', 'Құпиясөз'],
    ['Роль', 'Role', 'Рөл'], ['Действия', 'Actions', 'Әрекеттер'], ['Студент', 'Student', 'Студент'], ['Администратор', 'Administrator', 'Әкімші'],
    ['Вход', 'Sign in', 'Кіру'], ['Регистрация', 'Registration', 'Тіркелу'], ['Войти', 'Sign in', 'Кіру'],
    ['С возвращением', 'Welcome back', 'Қайта оралуыңызбен'], ['Войти в StudHub', 'Sign in to StudHub', 'StudHub жүйесіне кіру'],
    ['Создать аккаунт', 'Create account', 'Аккаунт құру'], ['Создать профиль', 'Create profile', 'Профиль құру'],
    ['Нет аккаунта?', 'No account yet?', 'Аккаунтыңыз жоқ па?'], ['Уже есть аккаунт?', 'Already have an account?', 'Аккаунтыңыз бар ма?'],
    ['Введите пароль', 'Enter password', 'Құпиясөзді енгізіңіз'], ['Не менее 8 символов', 'At least 8 characters', 'Кемінде 8 таңба'],
    ['Как к вам обращаться', 'What should we call you?', 'Сізге қалай хабарласайық?'], ['Вернуться к предпросмотру', 'Back to preview', 'Алдын ала қарауға оралу'],
    ['Личные данные вашей учётной записи.', 'Your account details.', 'Аккаунтыңыздың жеке деректері.'],
    ['Тема оформления', 'Color theme', 'Көрініс тақырыбы'], ['Светлая тема', 'Light theme', 'Ашық тақырып'], ['Тёмная тема', 'Dark theme', 'Қараңғы тақырып'],
    ['Сбросить данные предпросмотра', 'Reset preview data', 'Алдын ала қарау деректерін қалпына келтіру'],
    ['Открыть меню', 'Open menu', 'Мәзірді ашу'], ['Перейти к содержимому', 'Skip to content', 'Мазмұнға өту'],
    ['Отметить выполненным', 'Mark complete', 'Орындалды деп белгілеу'], ['Вернуть в работу', 'Mark incomplete', 'Орындалуға қайтару'],
    ['Показать пароль', 'Show password', 'Құпиясөзді көрсету'], ['Скрыть пароль', 'Hide password', 'Құпиясөзді жасыру'],
    ['Помощник', 'Assistant', 'Көмекші'], ['Помощник StudHub', 'StudHub assistant', 'StudHub көмекшісі'],
    ['Открыть помощника', 'Open assistant', 'Көмекшіні ашу'], ['Напишите сообщение…', 'Write a message…', 'Хабарлама жазыңыз…'],
    ['Отправить', 'Send', 'Жіберу'], ['На связи', 'Online', 'Байланыста'],
    ['StudHub · Ваше пространство для развития', 'StudHub · Your space to grow', 'StudHub · Даму кеңістігі']
  ];
  phrases.push(
    ['Основная навигация','Main navigation','Негізгі навигация'], ['Свернуть боковую панель','Collapse sidebar','Бүйірлік панельді жию'],
    ['Сбросить фильтры','Reset filters','Сүзгілерді тазалау'], ['На главную','Home','Басты бетке'], ['Переключить тему','Switch theme','Тақырыпты ауыстыру'],
    ['Загрузка…','Loading…','Жүктелуде…'], ['Всего','Total','Барлығы'], ['Статус','Status','Күйі'],
    ['Через {0} дн.','In {0} days','{0} күннен кейін'], ['{0} дн. назад','{0} days ago','{0} күн бұрын'],
    ['Пользователь','User','Қатысушы'], ['Нет данных','No data','Деректер жоқ'], ['Подтвердите удаление','Confirm deletion','Жоюды растаңыз'],
    ['Это действие нельзя отменить.','This action cannot be undone.','Бұл әрекетті қайтару мүмкін емес.'],
    ['Изменения сохранены','Changes saved','Өзгерістер сақталды'], ['Запись удалена','Item deleted','Жазба жойылды'],
    ['Не удалось выполнить действие. Попробуйте ещё раз.','The action failed. Please try again.','Әрекетті орындау мүмкін болмады. Қайта көріңіз.'],
    ['Задание','Assignment','Тапсырма'], ['Доступ запрещён','Access denied','Кіруге тыйым салынған'],
    ['В учебном пространстве','In your workspace','Оқу кеңістігінде'], ['Всего в списке','Total in the list','Тізімдегі барлығы'],
    ['Быстрые действия','Quick actions','Жылдам әрекеттер'], ['С чего начнём?','Where shall we start?','Неден бастаймыз?'],
    ['Добавьте предметы и держите важное под рукой.','Add subjects and keep important work within reach.','Пәндерді қосып, маңызды істерді қолжетімді ұстаңыз.'],
    ['Добавить предмет и преподавателя','Add a subject and teacher','Пән мен оқытушы қосу'], ['Запланировать следующую работу','Plan your next assignment','Келесі жұмысты жоспарлау'],
    ['Открыть дедлайны в календаре','Open deadlines in the calendar','Мерзімдерді күнтізбеден ашу'], ['План пока свободен','Your plan is clear','Жоспар әзірге бос'],
    ['Добавьте курс или задание, чтобы начать.','Add a course or assignment to get started.','Бастау үшін курс немесе тапсырма қосыңыз.'],
    ['Добавить курс','Add course','Курс қосу'], ['Добавить задание','Add assignment','Тапсырма қосу'],
    ['Сортировка','Sort','Сұрыптау'], ['По названию: А—Я','Title: A–Z','Атауы: А–Я'], ['По названию: Я—А','Title: Z–A','Атауы: Я–А'],
    ['По преподавателю','By teacher','Оқытушы бойынша'], ['Плитки','Grid','Тор'], ['Таблица','Table','Кесте'],
    ['Курсов пока нет','No courses yet','Әзірге курстар жоқ'], ['Создайте первый курс, чтобы собрать своё учебное пространство.','Create your first course to build your study workspace.','Оқу кеңістігін құру үшін алғашқы курсты қосыңыз.'],
    ['Создать первый курс','Create first course','Алғашқы курсты құру'], ['Открыть курс','Open course','Курсты ашу'],
    ['Удалить курс «{0}»?','Delete course “{0}”?','«{0}» курсын жою керек пе?'],
    ['Связанные задания могут помешать удалению курса.','Linked assignments may prevent this course from being deleted.','Байланысты тапсырмалар курсты жоюға кедергі болуы мүмкін.'],
    ['Курсов по вашему запросу не найдено.','No courses match your search.','Сұрауыңыз бойынша курстар табылмады.'],
    ['Редактирование курса','Edit course','Курсты өңдеу'], ['Например, Основы Java','For example, Java fundamentals','Мысалы, Java негіздері'],
    ['Имя преподавателя','Teacher name','Оқытушының аты'], ['Что изучаете на этом курсе?','What do you study in this course?','Бұл курста нені оқисыз?'],
    ['Все курсы','All courses','Барлық курстар'], ['Приоритет','Priority','Басымдық'], ['Все приоритеты','All priorities','Барлық басымдықтар'],
    ['Представление заданий','Assignment view','Тапсырмалар көрінісі'], ['Список','List','Тізім'],
    ['Перейти к текущему месяцу','Go to current month','Ағымдағы айға өту'],
    ['Нажмите на задание, чтобы открыть его редактирование.','Select an assignment to edit it.','Өңдеу үшін тапсырманы таңдаңыз.'],
    ['Все дедлайны и учебный план — в одном месте.','All deadlines and your study plan in one place.','Барлық мерзімдер мен оқу жоспары бір жерде.'],
    ['Заданий в этом месяце','Assignments this month','Осы айдағы тапсырмалар'],
    ['Заданий пока нет','No assignments yet','Әзірге тапсырмалар жоқ'], ['Добавьте задание и выберите курс.','Add an assignment and select a course.','Тапсырма қосып, курсты таңдаңыз.'],
    ['Создать первое задание','Create first assignment','Алғашқы тапсырманы құру'],
    ['По выбранным фильтрам ничего не найдено.','No results for these filters.','Таңдалған сүзгілер бойынша ештеңе табылмады.'],
    ['Срок сегодня','Due today','Мерзімі бүгін'], ['Срок завтра','Due tomorrow','Мерзімі ертең'], ['Осталось дней: {0}','Days remaining: {0}','Қалған күндер: {0}'],
    ['Удалить задание «{0}»?','Delete assignment “{0}”?','«{0}» тапсырмасын жою керек пе?'],
    ['Редактирование задания','Edit assignment','Тапсырманы өңдеу'], ['Выберите курс','Select a course','Курсты таңдаңыз'],
    ['Например, лабораторная работа № 2','For example, lab assignment #2','Мысалы, № 2 зертханалық жұмыс'],
    ['Сначала добавьте курс, чтобы создавать задания.','Add a course before creating assignments.','Тапсырмалар құру үшін алдымен курс қосыңыз.'],
    ['Задание выполнено','Assignment completed','Тапсырма орындалды'], ['Низкий','Low','Төмен'], ['Средний','Medium','Орташа'], ['Высокий','High','Жоғары'],
    ['Войдите в свой учебный кабинет','Sign in to your study workspace','Оқу кабинетіңізге кіріңіз'],
    ['Начните организовывать учёбу в StudHub','Organize your studies with StudHub','StudHub арқылы оқуыңызды ұйымдастырыңыз'],
    ['Курсы и дедлайны — в одном месте','Courses and deadlines in one place','Курстар мен мерзімдер бір жерде'],
    ['Курсы и учебные материалы','Courses and study materials','Курстар және оқу материалдары'],
    ['Понятный список заданий','A clear assignment list','Түсінікті тапсырмалар тізімі'],
    ['Доступ для студентов и преподавателей','Access for students and teachers','Студенттер мен оқытушыларға қолжетімділік'],
    ['Зарегистрироваться','Register','Тіркелу'], ['Повторите пароль','Confirm password','Құпиясөзді қайталаңыз'],
    ['Пароли совпадают','Passwords match','Құпиясөздер сәйкес келеді'], ['Пароли не совпадают','Passwords do not match','Құпиясөздер сәйкес келмейді'],
    ['Надёжность пароля','Password strength','Құпиясөздің сенімділігі'], ['Минимум 8 символов','At least 8 characters','Кемінде 8 таңба'],
    ['Добавьте цифры или символы','Add numbers or symbols','Сандар немесе таңбалар қосыңыз'], ['Надёжный пароль','Strong password','Сенімді құпиясөз'],
    ['Проверьте email, пароль и код двухфакторной защиты, если она включена.','Check your email, password and two-factor code, if enabled.','Email, құпиясөз және қосылған болса екі факторлы кодты тексеріңіз.'],
    ['Вы вышли из системы.','You have signed out.','Жүйеден шықтыңыз.'], ['Аккаунт создан. Теперь войдите.','Account created. You can now sign in.','Аккаунт құрылды. Енді кіріңіз.'],
    ['Не удалось создать аккаунт.','Could not create the account.','Аккаунт құру мүмкін болмады.'],
    ['Заполните, если включена двухфакторная защита.','Fill this in if two-factor authentication is enabled.','Екі факторлы қорғаныс қосылған болса, толтырыңыз.'],
    ['Все роли','All roles','Барлық рөлдер'], ['Пользователей пока нет','No users yet','Әзірге қатысушылар жоқ'],
    ['Не удалось загрузить пользователей.','Could not load users.','Қатысушыларды жүктеу мүмкін болмады.'], ['Новый пользователь','New user','Жаңа қатысушы'],
    ['Редактировать пользователя','Edit user','Қатысушыны өңдеу'], ['Удалить пользователя «{0}»?','Delete user “{0}”?','«{0}» қатысушысын жою керек пе?'],
    ['Пароль обязателен, минимум 8 символов','Password required, at least 8 characters','Құпиясөз міндетті, кемінде 8 таңба'],
    ['Нельзя удалить свою учётную запись.','You cannot delete your own account here.','Бұл жерде өз аккаунтыңызды жоя алмайсыз.'],
    ['Данные пользователя сохранены.','User details saved.','Қатысушы деректері сақталды.'], ['Пользователь удалён.','User deleted.','Қатысушы жойылды.'],
    ['Не удалось выполнить запрос.','The request failed.','Сұрауды орындау мүмкін болмады.'],
    ['Этот email уже используется другим пользователем.','Another user already uses this email.','Бұл email басқа қатысушыда қолданылады.'],
    ['Так вас будут видеть в StudHub.','This is how you appear in StudHub.','StudHub жүйесінде сіз осылай көрінесіз.'],
    ['Роль назначается администратором.','Your role is assigned by an administrator.','Рөлді әкімші тағайындайды.'],
    ['У вас нет доступа к этой странице','You do not have access to this page','Бұл бетке кіруге рұқсатыңыз жоқ'],
    ['Попросите администратора изменить права учётной записи.','Ask your administrator to update your account permissions.','Аккаунт рұқсаттарын өзгерту үшін әкімшіге хабарласыңыз.'],
    ['Страница не найдена','Page not found','Бет табылмады'], ['Возможно, ссылка устарела или адрес введён с ошибкой.','The link may be outdated or the address incorrect.','Сілтеме ескірген немесе мекенжай қате енгізілген болуы мүмкін.'],
    ['Что-то пошло не так','Something went wrong','Бірдеңе дұрыс болмады'], ['Сервер не смог обработать запрос. Попробуйте позже.','The server could not process your request. Try again later.','Сервер сұрауды өңдей алмады. Кейінірек қайталаңыз.'],
    ['Произошла ошибка','An error occurred','Қате пайда болды'], ['Сначала создайте курс','Create a course first','Алдымен курс құрыңыз'],
    ['Заполните это поле.','Fill in this field.','Бұл өрісті толтырыңыз.'], ['Введите корректный email.','Enter a valid email.','Дұрыс email енгізіңіз.'],
    ['Минимум {0} символов.','At least {0} characters.','Кемінде {0} таңба.'], ['Максимум {0} символов.','Up to {0} characters.','Ең көбі {0} таңба.'],
    ['Пн','Mon','Дс'], ['Вт','Tue','Сс'], ['Ср','Wed','Ср'], ['Чт','Thu','Бс'], ['Пт','Fri','Жм'], ['Сб','Sat','Сб'], ['Вс','Sun','Жс'],
    ['из','of','ішінен'], ['ДЕДЛАЙНЫ','DEADLINES','МЕРЗІМДЕР'], ['Без курса','No course','Курссыз'],
    ['Задания выбранного курса.','Assignments for the selected course.','Таңдалған курстың тапсырмалары.'],
    ['Добавьте название, преподавателя и описание.','Add a title, teacher and description.','Атауын, оқытушыны және сипаттаманы қосыңыз.']
  );
  Object.values(labels).forEach(row => phrases.push(row));
  const canonical = new Map();
  phrases.forEach(row => row.forEach(value => canonical.set(value.toLocaleLowerCase(), row)));
  const templates = phrases.filter(row => row[0].includes('{0}')).flatMap(row => row.map(value => ({row, pattern:new RegExp('^' + value.split('{0}').map(part => part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('(.+)') + '$')})));
  const originals = new WeakMap();
  let language = languages.includes(localStorage.getItem('studhub-language')) ? localStorage.getItem('studhub-language') : 'ru';
  const t = (key, vars = {}) => (labels[key]?.[languages.indexOf(language)] || key).replace(/\{(\w+)\}/g, (_, name) => String(vars[name] ?? ''));
  const translateText = text => {
    const trimmed = text.trim();
    let result = canonical.get(trimmed.toLocaleLowerCase())?.[languages.indexOf(language)];
    if (!result) {
      for (const template of templates) {
        const match = trimmed.match(template.pattern);
        if (match) { result = template.row[languages.indexOf(language)].replace('{0}',match[1]); break; }
      }
      const greeting = trimmed.match(/^(Добрый день|Доброе утро|Добрый вечер|Здравствуйте|Good afternoon|Good morning|Good evening|Hello|Қайырлы күн|Қайырлы таң|Қайырлы кеш|Сәлеметсіз бе),\s*(.+)$/);
      if (greeting) result = `${['Добрый день', 'Hello', 'Қайырлы күн'][languages.indexOf(language)]}, ${greeting[2]}`;
      const count = trimmed.match(/^(\d+) (курс(?:а|ов)?|courses?|курстар?|задани[ейя]|assignments?|тапсырма)$/);
      if (count) {
        const course = /курс|course/.test(count[2]);
        if (language === 'en') result = `${count[1]} ${course ? 'course' : 'assignment'}${count[1] === '1' ? '' : 's'}`;
        if (language === 'kk') result = `${count[1]} ${course ? 'курс' : 'тапсырма'}`;
      }
    }
    return result ? text.replace(trimmed, result) : text;
  };
  const skip = '[data-settings-root],[data-study-assistant],[data-ai-guide],script,style,textarea,code,pre,[data-user-content],[data-preview-name],.course-title-link,.course-description,.assignment-title,.course-detail-teacher';
  function scan(root) {
    if (!root || root.nodeType !== 1 && root.nodeType !== 3) return;
    if (root.nodeType === 3) {
      if (root.parentElement?.closest(skip)) return;
      const value = root.nodeValue;
      const cached = originals.get(root);
      const source = cached && value === cached.last ? cached.source : value;
      const translated = translateText(source);
      originals.set(root, {source, last: translated});
      if (value !== translated) root.nodeValue = translated;
      return;
    }
    if (root.matches(skip)) return;
    ['placeholder', 'aria-label', 'title', 'data-toast-saved', 'data-toast-error', 'data-toast-deleted', 'data-role-user', 'data-role-admin'].forEach(attr => {
      if (root.hasAttribute(attr)) {
        const value = root.getAttribute(attr), translated = translateText(value);
        if (value !== translated) root.setAttribute(attr, translated);
      }
    });
    if (root.id === 'uiMessages') root.getAttributeNames().filter(name => name.startsWith('data-')).forEach(name => root.setAttribute(name,translateText(root.getAttribute(name))));
    if (root.matches('time[datetime]')) {
      const date = new Date(root.getAttribute('datetime') + 'T12:00:00');
      if (!Number.isNaN(date.getTime())) root.textContent = new Intl.DateTimeFormat(language,{day:'numeric',month:'short'}).format(date);
    }
    root.childNodes.forEach(scan);
    if (root.matches('.sidebar-link[data-preview-route]')) {
      const caption = root.querySelector('span')?.textContent.trim();
      if (caption) { root.setAttribute('aria-label',caption); root.setAttribute('title',caption); }
    }
  }
  function setLanguage(value, emit = true) {
    language = languages.includes(value) ? value : 'ru';
    localStorage.setItem('studhub-language', language);
    document.documentElement.lang = language;
    scan(document.body);
    if (emit) document.dispatchEvent(new CustomEvent('studhub:language', {detail: language}));
  }
  window.StudHubI18n = { t, setLanguage, translate: () => scan(document.body), get language() { return language; } };
  setLanguage(language, false);
  new MutationObserver(records => {
    for (const record of records) {
      if (record.type === 'characterData') scan(record.target);
      else record.addedNodes.forEach(scan);
    }
  }).observe(document.body, {subtree:true, childList:true, characterData:true});
  document.dispatchEvent(new CustomEvent('studhub:i18n-ready'));
})();
