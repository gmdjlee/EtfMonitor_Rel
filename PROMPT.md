Read TASK.md, PROGRESS.md, and any existing migration analysis reports (MIGRATION_MAP.md, MIGRATION_REVIEW_REPORT.md, COVERAGE_MAP.md — whichever exist).

Mission: Migrate blood_indicator.py to native Kotlin. Maintain 100% functional parity with the Python version. Follow MVVM + Clean Architecture + Feature module pattern.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use python-analyzer to analyze blood_indicator.py, then use kotlin-implementer to build Kotlin equivalent. Implement in Clean Architecture layers.
2. QA-Engineer (Sonnet): Use qa-verifier to validate functional parity. Tests, build, output comparison vs Python.
3. Architect-Reviewer (Opus): Approve implementation plan before coding starts. Reject if architecture deviates from project patterns. Final quality gate.

Use Subagents:
- python-analyzer (haiku): Analyze blood_indicator.py logic, KRX API calls, calculations.
- kotlin-implementer (sonnet): Implement Kotlin code in project architecture.
- qa-verifier (sonnet): Verify parity, run tests, check build.

Rules:
- Read existing migration analysis first. Do not re-analyze what is already documented.
- Implementation plan required before coding. Write to PROGRESS.md, get Architect approval.
- If rejected: revise and resubmit (max 2 retries).
- After every code change: run gradlew assembleDebug.
- Every Blood Indicator calculation must produce identical output to Python version for same input.
- Log all changes to PROGRESS.md.

Workflow:
1. Lead reads TASK.md, picks next task.
2. Analysis tasks: python-analyzer scans, logs to PROGRESS.md.
3. Implementation tasks: plan in PROGRESS.md, Architect approves, implement, QA verifies.
4. Lead marks task done after verification.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- All blood_indicator.py functions migrated to Kotlin.
- gradlew assembleDebug passes.
- gradlew test passes.
- Output parity verified vs Python for test inputs.
- MIGRATION_REPORT.md generated.
- CLAUDE.md updated.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.