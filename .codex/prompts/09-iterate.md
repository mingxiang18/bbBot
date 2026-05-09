# Iteration Prompt

Read the user's feedback.

Save it to:
- `.ai-workflow/10-user-feedback.md`

Classify feedback as bug, UX issue, UI polish, missing requirement, new feature, performance issue, or technical debt.

Then decide: direct fix, return to product plan, return to UI design, or return to technical design.

If scope changes, stop and ask for approval.

If it is a bug or small polish:

1. Implement minimal fix.
2. Add/update tests.
3. Run checks.
4. Update review report.
5. Update QA report.
6. Ask for acceptance.
