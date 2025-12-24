---
name: android-feature-planner
description: Creates phase-based Android app feature plans with quality gates and incremental delivery structure. Use when planning Android features, organizing work, breaking down tasks, creating roadmaps, or structuring development strategy for Kotlin/Jetpack Compose applications. Keywords: android, plan, planning, phases, breakdown, strategy, roadmap, organize, structure, outline, kotlin, compose, jetpack.
---

# Android Feature Planner

## Purpose
Generate structured, phase-based plans for Android app development where:
- Each phase delivers complete, runnable functionality
- Quality gates enforce validation before proceeding (build, lint, tests)
- User approves plan before any work begins
- Progress tracked via markdown checkboxes
- Each phase is 1-4 hours maximum
- Follows Clean Architecture and MVVM/MVI patterns

## Planning Workflow

### Step 1: Requirements Analysis
1. Read relevant files to understand codebase architecture
2. Identify Android-specific dependencies and integration points:
   - UI Layer: Composables, Screens, Navigation
   - Domain Layer: UseCases, Models
   - Data Layer: Repositories, DataSources, Room entities
3. Assess complexity and risks (API changes, database migrations, etc.)
4. Determine appropriate scope (small/medium/large)

### Step 2: Phase Breakdown with TDD Integration
Break feature into 3-7 phases where each phase:
- **Test-First**: Write tests BEFORE implementation
- Delivers working, testable functionality
- Takes 1-4 hours maximum
- Follows Red-Green-Refactor cycle
- Has measurable test coverage requirements
- Can be rolled back independently (via Git)
- Has clear success criteria

**Phase Structure**:
- Phase Name: Clear deliverable (e.g., "Domain Models & Repository Interface")
- Goal: What working functionality this produces
- **Test Strategy**: What test types (Unit/Instrumented/UI), coverage target, test scenarios
- Tasks (ordered by TDD workflow):
  1. **RED Tasks**: Write failing tests first
  2. **GREEN Tasks**: Implement minimal code to make tests pass
  3. **REFACTOR Tasks**: Improve code quality while tests stay green
- Quality Gate: TDD compliance + Android-specific validation criteria
- Dependencies: What must exist before starting
- **Coverage Target**: Specific percentage or checklist for this phase

### Step 3: Plan Document Creation
Use plan-template-android.md to generate: `docs/plans/PLAN_<feature-name>.md`

Include:
- Overview and objectives
- Architecture decisions with rationale (Clean Architecture layers)
- Complete phase breakdown with checkboxes
- Quality gate checklists (Gradle, Lint, Detekt, Tests)
- Risk assessment table
- Rollback strategy per phase (Git-based)
- Progress tracking section
- Notes & learnings area

### Step 4: User Approval
**CRITICAL**: Use AskUserQuestion to get explicit approval before proceeding.

Ask:
- "Does this phase breakdown make sense for your Android project?"
- "Any concerns about the proposed architecture approach?"
- "Should I proceed with creating the plan document?"

Only create plan document after user confirms approval.

### Step 5: Document Generation
1. Create `docs/plans/` directory if not exists
2. Generate plan document with all checkboxes unchecked
3. Add clear instructions in header about quality gates
4. Inform user of plan location and next steps

## Quality Gate Standards

Each phase MUST validate these items before proceeding to next phase:

**Build & Compilation**:
- [ ] `./gradlew assembleDebug` completes without errors
- [ ] No Kotlin compilation errors
- [ ] No resource errors (layouts, drawables, strings)

**Test-Driven Development (TDD)**:
- [ ] Tests written BEFORE production code
- [ ] Red-Green-Refactor cycle followed
- [ ] Unit tests: ≥80% coverage for business logic (ViewModel, UseCase, Repository)
- [ ] UI tests: Critical user flows validated (Compose Testing)
- [ ] Test suite runs in acceptable time (<3 minutes for unit tests)

**Testing**:
- [ ] `./gradlew testDebugUnitTest` - All unit tests pass
- [ ] `./gradlew connectedDebugAndroidTest` - All instrumented tests pass (if applicable)
- [ ] No flaky tests (run 3+ times)
- [ ] Test coverage maintained or improved

**Code Quality**:
- [ ] `./gradlew lint` - No errors, warnings reviewed
- [ ] `./gradlew detekt` - Static analysis passes (if configured)
- [ ] `./gradlew spotlessCheck` - Code formatting consistent (if configured)
- [ ] Kotlin conventions followed

**Functionality**:
- [ ] Manual testing on emulator/device confirms feature works
- [ ] No regressions in existing functionality
- [ ] Edge cases tested (empty states, error states, loading states)
- [ ] Configuration changes handled (rotation, dark mode)

