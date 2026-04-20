# GitHub Actions CI for Build, Tests, and Security

**Date:** 2026-04-19
**Status:** Approved — ready for implementation planning
**Branch:** `chore/github-actions-ci` (off `develop`)

## Problem

The repository has no automated verification. Pull requests to `develop` or `main` merge without any external proof that the build compiles or the test suite passes. Security posture is also ad-hoc — third-party CVEs land silently and there's no SAST running against the Kotlin source.

## Goal

Every pull request to `develop` or `main`, and every push to those branches, runs through an automated pipeline that:

1. Compiles the project on Java 17
2. Runs the full test suite (`./gradlew test`, currently 335 tests) against a real Postgres 16 instance so repository and Flyway tests execute against the production database engine
3. Runs CodeQL SAST over the Kotlin/Java source for vulnerability patterns (SQL injection, crypto misuse, etc.)
4. Keeps dependencies monitored so known CVEs in Spring Boot, jjwt, the Postgres driver, Flyway, and friends auto-generate update PRs

Failures become required blockers (via branch-protection rules configured in the GitHub UI, out of scope for this change).

## Approach

Three new config files under `.github/`:

1. `.github/workflows/ci.yml` — single-job `build-and-test` workflow.
2. `.github/workflows/codeql.yml` — CodeQL analysis workflow.
3. `.github/dependabot.yml` — Dependabot configuration (no workflow; GitHub reads this file natively).

No application code changes. No Gradle plugin changes. No modifications to `application-testing.properties` — the tests already work against Postgres when the standard environment variables are set.

## Design

### `.github/workflows/ci.yml`

**Triggers:** `pull_request` against `develop` or `main`; `push` to `develop` or `main`.

**Job:** `build-and-test` on `ubuntu-latest`.

**Service container:** Postgres 16 official image with env `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=postgres`, `POSTGRES_DB=kasisira_test`, port 5432 exposed to the job network, healthcheck via `pg_isready` (interval 10s, 5 retries).

**Steps:**

1. `actions/checkout@v4`
2. `actions/setup-java@v4` with `distribution: temurin`, `java-version: 17`, `cache: gradle` so the Gradle caches persist across runs.
3. `./gradlew test --no-daemon` with env `TEST_DB_USERNAME=postgres` and `TEST_DB_PASSWORD=postgres`. The test classes already carry `@ActiveProfiles("testing")`; `application-testing.properties` resolves `${TEST_DB_USERNAME}` / `${TEST_DB_PASSWORD}` from those env vars.
4. `actions/upload-artifact@v4` conditional on `failure()`, uploading `kasisira-backend/build/reports/tests/test/` and `kasisira-backend/build/test-results/test/` so anyone inspecting a red run can download the HTML report and surefire XMLs.

**Why one job, not split build and test:** the test task compiles production and test code transitively, so splitting into `./gradlew build` then `./gradlew test` pays the compile cost twice without adding signal. A single `test` invocation is sufficient.

**Why Temurin:** it's the default JDK distribution in GitHub Actions and the project's `build.gradle.kts` only pins `JavaLanguageVersion.of(17)`, not a vendor.

### `.github/workflows/codeql.yml`

**Triggers:** same `pull_request` / `push` triggers as `ci.yml`, plus a `schedule` cron `0 6 * * 1` (Monday 06:00 UTC) so scheduled scans catch vulnerabilities introduced by updated CodeQL rules even when no commits land.

**Job:** `analyze` on `ubuntu-latest` with `permissions: { security-events: write, actions: read, contents: read }`.

**Matrix:** single entry `language: java-kotlin` (CodeQL's unified analyzer for both Java and Kotlin source).

**Steps:**

1. `actions/checkout@v4`
2. `actions/setup-java@v4` with Temurin 17 — CodeQL's `autobuild` invokes Gradle, so the JDK must be ready first.
3. `github/codeql-action/init@v3` with `languages: java-kotlin`
4. `github/codeql-action/autobuild@v3`
5. `github/codeql-action/analyze@v3` with `category: "/language:java-kotlin"`

**Failure behavior:** if the repository doesn't have GitHub Advanced Security enabled (a possibility for private repos on plans that exclude it), the `analyze` step fails. That's acceptable — the `ci.yml` workflow still gates merges. If GHAS truly isn't available we can remove the CodeQL workflow without disrupting anything else.

### `.github/dependabot.yml`

Two ecosystems, both at the repository root:

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
```

Dependabot will auto-open PRs for outdated Gradle dependencies and outdated Action versions. The `open-pull-requests-limit: 5` prevents a flood when many updates queue up.

### Secrets and credentials

The workflow uses no GitHub secrets. The database credentials (`postgres` / `postgres`) are hardcoded in `ci.yml` because they're ephemeral per-run credentials for a disposable service container — not deployment credentials. No real secrets leak.

### Branch protection (manual, out of this spec)

After merge, someone with admin rights on the repo configures GitHub branch protection so that `develop` and `main` require:

- `build-and-test` (from `ci.yml`)
- `analyze` (from `codeql.yml`) — only when GHAS is enabled
- Linear history / up-to-date with base branch (optional)

That configuration happens in the GitHub UI, not in this repo.

## Testing

1. **Push the branch and open a draft PR into `develop`.** The `ci.yml` workflow must run and go green; artifacts should be downloadable.
2. **Deliberately break a test in a follow-up commit on the PR branch.** Confirm `build-and-test` goes red and the failure artifact contains the expected report.
3. **Inspect the Dependabot alerts page** after merge. Within a day, Dependabot should list whichever vulnerable dependencies currently exist in the tree (if any).
4. **CodeQL:** if it runs, confirm the Security tab's "Code scanning alerts" populates. If it fails because GHAS isn't enabled, accept that outcome and note the workflow for later re-enabling.

## Out of Scope (YAGNI)

- Deploy jobs — no target environment is configured.
- ktlint / detekt static analysis for style/code smells (user explicitly picked security-only at the brainstorming stage).
- Publishing test reports as PR comments (artifact download is sufficient).
- Docker image build and publish.
- Separate `lint`, `build`, and `test` jobs — not needed at current scale; single-job is faster given Gradle's incremental compilation.
- Matrix builds across JDK versions — the project pins Java 17 and has no need to test elsewhere.
- Caching beyond what `actions/setup-java@v4`'s built-in Gradle cache provides.
