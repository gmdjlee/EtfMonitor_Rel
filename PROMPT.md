Read TASK.md and PROGRESS.md.

Reference files:
- D:\android_2025\mini_stock\docs\RANKING_FEATURE_SPEC.md (feature specification)
- D:\android_2025\mini_stock\docs\RANKING_FEATURE_UI_SPEC.md (UI specification)
- D:\android_2025\mini_stock\StockApp\ (source code to migrate from)

Mission: Migrate the Ranking feature from StockApp to the current project. Replicate exact functionality. Follow MVVM + Clean Architecture + Feature module pattern.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use feature-extractor to scan StockApp ranking code, then use source-migrator and kotlin-implementer to build in current project architecture.
2. QA-Engineer (Sonnet): Use qa-verifier. Verify build, test each ranking type, UI rendering, data flow correctness.
3. Architect-Reviewer (Opus): Approve data layer schema and navigation integration plan before implementation.

Use Subagents:
- feature-extractor (haiku): Scan StockApp source for ranking-related files.
- source-migrator (sonnet): Transform StockApp code to current project patterns.
- kotlin-implementer (sonnet): Implement in Clean Architecture layers.
- qa-verifier (sonnet): Test and verify.

Rules:
- Read BOTH spec documents completely before any implementation.
- Implementation plan required before coding. Write to PROGRESS.md, Architect approves.
- If rejected: revise and resubmit (max 2 retries).
- After every code change: run gradlew assembleDebug.
- Must match StockApp ranking functionality exactly.
- Log all changes to PROGRESS.md.

Workflow:
1. Lead reads TASK.md, picks next task.
2. Analysis tasks: feature-extractor scans StockApp, logs findings.
3. Implementation tasks: plan in PROGRESS.md, Architect approves, implement, QA verifies.
4. Lead marks task done after verification.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- All ranking types from spec implemented and functional.
- UI matches RANKING_FEATURE_UI_SPEC.md.
- gradlew assembleDebug passes.
- gradlew test passes.
- Functionality matches StockApp exactly.
- IMPLEMENTATION_REPORT.md generated.
- CLAUDE.md updated with ranking feature architecture.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.