Read TASK.md and PROGRESS.md.

Mission: Update Stock menu features. 4 items: sync supply-demand oscillator algorithm, sync trend signal algorithm, fix elder impulse bug, migrate intraday real-time feature. Keep current API approach except where real-time requires new APIs. Do not change anything else.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use source-migrator and kotlin-implementer subagents. Analyze reference implementations, implement algorithm updates and real-time feature. Follow MVVM + Clean Architecture + Feature module pattern.
2. QA-Engineer (Sonnet): Use qa-verifier subagent. Verify algorithm output parity, elder impulse fix, real-time data flow, build and tests.
3. Architect-Reviewer (Opus): Approve real-time feature integration plan (new API usage, data flow). Review algorithm changes before apply.

Use Subagents:
- feature-extractor (haiku): Scan reference source for algorithm logic.
- source-migrator (sonnet): Adapt reference code to current project.
- kotlin-implementer (sonnet): Implement in Clean Architecture layers.
- qa-verifier (sonnet): Test and verify parity.

Rules:
- Read reference implementations first. Identify exact differences before changing code.
- Algorithm updates: diff current vs reference, apply only the differences.
- Elder impulse: debug first, understand root cause, then fix.
- Real-time: use current API approach. Only add new API calls specifically needed for real-time data.
- Do NOT modify any other features or APIs.
- After every code change: run gradlew assembleDebug.
- Log all changes with before/after diff to PROGRESS.md.

Workflow:
1. Lead reads TASK.md, picks next task.
2. Algorithm tasks: feature-extractor analyzes reference, Integrator diffs and updates, Architect approves changes.
3. Bug fix: debug trace first, document cause, then fix.
4. Real-time: Architect approves API and data flow plan before implementation.
5. QA verifies each change: build passes, output matches reference.
6. Lead marks task done after verification.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- Supply-demand oscillator matches reference algorithm exactly.
- Trend signal matches reference algorithm exactly.
- Elder impulse bug resolved.
- Real-time intraday feature working with minimal new API usage.
- No other features changed.
- gradlew assembleDebug passes.
- gradlew test passes.
- CHANGE_LOG.md generated with all changes (before/after).
- CLAUDE.md updated.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.