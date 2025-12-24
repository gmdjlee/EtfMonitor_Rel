# Implementation Plan: [Feature Name]

**Status**: 🔄 In Progress
**Started**: YYYY-MM-DD
**Last Updated**: YYYY-MM-DD
**Estimated Completion**: YYYY-MM-DD
**Target Module**: `app` | `feature:[module_name]`
**Min SDK**: XX | **Target SDK**: XX

---

**⚠️ CRITICAL INSTRUCTIONS**: After completing each phase:
1. ✅ Check off completed task checkboxes
2. 🧪 Run all quality gate validation commands
3. ⚠️ Verify ALL quality gate items pass
4. 📅 Update "Last Updated" date above
5. 📝 Document learnings in Notes section
6. ➡️ Only then proceed to next phase

⛔ **DO NOT skip quality gates or proceed with failing checks**

---

## 📋 Overview

### Feature Description
[What this feature does and why it's needed]

### Success Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

### User Impact
[How this benefits users or improves the product]

### Affected Screens/Components
- [ ] Screen/Component 1
- [ ] Screen/Component 2

---

## 🏗️ Architecture Decisions

| Decision | Rationale | Trade-offs |
|----------|-----------|------------|
| [Architecture Layer: e.g., MVVM with Clean Architecture] | [Why this approach] | [What we're giving up] |
| [State Management: e.g., StateFlow + UiState sealed class] | [Why this approach] | [What we're giving up] |
| [DI Strategy: e.g., Hilt with @HiltViewModel] | [Why this approach] | [What we're giving up] |
| [Navigation: e.g., Compose Navigation] | [Why this approach] | [What we're giving up] |

### Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                      │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │  Composable │───▶│  ViewModel  │───▶│  UiState/Event  │  │
│  └─────────────┘    └──────┬──────┘    └─────────────────┘  │
│                            │                                 │
├────────────────────────────┼────────────────────────────────┤
│                      Domain Layer                            │
│                     ┌──────▼──────┐                         │
│                     │   UseCase   │                         │
│                     └──────┬──────┘                         │
│                            │                                 │
│              ┌─────────────┴─────────────┐                  │
│              │   Repository Interface    │                  │
│              └───────────────────────────┘                  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Repository Implementation                 │  │
│  └────────────────────┬──────────────────┬───────────────┘  │
│                       │                  │                   │
│              ┌────────▼──────┐  ┌────────▼──────┐          │
│              │ LocalDataSource│  │RemoteDataSource│          │
│              │    (Room)     │  │  (Retrofit)   │          │
│              └───────────────┘  └───────────────┘          │
└──────────────────────────────────────────────────────────────┘
```

---

## 📦 Dependencies

### Required Before Starting
- [ ] Dependency 1: [Description]
- [ ] Dependency 2: [Description]

### External Dependencies (build.gradle.kts)
```kotlin
dependencies {
    // Core
    implementation("androidx.core:core-ktx:X.X.X")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:X.X.X")

    // Compose
    implementation(platform("androidx.compose:compose-bom:YYYY.MM.XX"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:X.X.X")
    implementation("androidx.hilt:hilt-navigation-compose:X.X.X")
    implementation("com.google.dagger:hilt-android:X.X.X")

    // [Add feature-specific dependencies]
}
```

### New Dependencies to Add
- [ ] Package 1: version X.Y.Z - [Purpose]
- [ ] Package 2: version X.Y.Z - [Purpose]

---

## 🧪 Test Strategy

### Testing Approach
**TDD Principle**: Write tests FIRST, then implement to make them pass

### Test Pyramid for This Feature
| Test Type | Coverage Target | Purpose | Location |
|-----------|-----------------|---------|----------|
| **Unit Tests** | ≥80% | ViewModel, UseCase, Repository logic | `src/test/` |
| **Integration Tests** | Critical paths | Repository + DataSource, Room queries | `src/test/` or `src/androidTest/` |
| **UI Tests** | Key user flows | Compose UI, Navigation | `src/androidTest/` |

### Test File Organization
```
app/src/
├── test/java/com/example/[feature]/
│   ├── data/
│   │   ├── repository/
│   │   │   └── [Feature]RepositoryTest.kt
│   │   └── mapper/
│   │       └── [Feature]MapperTest.kt
│   ├── domain/
│   │   └── usecase/
│   │       └── [Feature]UseCaseTest.kt
│   └── presentation/
│       └── viewmodel/
│           └── [Feature]ViewModelTest.kt
│
└── androidTest/java/com/example/[feature]/
    └── ui/
        ├── [Feature]ScreenTest.kt
        └── [Feature]NavigationTest.kt
```

### Coverage Requirements by Phase
- **Phase 1 (Domain)**: Unit tests for models, repository interface (≥90%)
- **Phase 2 (Data)**: Repository + DataSource tests (≥80%)
- **Phase 3 (Presentation)**: ViewModel tests with Turbine (≥80%)
- **Phase 4 (UI)**: Compose UI tests (critical paths)

### Test Naming Convention
```kotlin
// ViewModel Tests
@Test
fun `when [action] then [expected result]`() { }

// UseCase Tests
@Test
fun `invoke with [condition] returns [expected]`() { }

// Repository Tests
@Test
fun `[method] when [condition] should [behavior]`() { }

// UI Tests
@Test
fun [componentName]_[action]_[expectedBehavior]() { }
```

---

## 🚀 Implementation Phases

### Phase 1: Domain Layer - Models & Repository Interface
**Goal**: Define domain models and repository contract
**Estimated Time**: X hours
**Status**: ⏳ Pending | 🔄 In Progress | ✅ Complete

#### Tasks

**🔴 RED: Write Failing Tests First**
- [ ] **Test 1.1**: Write unit tests for domain model validation
  - File: `app/src/test/java/com/example/[feature]/domain/model/[Model]Test.kt`
  - Expected: Tests FAIL because model doesn't exist
  - Test Cases:
    - [ ] Model creation with valid data
    - [ ] Model equality and hashCode
    - [ ] Edge cases (empty values, null handling)

- [ ] **Test 1.2**: Write repository interface contract tests
  - File: `app/src/test/java/com/example/[feature]/domain/repository/[Feature]RepositoryContractTest.kt`
  - Expected: Tests FAIL because repository doesn't exist
  - Test Cases:
    - [ ] Successful data retrieval flow
    - [ ] Error handling scenarios
    - [ ] Empty result handling

**🟢 GREEN: Implement to Make Tests Pass**
- [ ] **Task 1.3**: Create domain models
  - File: `app/src/main/java/com/example/[feature]/domain/model/[Model].kt`
  - Goal: Make Test 1.1 pass with minimal code
  - Details: Data class with required fields, validation logic

- [ ] **Task 1.4**: Define repository interface
  - File: `app/src/main/java/com/example/[feature]/domain/repository/[Feature]Repository.kt`
  - Goal: Define contract for data layer
  - Details: Interface with suspend functions returning Flow/Result

**🔵 REFACTOR: Clean Up Code**
- [ ] **Task 1.5**: Refactor for code quality
  - Files: Review all new code in this phase
  - Goal: Improve design without breaking tests
  - Checklist:
    - [ ] KDoc comments added
    - [ ] Kotlin idioms applied (data class, sealed class)
    - [ ] Naming follows Android conventions
    - [ ] No code duplication

#### Quality Gate ✋

**⚠️ STOP: Do NOT proceed to Phase 2 until ALL checks pass**

**TDD Compliance** (CRITICAL):
- [ ] Tests written FIRST and initially failed
- [ ] Production code written to make tests pass
- [ ] Code improved while tests still pass
- [ ] Coverage: ≥90% for domain models

**Validation Commands**:
```bash
# Build Check
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Run Specific Tests
./gradlew testDebugUnitTest --tests "com.example.[feature].domain.*"

# Coverage Report
./gradlew testDebugUnitTestCoverage
# or with Kover
./gradlew koverHtmlReportDebug

# Lint Check
./gradlew lint

# Code Formatting (if using Spotless)
./gradlew spotlessCheck

# Static Analysis (if using Detekt)
./gradlew detekt
```

**Build & Tests**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew testDebugUnitTest` - All tests pass
- [ ] No flaky tests (run 3+ times)

**Code Quality**:
- [ ] `./gradlew lint` - No errors
- [ ] `./gradlew detekt` - Passes (if configured)
- [ ] `./gradlew spotlessCheck` - Passes (if configured)
- [ ] Kotlin conventions followed

**Manual Review**:
- [ ] Domain models correctly represent business entities
- [ ] Repository interface is testable (no Android framework dependencies)
- [ ] Sealed classes used for Result/State where appropriate

---

### Phase 2: Data Layer - Repository Implementation & DataSource
**Goal**: Implement data persistence/retrieval logic
**Estimated Time**: X hours
**Status**: ⏳ Pending | 🔄 In Progress | ✅ Complete

#### Tasks

**🔴 RED: Write Failing Tests First**
- [ ] **Test 2.1**: Write repository implementation tests
  - File: `app/src/test/java/com/example/[feature]/data/repository/[Feature]RepositoryImplTest.kt`
  - Expected: Tests FAIL because implementation doesn't exist
  - Test Cases:
    - [ ] Data mapping from DTO/Entity to Domain model
    - [ ] Cache-first strategy (if applicable)
    - [ ] Error propagation from DataSource
    - [ ] Empty result handling
  - Mocking: Use MockK for DataSource dependencies

- [ ] **Test 2.2**: Write mapper tests
  - File: `app/src/test/java/com/example/[feature]/data/mapper/[Feature]MapperTest.kt`
  - Expected: Tests FAIL because mapper doesn't exist
  - Test Cases:
    - [ ] Entity to Domain mapping
    - [ ] DTO to Entity mapping
    - [ ] Null/edge case handling

- [ ] **Test 2.3**: Write Room DAO tests (if applicable)
  - File: `app/src/androidTest/java/com/example/[feature]/data/local/[Feature]DaoTest.kt`
  - Expected: Tests FAIL because DAO doesn't exist
  - Test Cases:
    - [ ] Insert and retrieve
    - [ ] Update and delete
    - [ ] Query with filters
    - [ ] Flow emission on data change

**🟢 GREEN: Implement to Make Tests Pass**
- [ ] **Task 2.4**: Create data models (Entity/DTO)
  - Files:
    - `app/src/main/java/com/example/[feature]/data/local/entity/[Feature]Entity.kt`
    - `app/src/main/java/com/example/[feature]/data/remote/dto/[Feature]Dto.kt`
  - Goal: Data layer models with Room/Retrofit annotations

- [ ] **Task 2.5**: Implement mappers
  - File: `app/src/main/java/com/example/[feature]/data/mapper/[Feature]Mapper.kt`
  - Goal: Make Test 2.2 pass

- [ ] **Task 2.6**: Implement Room DAO (if applicable)
  - File: `app/src/main/java/com/example/[feature]/data/local/dao/[Feature]Dao.kt`
  - Goal: Make Test 2.3 pass

- [ ] **Task 2.7**: Implement repository
  - File: `app/src/main/java/com/example/[feature]/data/repository/[Feature]RepositoryImpl.kt`
  - Goal: Make Test 2.1 pass with minimal code

**🔵 REFACTOR: Clean Up Code**
- [ ] **Task 2.8**: Refactor for code quality
  - Checklist:
    - [ ] Extension functions for mapping
    - [ ] Proper error handling with Result/Either
    - [ ] Coroutine dispatcher injection
    - [ ] No memory leaks in Flow operations

#### Quality Gate ✋

**⚠️ STOP: Do NOT proceed to Phase 3 until ALL checks pass**

**TDD Compliance**:
- [ ] Tests written FIRST and initially failed
- [ ] Production code written to make tests pass
- [ ] Coverage: ≥80% for repository and mappers

**Validation Commands**:
```bash
# Run All Unit Tests
./gradlew testDebugUnitTest

# Run Instrumented Tests (for Room)
./gradlew connectedDebugAndroidTest --tests "com.example.[feature].data.local.*"

# Full Build with Tests
./gradlew build

# Lint & Static Analysis
./gradlew lint detekt spotlessCheck
```

**Build & Tests**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew testDebugUnitTest` - All tests pass
- [ ] `./gradlew connectedDebugAndroidTest` - Room tests pass (if applicable)

**Code Quality**:
- [ ] No lint errors
- [ ] Proper use of Kotlin coroutines
- [ ] Thread safety considered

**Integration Verification**:
- [ ] Repository correctly integrates with DataSources
- [ ] Data flows correctly from API/DB to Domain layer
- [ ] Error states propagate correctly

---

### Phase 3: Presentation Layer - ViewModel & State Management
**Goal**: Implement UI logic with reactive state management
**Estimated Time**: X hours
**Status**: ⏳ Pending | 🔄 In Progress | ✅ Complete

#### Tasks

**🔴 RED: Write Failing Tests First**
- [ ] **Test 3.1**: Write ViewModel tests with Turbine
  - File: `app/src/test/java/com/example/[feature]/presentation/viewmodel/[Feature]ViewModelTest.kt`
  - Expected: Tests FAIL because ViewModel doesn't exist
  - Test Cases:
    - [ ] Initial state is correct
    - [ ] Loading state emitted on action
    - [ ] Success state with data
    - [ ] Error state handling
    - [ ] Retry functionality
  - Mocking: MockK for UseCase/Repository

- [ ] **Test 3.2**: Write UseCase tests (if applicable)
  - File: `app/src/test/java/com/example/[feature]/domain/usecase/[Feature]UseCaseTest.kt`
  - Expected: Tests FAIL because UseCase doesn't exist
  - Test Cases:
    - [ ] Business logic validation
    - [ ] Data transformation
    - [ ] Error handling

**🟢 GREEN: Implement to Make Tests Pass**
- [ ] **Task 3.3**: Define UI State sealed class
  - File: `app/src/main/java/com/example/[feature]/presentation/state/[Feature]UiState.kt`
  - Goal: Define all possible UI states

- [ ] **Task 3.4**: Implement UseCase (if applicable)
  - File: `app/src/main/java/com/example/[feature]/domain/usecase/[Feature]UseCase.kt`
  - Goal: Make Test 3.2 pass

- [ ] **Task 3.5**: Implement ViewModel
  - File: `app/src/main/java/com/example/[feature]/presentation/viewmodel/[Feature]ViewModel.kt`
  - Goal: Make Test 3.1 pass with minimal code
  - Details: @HiltViewModel, StateFlow for state, proper lifecycle handling

**🔵 REFACTOR: Clean Up Code**
- [ ] **Task 3.6**: Refactor for code quality
  - Checklist:
    - [ ] Single responsibility for ViewModel methods
    - [ ] Proper coroutine scope usage (viewModelScope)
    - [ ] State immutability ensured
    - [ ] Events handled properly (one-time events via Channel/SharedFlow)

#### Quality Gate ✋

**⚠️ STOP: Do NOT proceed to Phase 4 until ALL checks pass**

**TDD Compliance**:
- [ ] Tests written FIRST and initially failed
- [ ] Turbine used for Flow testing
- [ ] Coverage: ≥80% for ViewModel

**Validation Commands**:
```bash
# Run ViewModel Tests
./gradlew testDebugUnitTest --tests "com.example.[feature].presentation.viewmodel.*"

# Run All Unit Tests with Coverage
./gradlew testDebugUnitTestCoverage

# Check for Memory Leaks (manual)
# Run app with LeakCanary and navigate through feature
```

**Build & Tests**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew testDebugUnitTest` - All tests pass
- [ ] No coroutine leaks (viewModelScope properly used)

**State Management Verification**:
- [ ] All UI states defined in sealed class
- [ ] State transitions are predictable
- [ ] No duplicate state emissions
- [ ] Loading/Error/Success states handled

---

### Phase 4: UI Layer - Compose Screens & Navigation
**Goal**: Implement user interface with Jetpack Compose
**Estimated Time**: X hours
**Status**: ⏳ Pending | 🔄 In Progress | ✅ Complete

#### Tasks

**🔴 RED: Write Failing Tests First**
- [ ] **Test 4.1**: Write Compose UI tests
  - File: `app/src/androidTest/java/com/example/[feature]/ui/[Feature]ScreenTest.kt`
  - Expected: Tests FAIL because Screen doesn't exist
  - Test Cases:
    - [ ] Loading state displays correctly
    - [ ] Success state renders data
    - [ ] Error state shows message and retry
    - [ ] User interactions trigger callbacks
    - [ ] Empty state displays correctly

- [ ] **Test 4.2**: Write Navigation tests (if applicable)
  - File: `app/src/androidTest/java/com/example/[feature]/ui/[Feature]NavigationTest.kt`
  - Expected: Tests FAIL because navigation not set up
  - Test Cases:
    - [ ] Navigation to feature screen
    - [ ] Back navigation works
    - [ ] Deep link handling (if applicable)

**🟢 GREEN: Implement to Make Tests Pass**
- [ ] **Task 4.3**: Create reusable Composable components
  - Files: `app/src/main/java/com/example/[feature]/presentation/component/`
  - Goal: Build UI building blocks

- [ ] **Task 4.4**: Implement main Screen Composable
  - File: `app/src/main/java/com/example/[feature]/presentation/screen/[Feature]Screen.kt`
  - Goal: Make Test 4.1 pass
  - Details: Stateless composable with state hoisting

- [ ] **Task 4.5**: Integrate with Navigation
  - File: `app/src/main/java/com/example/navigation/[Feature]Navigation.kt`
  - Goal: Make Test 4.2 pass
  - Details: NavGraphBuilder extension, route definition

- [ ] **Task 4.6**: Wire ViewModel to Screen
  - File: Update Screen with hiltViewModel()
  - Goal: Full integration working

**🔵 REFACTOR: Clean Up Code**
- [ ] **Task 4.7**: Refactor for code quality
  - Checklist:
    - [ ] Preview functions added (@Preview)
    - [ ] Theme/styling consistent with design system
    - [ ] Accessibility (contentDescription, semantics)
    - [ ] State hoisting properly implemented
    - [ ] Recomposition optimized (remember, derivedStateOf)

#### Quality Gate ✋

**⚠️ STOP: Do NOT mark feature complete until ALL checks pass**

**TDD Compliance**:
- [ ] UI tests written FIRST
- [ ] Tests cover critical user flows
- [ ] All UI states tested

**Validation Commands**:
```bash
# Run UI Tests
./gradlew connectedDebugAndroidTest --tests "com.example.[feature].ui.*"

# Run All Tests
./gradlew testDebugUnitTest connectedDebugAndroidTest

# Generate Full Coverage Report
./gradlew jacocoTestReport
# or
./gradlew koverMergedHtmlReport

# Build Release Variant
./gradlew assembleRelease

# Full Quality Check
./gradlew build lint detekt spotlessCheck
```

**Build & Tests**:
- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew assembleRelease` succeeds (ProGuard rules correct)
- [ ] `./gradlew connectedDebugAndroidTest` - All UI tests pass
- [ ] Tests pass on multiple device configurations

**UI/UX Verification**:
- [ ] All states render correctly (Loading, Success, Error, Empty)
- [ ] Animations smooth (no jank)
- [ ] Dark mode works correctly
- [ ] Configuration changes handled (rotation)
- [ ] Keyboard handling correct
- [ ] Accessibility labels present

**Manual Test Checklist**:
- [ ] Test on physical device
- [ ] Test on emulator with different screen sizes
- [ ] Test with slow network (use Network Profiler)
- [ ] Test with no network
- [ ] Test process death recovery
- [ ] Test with TalkBack enabled

---

## ⚠️ Risk Assessment

| Risk | Probability | Impact | Mitigation Strategy |
|------|-------------|--------|---------------------|
| Compose version compatibility | Low | High | Pin BOM version, test before updates |
| Memory leaks in Composables | Medium | High | Use remember properly, test with LeakCanary |
| ProGuard stripping required code | Medium | Medium | Add keep rules, test release build |
| Room migration failures | Low | High | Write migration tests, backup strategy |
| API breaking changes | Medium | Medium | Version API, use DTOs |

---

## 🔄 Rollback Strategy

### If Phase 1 Fails
**Git Rollback**:
```bash
git log --oneline -5  # Find commit before Phase 1
git revert HEAD~N..HEAD  # Revert N commits
```
**Files to remove**: Domain models, repository interface

### If Phase 2 Fails
**Git Rollback**:
```bash
git revert HEAD~N..HEAD  # Revert to Phase 1 complete
```
**Database migration**:
- [ ] Remove Room entities from database version
- [ ] Update schema version
- [ ] Test fresh install

### If Phase 3 Fails
**Git Rollback**:
```bash
git revert HEAD~N..HEAD  # Revert to Phase 2 complete
```
**Cleanup**:
- [ ] Remove Hilt ViewModel modules
- [ ] Remove state classes

### If Phase 4 Fails
**Git Rollback**:
```bash
git revert HEAD~N..HEAD  # Revert to Phase 3 complete
```
**Cleanup**:
- [ ] Remove navigation route
- [ ] Remove Composables
- [ ] Update NavHost

---

## 📊 Progress Tracking

### Completion Status
- **Phase 1 (Domain)**: ⏳ 0% | 🔄 50% | ✅ 100%
- **Phase 2 (Data)**: ⏳ 0% | 🔄 50% | ✅ 100%
- **Phase 3 (Presentation)**: ⏳ 0% | 🔄 50% | ✅ 100%
- **Phase 4 (UI)**: ⏳ 0% | 🔄 50% | ✅ 100%

**Overall Progress**: X% complete

### Time Tracking
| Phase | Estimated | Actual | Variance |
|-------|-----------|--------|----------|
| Phase 1 (Domain) | X hours | - | - |
| Phase 2 (Data) | X hours | - | - |
| Phase 3 (Presentation) | X hours | - | - |
| Phase 4 (UI) | X hours | - | - |
| **Total** | X hours | - | - |

### Test Coverage Tracking
| Layer | Target | Actual | Status |
|-------|--------|--------|--------|
| Domain | ≥90% | -% | ⏳ |
| Data | ≥80% | -% | ⏳ |
| ViewModel | ≥80% | -% | ⏳ |
| UI | Critical paths | - | ⏳ |

---

## 📝 Notes & Learnings

### Implementation Notes
- [Add insights discovered during implementation]
- [Document decisions that deviate from original plan]
- [Record helpful debugging discoveries]

### Compose Tips Discovered
- [Compose-specific learnings]
- [Performance optimizations found]

### Blockers Encountered
- **Blocker 1**: [Description] → [Resolution]
- **Blocker 2**: [Description] → [Resolution]

### Improvements for Future Plans
- [What would you do differently next time?]
- [What worked particularly well?]

---

## 📚 References

### Documentation
- [Android Developers - Compose](https://developer.android.com/jetpack/compose)
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Testing in Compose](https://developer.android.com/jetpack/compose/testing)

### Related Issues
- Issue #X: [Description]
- PR #Y: [Description]

### Design Resources
- [Figma/Design link if available]

---

## ✅ Final Checklist

**Before marking plan as COMPLETE**:
- [ ] All phases completed with quality gates passed
- [ ] Full integration testing performed
- [ ] Test coverage meets targets
- [ ] `./gradlew build` passes completely
- [ ] Release build tested (`./gradlew assembleRelease`)
- [ ] Documentation updated
- [ ] No lint errors or warnings
- [ ] Performance verified (no jank, memory leaks)
- [ ] Accessibility verified (TalkBack tested)
- [ ] Dark mode verified
- [ ] Different screen sizes tested
- [ ] Process death recovery verified
- [ ] All stakeholders notified
- [ ] Plan document archived for future reference

---

## 📖 TDD Example for Android

### Example: Adding User Profile Feature

**Phase 1: RED (Write Failing ViewModel Test)**

```kotlin
// src/test/java/.../ProfileViewModelTest.kt
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getUserUseCase = mockk<GetUserProfileUseCase>()

    @Test
    fun `when loadProfile called, should emit loading then success`() = runTest {
        // Arrange
        coEvery { getUserUseCase("user123") } returns flowOf(
            Result.success(testUser)
        )
        val viewModel = ProfileViewModel(getUserUseCase)

        // Act & Assert
        viewModel.uiState.test {
            // Initial state
            assertThat(awaitItem()).isEqualTo(ProfileUiState.Initial)

            // Trigger load
            viewModel.loadProfile("user123")

            // Loading state
            assertThat(awaitItem()).isEqualTo(ProfileUiState.Loading)

            // Success state
            val successState = awaitItem()
            assertThat(successState).isInstanceOf(ProfileUiState.Success::class.java)
            assertThat((successState as ProfileUiState.Success).user).isEqualTo(testUser)

            cancelAndIgnoreRemainingEvents()
        }
    }
    // TEST FAILS - ProfileViewModel doesn't exist yet
}
```

**Phase 2: GREEN (Minimal ViewModel Implementation)**

```kotlin
// src/main/java/.../ProfileViewModel.kt
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getUserUseCase: GetUserProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Initial)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            getUserUseCase(userId)
                .collect { result ->
                    _uiState.value = result.fold(
                        onSuccess = { ProfileUiState.Success(it) },
                        onFailure = { ProfileUiState.Error(it.message ?: "Unknown error") }
                    )
                }
        }
    }
}
// TESTS PASS - minimal functionality works
```

**Phase 3: REFACTOR (Improve Design)**

```kotlin
// Improved version with better error handling and retry
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getUserUseCase: GetUserProfileUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Initial)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentUserId: String? = null

    fun loadProfile(userId: String) {
        currentUserId = userId
        fetchProfile(userId)
    }

    fun retry() {
        currentUserId?.let { fetchProfile(it) }
    }

    private fun fetchProfile(userId: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading

            getUserUseCase(userId)
                .catch { e ->
                    _uiState.value = ProfileUiState.Error(
                        message = e.localizedMessage ?: "Failed to load profile",
                        canRetry = e is IOException
                    )
                }
                .collect { result ->
                    _uiState.value = result.fold(
                        onSuccess = { ProfileUiState.Success(it) },
                        onFailure = { ProfileUiState.Error(it.message ?: "Unknown error") }
                    )
                }
        }
    }
}
// TESTS STILL PASS - improved code quality
```

### TDD Cycle Visualization for Android

```
Phase 1: 🔴 RED
├── Write ViewModel test with Turbine
├── Write UseCase test with MockK
├── Run tests → FAILS ❌
└── Commit: "Add failing tests for Profile feature"

Phase 2: 🟢 GREEN
├── Implement UiState sealed class
├── Implement UseCase
├── Implement ViewModel
├── Run tests → PASSES ✅
└── Commit: "Implement Profile feature to pass tests"

Phase 3: 🔵 REFACTOR
├── Add SavedStateHandle support
├── Run tests → STILL PASSES ✅
├── Add error retry logic
├── Run tests → STILL PASSES ✅
├── Extract common patterns
├── Run tests → STILL PASSES ✅
└── Commit: "Refactor Profile for production quality"

Phase 4: 🎨 UI
├── Write Compose UI tests
├── Implement Screen Composable
├── Run UI tests → PASSES ✅
├── Add Navigation
├── Run all tests → PASSES ✅
└── Commit: "Add Profile UI with Compose"
```

---

**Plan Status**: 🔄 In Progress
**Next Action**: [What needs to happen next]
**Blocked By**: [Any current blockers] or None