**Security & Performance**:
- [ ] No hardcoded secrets or API keys
- [ ] ProGuard/R8 rules updated if needed
- [ ] No memory leaks (check with LeakCanary if configured)
- [ ] No ANR risks (heavy work off main thread)
- [ ] No excessive battery/network usage

**Documentation**:
- [ ] KDoc comments for public APIs
- [ ] README updated if architecture changes
- [ ] CHANGELOG updated

## Progress Tracking Protocol

Add this to plan document header:

```markdown
**CRITICAL INSTRUCTIONS**: After completing each phase:
1. ✅ Check off completed task checkboxes
2. 🧪 Run all quality gate validation commands
3. ⚠️ Verify ALL quality gate items pass
4. 📅 Update "Last Updated" date
5. 📝 Document learnings in Notes section
6. ➡️ Only then proceed to next phase

⛔ DO NOT skip quality gates or proceed with failing checks
```

## Android-Specific Phase Sizing Guidelines

**Small Scope** (2-3 phases, 3-6 hours total):
- Single screen or simple feature
- Minimal data layer changes
- No new dependencies
- Example: Add settings toggle, create new dialog, simple list screen

**Medium Scope** (4-5 phases, 8-15 hours total):
- Multiple screens or moderate feature
- Room database changes or API integration
- New Jetpack components (Navigation, WorkManager)
- Example: User profile feature, search with filters, offline caching

**Large Scope** (6-7 phases, 15-25 hours total):
- Complex feature spanning multiple layers
- Significant architectural changes
- Multiple integrations (API + Database + Background work)
- Example: Real-time sync feature, complex dashboard, multi-step wizard

## Risk Assessment

Identify and document:
- **Technical Risks**: API breaking changes, Compose compatibility, library updates
- **Dependency Risks**: Third-party SDK updates, Play Services requirements
- **Timeline Risks**: Complexity unknowns, emulator/device testing time
- **Quality Risks**: Test coverage gaps, UI regression potential
- **Platform Risks**: Min SDK support, device fragmentation

For each risk, specify:
- Probability: Low/Medium/High
- Impact: Low/Medium/High
- Mitigation Strategy: Specific action steps

## Rollback Strategy

For each phase, document how to revert changes if issues arise.
Consider:
- Git commands to revert commits
- Room database migration rollback (if applicable)
- Feature flags to disable functionality
- Gradle dependency version rollback
- Compose state restoration issues

## Test Specification Guidelines

### Test-First Development Workflow

**For Each Feature Component**:
1. **Specify Test Cases** (before writing ANY code)
   - What inputs will be tested?
   - What outputs are expected?
   - What edge cases must be handled?
   - What error conditions should be tested?

2. **Write Tests** (Red Phase)
   - Write tests that WILL fail
   - Verify tests fail for the right reason
   - Run tests to confirm failure
   - Commit failing tests to track TDD compliance

3. **Implement Code** (Green Phase)
   - Write minimal code to make tests pass
   - Run tests frequently (every 2-5 minutes)
   - Stop when all tests pass
   - No additional functionality beyond tests

4. **Refactor** (Blue Phase)
   - Improve code quality while tests remain green
   - Extract duplicated logic
   - Improve naming and structure
   - Run tests after each refactoring step
   - Commit when refactoring complete

### Android Test Types

**Unit Tests** (`src/test/`):
- **Target**: ViewModels, UseCases, Repositories, Mappers, Utilities
- **Framework**: JUnit 5, MockK, Turbine (for Flow), Kotest
- **Dependencies**: Mocked using MockK or Fake implementations
- **Speed**: Fast (<100ms per test)
- **Isolation**: Complete isolation from Android framework
- **Coverage**: ≥80% of business logic

**Integration Tests** (`src/test/` or `src/androidTest/`):
- **Target**: Repository + DataSource, ViewModel + UseCase
- **Framework**: JUnit 5, Robolectric (for Android without emulator)
- **Dependencies**: Real Room database (in-memory), Fake API responses
- **Speed**: Moderate (<1s per test)
- **Isolation**: Tests component boundaries
- **Coverage**: Critical integration points

**UI/Instrumented Tests** (`src/androidTest/`):
- **Target**: Composables, Screens, Navigation flows
- **Framework**: Compose Testing, Espresso (for Views), UI Automator
- **Dependencies**: Real or mock ViewModel, Test dispatchers
- **Speed**: Slow (seconds per test)
- **Isolation**: Full system integration or isolated Composable
- **Coverage**: Critical user journeys

### Test Coverage Calculation

**Coverage Thresholds**:
- **ViewModel/UseCase**: ≥90% (critical business logic)
- **Repository**: ≥80% (data access layer)
- **Mapper/Utility**: ≥90% (pure functions)
- **Composable UI**: UI tests preferred over coverage metrics

