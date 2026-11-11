# Workflow for StudHub

- Before changing code, create a dedicated Git feature or fix branch.
- Save completed, tested increments as descriptive commits.
- Merge into the primary branch only after applicable automated tests, build,
  code review and browser checks pass. Never merge failing work.
- Local primary branch is `master`, tracking `origin/main`. Do not rename it implicitly.
- Preserve unrelated work; do not commit secrets, logs, generated builds or test accounts.
- Do not push unless the user requests it.
- Update the existing graphify code graph after architectural changes.
