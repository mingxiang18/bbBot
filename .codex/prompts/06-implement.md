# Implementation Prompt

Read:
- `AGENTS.md`
- `.ai-workflow/decisions/approved-requirements.md`
- `.ai-workflow/decisions/approved-product-plan.md`
- `.ai-workflow/decisions/approved-ui.md`
- `.ai-workflow/decisions/approved-tech-plan.md`

If any approved file is missing, stop.

Then:

1. Create or switch to branch `ai/feature-<short-name>`.
2. Create `.ai-workflow/06-implementation-plan.md`.
3. Implement only the approved scope.
4. Add or update tests.
5. Run the project check command.
6. If checks fail, analyze, fix minimal cause, rerun, and repeat up to 3 times.
7. If still failing, stop and write `.ai-workflow/failed-check-report.md`.
8. If passing, write `.ai-workflow/implementation-summary.md`.
9. Prepare a commit with a clear message.

Do not merge main.
Do not push unless explicitly allowed.
