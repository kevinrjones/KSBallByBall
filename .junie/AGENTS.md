# Project Guidelines

This document outlines the coding standards, architecture, and deployment procedures for this project.

## General Coding Standards

** For Kotlin**

- **Kotlin First**: Use idiomatic Kotlin (data classes, extension functions, sealed classes, null safety).
- **Provide Tests**: Prefer JUnit and Strikt for the tests and always provide tests
- **Functional Style**: Prefer Arrow's `Either` for error handling and data flow instead of throwing exceptions.
- **Functional Style**: Prefer a functional style for all code.
- **Small function**: Prefer smaller descriptively named functions over large blocks of code
- **Naming**:
    - Classes/Interfaces: PascalCase (e.g., `JooqPartnershipsRepository`)
    - Functions/Properties: camelCase (e.g., `getOverallPartnership`)
    - Constants: UPPER_SNAKE_CASE (e.g., `OVERALL_ROUTE`)
- **Immutability**: Prefer `val` over `var` and `List` over `MutableList`.
- Prefer immutable data classes, for example prefer
- **Static analysis** Make use of static analysis tools like Detekt and Ktlint to enforce coding standards and catch
  potential issues early. Run these tools as part of your CI/CD pipeline and integrate them into your development
  workflow.

```kotlin
data class OidcConfiguration(
    val authority: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scopes: List<String> = listOf("openid", "profile")
)
``` 

over

```kotlin
data class OidcConfiguration(
    var authority: String = "",
    var clientId: String = "",
    var clientSecret: String = "",
    var scopes: List<String> = listOf("openid", "profile")
)
``` 

- When building DSLs prefer the use of context receivers to make the DSLs easier to construct and read
- When creating errors prefer a sealed class hierarchy (e.g. `sealed class Error(val message: String)`) matching the
  pattern in `acs-api`, providing a common `message` property across all domain and validation errors
- Prefer to use the type system where possible, for example use **Tiny Types** in thd code where you can rather than
  scattering say 'Int' or 'String' types throughout the code
- Use these tiny types as a validation mechanism
- Log all Exceptions using the standard logger mechanism
- Add logs extensively throughout the code, making use of the different logging levels

### After Adding Code at Any Time

- After adding code, make sure all the tests run
- After adding code, make sure that the gradle `check` task runs successfully for all modules
- Do not simply 'suppress' any errors or warnings, fix the code instead

### Build & Dependencies

- Use Gradle (`build.gradle.kts`) for dependency management and builds in JVM based projects (eg Kotlin or Java).
- Use a toml file for all dependencies and reference that TOML file in the gradle build files
- Make sure to create the appropriate .gitignore file. This should make sure it ignores any files that are built as well
  as files that are created by the ide that should not be shared.
- Make sure that .gitignore files also includes any files that contains secrets that should not be pushed to a public
  repository
- when referencing plugins add the plugin reference to the toml file and then use that reference in the gradle file,
  i.e.
  prefer this

``` kotlin
    alias(libs.plugins.ktor.plugin)
```

to this

```
    id("io.ktor.plugin") version "3.4.1"
```

## Architecture Guidelines

- Read `docs/ARCHITECTURE.md` before making large structural changes or when you need a module-by-module overview of the
  application.
- Update `docs/ARCHITECTURE.md` at the end of any sprint that changes or extends the architecture.
- **SOLID** prefer to follow the SOLID principals
- **Patterns** prefer using GoF or other established patterns in the code
- GoF patterns are opt-in, never speculative. No pattern without a named reason in an ADR or the sprint Architect
  design.
- Pre-blessed patterns for all codebases:
    - Command
    - Adapter
    - Strategy
- Discouraged: Template Method. Prefer composition.
- Inheritance: capped at one level. Prefer composition.
- **COMPOSITION** Prefer composition over inheritance
- **ADR** Generate an ADR for every architectural decision
- **Testing** Code must be test first, prefer to use BDD for user stories and TDD for testing smaller units
- **TDD** Prefer JUnit and Strikt for TDD
- **BDD** Prefer Cucumber JVM for BDD and use Gherkin spec files

## Feature Slices Architecture

The application is structured using **Feature Slices**. Instead of organizing code strictly by horizontal technical
layers (e.g. monolithic global `controllers`, `services`, and `repositories` across disparate domains), each feature has
its own package/directory, and everything related to that feature that is not shared lives in that directory.

Features reside under `feature.<feature_name>` (e.g., `feature.heartbeat`, `feature.health`, `feature.matches`,
`feature.user`).

Each feature slice is structured into:

- `presentation`: Ktor routes, HTTP request/response handling, parameter parsing and validation for the feature.
- `domain`: Use cases, services, domain models, and repository interfaces specific to the feature.
- `data`: Repository implementations (e.g. jOOQ-based data access), database queries, and data mappers for the feature.

