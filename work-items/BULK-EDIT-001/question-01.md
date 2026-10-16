# Decision gate 1 — bulk-edit scope and rules (BULK-EDIT-001)

Cannot design without these; they change endpoints, validation semantics, and filter scope.

- Q-1 (targets): any graph-legal status, or restricted list? Proposal: any graph-legal target (graph already encodes policy).
- Q-2 (batch policy): strict reject-all vs lenient flip-legal + report? Proposal: lenient (`bulkTransitionLenient` exists).
- Q-3 (filters): fix server-side date/status/search filtering in this run (needed for S-3 select-filtered)? Proposal: yes, same root cause as the reported filter bug.
- Q-4 (selection scope): current filtered table only (across its pages?), or also cross-page selection? Proposal: explicit choice needed — affects select-all semantics.

Details, consequences and full text: `requirements-draft.md`. Awaiting actual user reply; silence is not approval.
