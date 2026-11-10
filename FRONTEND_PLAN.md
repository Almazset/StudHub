# StudHub frontend plan

## Existing foundation

StudHub already uses Spring MVC and Thymeleaf, with page routes for the dashboard, sign-in/registration, courses, and assignments. Keep that stack: it keeps forms, validation, authentication, and the REST API in one application without adding a second build system.

## Delivered in this first frontend pass

- Added an upcoming assignment panel to the dashboard, showing the next three incomplete tasks and marking overdue dates.
- Added responsive navigation with a keyboard accessible mobile menu, Escape handling, active page highlighting, and a direct API link.
- Added responsive dashboard styling and compact task cards for small screens.

## Practical next steps

1. **Make the workspace personal.** Filter courses and assignments by the signed-in account, or explicitly make them shared class resources. The current service layer returns all records to every authenticated user.
2. **Improve the course and assignment lists.** Add search, status/course filters, sorting by due date, and pagination when the dataset grows. For narrow screens, render each row as a readable card instead of relying on horizontal table scrolling.
3. **Complete the assignment workflow.** Add a one-click complete/reopen action, clear overdue and due-soon labels, and an empty-courses state on the assignment form.
4. **Add usability details.** Associate every visible label with its input, retain values and show field-level errors after server validation, and add confirmation/success feedback for save and delete actions.
5. **Add integration coverage.** Once PostgreSQL is available (or a test database is configured), cover login, registration, course CRUD, assignment CRUD, authorization, and template rendering.
6. **Polish delivery.** Add a visual theme toggle only if it fits the course scope, then capture desktop/mobile screenshots and update the README with verified setup instructions.

## Local prerequisites

- JDK 21 and Maven 3.9+ are available in this environment.
- The application expects PostgreSQL at `localhost:5432/studhub` by default. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, or start a local PostgreSQL database before running the web app.
- Override `STUDHUB_ADMIN_PASSWORD` with a unique value before starting a shared/demo instance; the source default is a known development password.
