# GitHub Actions CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land a build-and-test workflow, a CodeQL SAST workflow, and a Dependabot configuration so PRs and develop pushes get automated verification.

**Architecture:** Three config files under `.github/` at the repository root. The build workflow compiles and runs the test suite against a Postgres 16 service container; CodeQL runs the `java-kotlin` analyzer with `autobuild`; Dependabot is a data file GitHub reads natively (no workflow needed).

**Tech Stack:** GitHub Actions, Temurin JDK 17, Postgres 16, CodeQL Action v3, Gradle 9 wrapper.

**Spec:** `docs/superpowers/specs/2026-04-19-github-actions-ci-design.md`

---

## File Structure

**New files (all at `kasisira-backend/`):**
- `.github/workflows/ci.yml` — build-and-test pipeline.
- `.github/workflows/codeql.yml` — SAST pipeline.
- `.github/dependabot.yml` — dependency and Actions version monitoring.

**Unchanged:**
- No Kotlin source.
- No Gradle config.
- No application properties.

---

## Task 1: CI build-and-test workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1.1: Create the workflow file**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]

jobs:
  build-and-test:
    name: Build & Test
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
          POSTGRES_DB: kasisira_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    env:
      TEST_DB_USERNAME: postgres
      TEST_DB_PASSWORD: postgres

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Run tests
        run: ./gradlew test --no-daemon

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            build/reports/tests/test
            build/test-results/test
          retention-days: 7
```

- [ ] **Step 1.2: Validate YAML syntax locally**

```bash
cd kasisira-backend && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('OK')"
```

Expected: `OK`. If `PyYAML` is not installed, skip — GitHub will validate on push.

- [ ] **Step 1.3: Commit**

```bash
cd kasisira-backend && git add .github/workflows/ci.yml
git commit -m "ci(actions): add build-and-test workflow with postgres service"
```

---

## Task 2: CodeQL SAST workflow

**Files:**
- Create: `.github/workflows/codeql.yml`

- [ ] **Step 2.1: Create the workflow file**

Create `.github/workflows/codeql.yml`:

```yaml
name: CodeQL

on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop, main]
  schedule:
    - cron: '0 6 * * 1'

jobs:
  analyze:
    name: Analyze
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      actions: read
      contents: read

    strategy:
      fail-fast: false
      matrix:
        language: [java-kotlin]

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v3
        with:
          languages: ${{ matrix.language }}

      - name: Autobuild
        uses: github/codeql-action/autobuild@v3

      - name: Perform CodeQL analysis
        uses: github/codeql-action/analyze@v3
        with:
          category: "/language:${{ matrix.language }}"
```

- [ ] **Step 2.2: Validate YAML syntax locally**

```bash
cd kasisira-backend && python3 -c "import yaml; yaml.safe_load(open('.github/workflows/codeql.yml')); print('OK')"
```

Expected: `OK`. Skip if PyYAML not installed.

- [ ] **Step 2.3: Commit**

```bash
cd kasisira-backend && git add .github/workflows/codeql.yml
git commit -m "ci(actions): add CodeQL SAST workflow for java-kotlin"
```

---

## Task 3: Dependabot configuration

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 3.1: Create the config file**

Create `.github/dependabot.yml`:

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

- [ ] **Step 3.2: Validate YAML syntax locally**

```bash
cd kasisira-backend && python3 -c "import yaml; yaml.safe_load(open('.github/dependabot.yml')); print('OK')"
```

Expected: `OK`. Skip if PyYAML not installed.

- [ ] **Step 3.3: Commit**

```bash
cd kasisira-backend && git add .github/dependabot.yml
git commit -m "ci(actions): add Dependabot config for gradle and github-actions"
```

---

## Task 4: Push branch, open PR, observe CI

**Files:**
- None (remote operations only)

- [ ] **Step 4.1: Push the branch**

```bash
cd kasisira-backend && git push -u origin chore/github-actions-ci 2>&1 | tail -5
```

Expected: `* [new branch]      chore/github-actions-ci -> chore/github-actions-ci` and an upstream-tracking confirmation.

- [ ] **Step 4.2: Open the pull request**

```bash
cd kasisira-backend && gh pr create --base develop --title "ci(actions): add build/test + CodeQL + Dependabot" --body "$(cat <<'EOF'
## Summary

Adds three config files under `.github/` to give every PR and every develop merge automated verification:

- `ci.yml` — compiles the project and runs the 335-test suite against a Postgres 16 service container.
- `codeql.yml` — CodeQL `java-kotlin` analyzer on PRs, develop pushes, and weekly cron.
- `dependabot.yml` — weekly Dependabot PRs for gradle and github-actions ecosystem updates.

No application code changes.

## Test plan

- [ ] This PR's own `Build & Test` check goes green against the real service-container Postgres.
- [ ] `Analyze` (CodeQL) runs to completion, or fails cleanly with a GitHub Advanced Security availability error (acceptable outcome — to be removed later if GHAS isn't enabled).
- [ ] After merge, branch protection on `develop` and `main` is updated (in the GitHub UI) to require the `Build & Test` check.
- [ ] After merge, Dependabot posts any existing vulnerable-dependency updates.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: the command prints a PR URL on stdout.

- [ ] **Step 4.3: Watch CI runs**

```bash
cd kasisira-backend && gh pr checks --watch
```

Expected: once the jobs complete, `Build & Test` is `pass`. `Analyze` is either `pass` (GHAS enabled) or `fail` with a `code-scanning` / `advanced security not enabled` message (acceptable — remove the workflow in a follow-up if that's the case).

- [ ] **Step 4.4: Record the outcome in the plan doc**

If `Analyze` failed because GHAS isn't enabled, edit `docs/superpowers/plans/2026-04-19-github-actions-ci.md` and add a one-line note at the bottom under a new `## Post-merge follow-ups` section:

```markdown
## Post-merge follow-ups

- `codeql.yml` failed because GitHub Advanced Security is not enabled on this repo. Remove the workflow (or enable GHAS) in a follow-up PR.
```

Commit as:

```bash
cd kasisira-backend && git add docs/superpowers/plans/2026-04-19-github-actions-ci.md
git commit -m "docs(ci): record CodeQL outcome on chore/github-actions-ci"
git push
```

Skip this step if `Analyze` passed.

---

## Done Criteria

- Three new files committed on `chore/github-actions-ci`: `ci.yml`, `codeql.yml`, `dependabot.yml`.
- Branch pushed to `origin`.
- Pull request opened into `develop`.
- `Build & Test` check passes on the PR.
- CodeQL outcome documented (either green, or the follow-up note added to the plan).
- Dependabot starts monitoring the repo (visible in the Insights → Dependency graph → Dependabot tab after merge).
