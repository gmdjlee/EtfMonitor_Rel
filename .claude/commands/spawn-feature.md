# /project:spawn-feature — Cross-Agent New Feature Protocol

Orchestrate the creation of a new feature using all 6 agents in the correct sequence defined in `docs/AGENTS.md`.

## Protocol Sequence

Execute agents in this order. Each step depends on the previous.

### Step 1: feature-agent — Define UseCase Signature
The feature-agent identifies what UseCases the new feature needs based on the UI requirements.
- Define screen states (UiState sealed class)
- List required UseCase signatures (input -> output)
- Identify navigation requirements

### Step 2: domain-agent — Create UseCase + Repository Interface
Pure Kotlin business logic layer.
- Create domain models (data classes)
- Create repository interface (suspend/Flow methods)
- Create UseCase classes with `suspend operator fun invoke()`

### Step 3: data-agent — Implement Repository + Data Sources
Data access implementation.
- Create Room entities and DAOs (with migration if needed)
- Create mappers (Entity <-> Domain)
- Implement RepositoryImpl
- Create/update Python client if needed
- Update DI module bindings

### Step 4: feature-agent — Integrate UseCase into ViewModel
Wire up the presentation layer.
- Create ViewModel with UseCase injection
- Create Screen composable
- Wire up state collection and event handling

### Step 5: integration-agent — Register Navigation
App-level wiring.
- Add Screen route to sealed class
- Register composable in NavHost
- Schedule workers if needed
- Update build config if new dependencies

### Step 6: test-agent — Write Tests
Comprehensive test coverage.
- Domain: Pure JUnit + FakeRepository
- ViewModel: JUnit + Turbine + fake UseCases
- Repository: JUnit + fake DataSources
- Migration: Room MigrationTestHelper (if schema changed)

## Usage

Provide the feature description as argument:
```
/project:spawn-feature Add a sector rotation analysis screen that shows ETF sector allocation changes over time
```

## Output

For each step, report:
- Files created/modified
- Key decisions made
- Dependencies between steps
