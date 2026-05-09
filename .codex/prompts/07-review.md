# Review Prompt

Review the current diff against:

- `AGENTS.md`
- `.ai-workflow/decisions/approved-requirements.md`
- `.ai-workflow/decisions/approved-product-plan.md`
- `.ai-workflow/decisions/approved-ui.md`
- `.ai-workflow/decisions/approved-tech-plan.md`

Create:
- `.ai-workflow/08-review-report.md`

Review requirement match, UI/UX match, code quality, simplicity, maintainability, error handling, states, boundary cases, security, performance, and test coverage.

Output:

| Severity | Issue | File | Recommendation |
|---|---|---|---|

Then provide final result: Pass, Pass with notes, or Fail.

If fail, recommend the smallest fix plan.
Do not apply fixes unless asked.