### Rules for Feature Slices

1. **Feature Colocation**: Code that changes together stays together. All routes, services, and queries for a given
   feature are located inside its feature folder.
2. **Shared Infrastructure & Core**: Cross-cutting concerns that are shared across features (such as server bootstrap,
   database connection pooling/configuration, security/JWT verification plugins, common HTTP helpers, and shared
   serialization models like `Envelope`) live in top-level shared packages (e.g. `bootstrap`, `config`, or
   `bbb-shared`).
3. **Encapsulated Dependencies**: Features define their dependencies (such as repository interfaces) in their `domain`
   layer and implement them in their `data` layer.
4. **Independent Evolution**: Adding, modifying, or removing a feature touches only that feature's directory, avoiding
   cascading modifications across unrelated domains.

## Tiny Types (Value Classes) and Boundary Validation

The codebase enforces **Tiny Types** using Kotlin inline value classes (`@JvmInline value class`) combined with
functional boundary validation via **Arrow** (`Either`, `Raise`, and `zipOrAccumulate`).

### Core Principles

1. **Zero-Allocation Strong Typing**: Domain concepts that wrap primitives (such as IDs, limits, codes, seasons, and
   user identifiers) must be defined as `@JvmInline value class` (e.g. `Limit`, `MatchKey`, `PublicMatchId`,
   `MatchType`, `Season`, `UserId`). On the JVM they compile to raw primitives, incurring zero runtime object allocation
   overhead.
2. **Serialization Transparency**: Value classes annotated with `@Serializable` serialize directly as their underlying
   primitive values in `kotlinx.serialization`. JSON contracts on the wire remain standard primitives (numbers, strings)
   for seamless client compatibility.
3. **Encapsulated Construction & Invariants**:
    - Constructors should be `private` to prevent unvalidated instantiation.
    - Companion `invoke` operators with Arrow `Raise`:
      ```kotlin
      context(raise: Raise<LimitError>)
      operator fun invoke(value: String?): Limit
      ```
    - Factory methods returning Arrow `Either`:
      ```kotlin
      fun of(value: Int): Either<LimitError, Limit> = either { invoke(value) }
      fun fromRaw(value: String?): Either<LimitError, Limit> = either { invoke(value) }
      ```
    - Validated internal factory methods (`from`) for trusted internal mappings (such as jOOQ SQL record mapping):
      ```kotlin
      fun from(value: Int): Limit {
          require(value in MIN_LIMIT..MAX_LIMIT) { "limit must be between $MIN_LIMIT and $MAX_LIMIT" }
          return Limit(value)
      }
      ```

### Boundary Validation at Presentation Layer

1. **Validate at the Edge**: Untrusted HTTP request parameters (query parameters, path segments, request headers) must
   be parsed and validated at the **presentation boundary** (Ktor route handlers) before invoking domain services or
   repositories.
2. **Arrow Raise DSL & `fold`**: Use Arrow's `fold` or `either` blocks at the route entry point:
   ```kotlin
   get("/matches") {
       fold(
           block = { Limit(call.request.queryParameters["limit"]) },
           recover = { error -> call.respondBadRequest(error.message) },
           transform = { limit ->
               val matches = matchService.recentMatches(limit)
               call.respondOk(RecentMatchesResponse(matches = matches))
           }
       )
   }
   ```
3. **Multi-Parameter Accumulation (`zipOrAccumulate`)**: When an endpoint requires validating multiple parameters, use
   `zipOrAccumulate` to aggregate all errors into a `NonEmptyList<Error>` so clients receive complete feedback on all
   invalid fields in a single response:
   ```kotlin
   fold(
       block = {
           zipOrAccumulate(
               { Limit(call.request.queryParameters["limit"]) },
               { MatchType(call.request.queryParameters["matchType"]) }
           ) { limit, matchType -> Pair(limit, matchType) }
       },
       recover = { errors -> call.respondBadRequest(errors) },
       transform = { (limit, matchType) -> ... }
   )
   ```
4. **Structured Error Hierarchy**: Domain errors extend a sealed class matching `acs-api`:
   ```kotlin
   @Serializable
   sealed class Error(val message: String)

   class LimitError(message: String, val limit: String? = null) : Error(message)
   class MatchTypeError(message: String, val matchType: String? = null) : Error(message)
   class DatabaseError(val stackTrace: String, message: String) : Error(message)
   ```
   All domain, validation, and persistence errors inherit from `Error(message)` so they share a common `message`
   property and can be formatted and logged uniformly.
5. **Pure Domain Services**: Domain use cases, services, and repositories accept only validated tiny types (`Limit`,
   `MatchKey`), ensuring illegal states are completely unrepresentable inside the core business logic.

## Testing Strategies

