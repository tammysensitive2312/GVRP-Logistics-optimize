# Findings — DRIVER-APP-001, test values (after acceptance rev 9)

Date: 2026-10-31. Source: the user's decisions in the Claude Code session, answering the two open AGENTS.md §14 items from `verification.md`:

> "đồng ý xoá file test mẫu widget_test.dart và bỏ các giá trị giả trong test"

| # | Finding | Route |
|---|---|---|
| F1 | Removing the generated template test `test/widget_test.dart` is approved. No file change is needed: it is already absent | record only |
| F2 | The tests must not hold token, password or account literals (`fake-token-for-tests`, `not-a-real-password`, `taixe_thu`, `Chi nhánh Thử`, `x`). Task rule: "Test accounts, passwords or tokens in any file." | implement: generate these values at run time in `test/support/test_app.dart` |

Invented sample customer data (names such as "Khách hàng 1", phones `09000000xx`, coordinates near 10.0/106.0) is fixture data, not accounts or credentials, and stays.

Scope: test files only. No `lib/` change. After the change: re-verify and give a new handoff for user acceptance.