**Coverage Commands**:
```bash
# Generate coverage report
./gradlew testDebugUnitTestCoverage

# View coverage report
open app/build/reports/jacoco/testDebugUnitTestCoverage/html/index.html

# With Kover (Kotlin-native coverage)
./gradlew koverHtmlReport
open app/build/reports/kover/html/index.html
```

### Common Android Test Patterns

**ViewModel Testing with Turbine**:
```kotlin
@Test
fun `when loadData called, should emit loading then success`() = runTest {
    // Arrange
    val useCase = mockk<GetDataUseCase>()
    coEvery { useCase() } returns flowOf(Result.success(testData))
    val viewModel = MyViewModel(useCase)

    // Act & Assert
    viewModel.uiState.test {
        viewModel.loadData()

        assertThat(awaitItem()).isEqualTo(UiState.Loading)
        assertThat(awaitItem()).isEqualTo(UiState.Success(testData))
        cancelAndIgnoreRemainingEvents()
    }
}
```

**Repository Testing with Fake DataSource**:
```kotlin
@Test
fun `when fetchItems called, should return cached data first`() = runTest {
    // Arrange
    val fakeLocalDataSource = FakeLocalDataSource(cachedItems)
    val fakeRemoteDataSource = FakeRemoteDataSource(remoteItems)
    val repository = ItemRepository(fakeLocalDataSource, fakeRemoteDataSource)

    // Act
    val result = repository.fetchItems().first()

    // Assert
    assertThat(result).isEqualTo(cachedItems)
}
```

**Compose UI Testing**:
```kotlin
@Test
fun whenButtonClicked_shouldShowDialog() {
    // Arrange
    composeTestRule.setContent {
        MyScreen(onButtonClick = {})
    }

    // Act
    composeTestRule.onNodeWithText("Click Me").performClick()

    // Assert
    composeTestRule.onNodeWithText("Dialog Title").assertIsDisplayed()
}
```

**Mocking with MockK**:
```kotlin
@Test
fun `when repository fails, should emit error state`() = runTest {
    // Create mock
    val repository = mockk<ItemRepository>()
    coEvery { repository.getItems() } throws IOException("Network error")

    // Create ViewModel with mock
    val viewModel = ItemViewModel(repository)

    // Execute and verify
    viewModel.loadItems()
    assertThat(viewModel.uiState.value).isInstanceOf(UiState.Error::class.java)
}
```

### Test Documentation in Plan

**In each phase, specify**:
1. **Test File Location**: Exact path (e.g., `app/src/test/java/com/example/feature/`)
2. **Test Scenarios**: List of specific test cases
3. **Expected Failures**: What error should tests show initially?
4. **Coverage Target**: Percentage for this phase
5. **Dependencies to Mock**: What needs MockK mocking?
6. **Test Data**: What fixtures/factories are needed?
7. **Dispatcher Handling**: How to handle coroutine dispatchers in tests?

## Android Architecture Reference

### Clean Architecture Layers

```
📁 app/src/main/java/com/example/
├── 📁 core/                    # Shared utilities
│   ├── 📁 common/              # Common extensions, utilities
│   ├── 📁 di/                  # Hilt modules
│   └── 📁 ui/                  # Common UI components
│
├── 📁 feature/                 # Feature modules
│   └── 📁 [feature_name]/
│       ├── 📁 data/            # Data layer
│       │   ├── 📁 local/       # Room DAOs, entities
│       │   ├── 📁 remote/      # API services, DTOs
│       │   ├── 📁 mapper/      # Data <-> Domain mappers
│       │   └── 📁 repository/  # Repository implementations
│       │
│       ├── 📁 domain/          # Domain layer
│       │   ├── 📁 model/       # Domain models
│       │   ├── 📁 repository/  # Repository interfaces
│       │   └── 📁 usecase/     # Use cases
│       │
│       └── 📁 presentation/    # Presentation layer
│           ├── 📁 component/   # Reusable Composables
│           ├── 📁 screen/      # Screen Composables
│           ├── 📁 viewmodel/   # ViewModels
│           └── 📁 state/       # UI State, Events, Effects
│
📁 app/src/test/                # Unit tests
│   └── java/com/example/
│       └── 📁 feature/
│           └── 📁 [feature_name]/
│               ├── 📁 data/
│               ├── 📁 domain/
│               └── 📁 presentation/
│
📁 app/src/androidTest/         # Instrumented tests
    └── java/com/example/
        └── 📁 feature/
            └── 📁 [feature_name]/
                └── 📁 ui/
```

### Key Dependencies for Testing

```kotlin
// build.gradle.kts (Module)
dependencies {
    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11")

    // Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
}
```

## Supporting Files Reference
- [plan-template-android.md](plan-template-android.md) - Complete Android plan document template