* **Frameworks**: Use JUnit 5 with Kluent or Strikt for assertions.
* **Mocking**: When testing parsers, use local HTML files instead of making real network calls.
* **Database**: Integration tests for should use test-containers
* **coverage**  Use kover for test Coverage
*               Attempt to get 100% test coverage before completing the task, if you cannon get that coverage report the reasons to the user
* **Mutation Testing**  Use Pitest for mutation testing

### Git Workflow

#### Guidelines

- ***Only commit when explicitly asked to***
- Use clear, descriptive commit messages.
- DO NOT add any ads such as "Co-authored-by: Junie <junie@jetbrains.com>`."
- Only generate the message for staged files/changes
- Don't add any files using `git add`. The user will decide what to add.
- Follow the rules below for the commit message.
- Use standard labels and scoping
- Make sure I'm not committing to `main`. If I'm trying to do that, suggest a branch name, ask me to confirm, then
  create the branch.
- Ask me to confirm the message, then add all files and run the commit.

#### Format

```
<type>:<space><message title>

<bullet points summarizing what was updated>
```

#### Example Titles

```
feat(auth): add JWT login flow
fix(ui): handle null pointer in sidebar
refactor(api): split user controller logic
docs(readme): add usage section
```

#### Example with Title and Body

```
feat(auth): add JWT login flow

- Implemented JWT token validation logic
- Added documentation for the validation component
```

#### Rules

* title is lowercase, no period at the end.
* Title should be a clear summary, max 50 characters.
* Use the body (optional) to explain *why*, not just *what*.
* Bullet points should be concise and high-level.

Avoid

* Vague titles like: "update", "fix stuff"
* Overly long or unfocused titles
* Excessive detail in bullet points

#### Allowed Types

| Type     | Description                           |
|----------|---------------------------------------|
| feat     | New feature                           |
| fix      | Bug fix                               |
| chore    | Maintenance (e.g., tooling, deps)     |
| docs     | Documentation changes                 |
| refactor | Code restructure (no behavior change) |
| test     | Adding or refactoring tests           |
| style    | Code formatting (no logic change)     |
| perf     | Performance improvements              |

## Adding Features

* When adding features prefer using an 'outside-in' rather than in 'inside-out' style (also known as creating a 'walking
  skeleton').
* Prefer creating the UI or command line first and filling in the internal details later
* If the feature is part of a CLI then make sure that the feature is always 'runnable' from the command line with the
  correct command line flags in place
* If the feature is part of a web or mobile user interface make sure that the UI is navigable and available to a human
  user and tester

## Tasks

### Task List Structure

- By default task lists are maintained in the docs/tasks/TASKS-SPRINT-[SPRINTNUMBER]-[SPRINTNAME].md file unless
  otherwise specified. Where [SPRINTNUMBER] and [SPRINTNAME] are placeholders for the sprint number and name.
- Tasks are organised hierarchically with main tasks and subtasks
- Each task has a unique identifier (e;g; 1, 1.1, 1.2 etc.)
- Tasks are grouped into logical sections based on the feature

### Task Status Tracking

- Tasks are marked with checkboxes:
    - `[ ]` indicates a task that has not been started or is in progress
    - `[x]` indicates a task that has been completed
- A parent task should only be marked as completed when all its subtasks are completed
- The tasks file should be updated as you progress through the tasks
- The last task in a sprint should be a "Human in the Loop" task, this should tell the Human exactly how to test the set of tasks that have just been completed with check boxes for the human to confirm that the tasks have been completed

### Structure

### Notes

- ALWAYS IGNORE node_modules folders when evaluating code
- Use `docs/ARCHITECTURE.md` as the primary architecture map for module boundaries, runtime flows, and contributor entry
  points.
- Use the **UBIQUITOUS_LANGUAGE.md*, if it exists, to understand the domain language of the project
- Use the **docs/RECAP.md* to understand what has happened in project
- Use the **docs/project_memory.md* to understand what has happened in project

** Sprint Completion or Task Completion **

At the end of each phase - either a sprint has completed or a full task has completed (not just subtasks) update a
`docs/project_memory.md` file. This file should have several sections

If the sprint changed module responsibilities, runtime flows, or major subsystem boundaries, also update
`docs/ARCHITECTURE.md` and make sure any contributor-navigation guidance here still points to the right files.

Overall (this will be expanded after each sprint/task completion)
**What was shipped**
**Key decisions**
**Gotchas**
For each sprint/task **Title**
**Date/time completed**
**What was shipped**
**Key decisions**
**Gotchas**
**Test coverage areas**

# Contributor navigation

Read `docs/architecture/applications.md` for the architecture map of module boundaries
and data flows.

- Organize shared schemas and identity contracts in `bbb-shared`.
- Run `./gradlew clean check --no-daemon` after application changes.

The broader project standards remain in `.junie/AGENTS.md`.