Read TASK.md, PROGRESS.md, and these reference files:
- D:\android_2025\mini_stock\StockApp\MIGRATION_SPEC.md (feature specification)
- D:\android_2025\mini_stock\StockApp\FILE_MANIFEST.md (file list and dependencies)
- Also read StockApp source code as needed from D:\android_2025\mini_stock\StockApp\

Mission: Implement Financial Information tab in the current project's Stock menu. When stock analysis completes, display financial info. Store collected financial data in local DB. Update on new or changed data. Replicate exact functionality from StockApp.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use source-migrator and feature-extractor subagents. Analyze StockApp source, implement feature in current project following MVVM + Clean Architecture + Feature module pattern.
2. QA-Engineer (Sonnet): Use qa-verifier subagent. Verify each implementation step: build, tests, DB operations, data flow correctness.
3. Architect-Reviewer (Opus): Approve implementation plans for data layer (Room DB schema), DI configuration, and navigation changes. Final quality gate.

Use Subagents:
- feature-extractor (haiku): Scan StockApp source to identify needed code.
- source-migrator (sonnet): Transform StockApp code to current project patterns.
- kotlin-implementer (sonnet): Implement new Kotlin code following project conventions.
- qa-verifier (sonnet): Test and verify implementations.

Implementation Requirements:
1. Financial Info Tab: Add tab under Stock menu. Show financial data after stock analysis.
2. Data Layer: Room DB table for financial info. Insert on first collection, update on changes.
3. Domain Layer: Entity, Repository interface, UseCases (GetFinancialInfo, SaveFinancialInfo, UpdateFinancialInfo).
4. Presentation: ViewModel with StateFlow, UI showing financial metrics.
5. DI: Hilt module for financial info feature.
6. Navigation: Wire tab into existing stock analysis flow.
7. Functional Parity: Must match StockApp behavior exactly.

Workflow per iteration:
1. Lead reads TASK.md, picks next incomplete task.
2. For analysis tasks: use feature-extractor to scan StockApp source.
3. For implementation tasks: write plan to PROGRESS.md, Architect approves, implement.
4. If plan rejected: revise and resubmit (max 2 retries).
5. For each code change: QA verifies build passes.
6. Lead marks task done after verification.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- Financial Info tab visible in Stock menu.
- DB schema created with Room, CRUD operations working.
- Data persists and updates correctly.
- gradlew assembleDebug passes.
- gradlew test passes.
- Functionality matches StockApp exactly.
- IMPLEMENTATION_REPORT.md generated.
- CLAUDE.md updated with new feature architecture.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.