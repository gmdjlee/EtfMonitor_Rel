---
active: true
iteration: 1
max_iterations: 15
completion_promise: "COMPLETE"
started_at: "2026-02-14T06:36:01Z"
---

Read TASK.md and PROGRESS.md. Execute the next incomplete task.

## Mission
Post-migration review: verify pykrx→kotlin_krx migration has 100% functional parity.
Then cleanup all dead code, unused files, and legacy dependencies.

## Agent Team (3 members)
Spawn 3 teammates:
1. Verifier (Sonnet): Functional parity checks — compare every pykrx call site against kotlin_krx implementation. Diff data flows, DTOs, error handling.
2. QA-Engineer (Sonnet): Test coverage + performance benchmarks + build verification + stability. Run tests, measure latency, verify clean builds.
3. Architect-Reviewer (Opus): Approve/reject cleanup plans. Final quality gate. Architecture consistency review.

## Workflow
1. Lead reads TASK.md → picks next incomplete task
2. For cleanup tasks (R-005~R-007): write plan to PROGRESS.md → Architect-Reviewer approves before deletion
3. If rejected: revise plan, resubmit (max 2 retries then flag for human)
4. Verifier or QA executes the approved task
5. All teammates log findings to PROGRESS.md
6. Lead marks task [x] in TASK.md after verification

## Completion
ALL conditions must be met:
- Every task in TASK.md is [x]
- ./gradlew assembleDebug passes
- ./gradlew test passes
- REVIEW_REPORT.md generated with full findings
- CLAUDE.md updated with post-migration architecture
- PROGRESS.md contains LOOP_COMPLETE

Output <promise>COMPLETE</promise> only when ALL above are verified.
