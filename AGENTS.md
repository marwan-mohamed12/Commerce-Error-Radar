# Commerce Error Radar — project instructions

This file is the source of truth for Grok and any other agent working in this repository.

## Work here, never in a worktree

- **Canonical checkout:** `F:\grok\Commerce-Error-Radar` (this git repo, branch `main`).
- **Always edit files in this checkout.** Do not create, use, or copy work into `~/.grok/worktrees/…`.
- Do **not** pass `isolation: "worktree"` to subagents.
- Do **not** run `grok worktree`, `git worktree add`, or any isolated worktree for this project.
- If a session starts inside a grok worktree, stop, `cd` to `F:\grok\Commerce-Error-Radar`, and make every change there.
- One working tree only: the user’s real repo.

## What this project is

Local **ERROR / WARN inbox** for **SAP Commerce (Hybris) + Spring** on Windows.

The developer starts `hybrisserver.bat` as usual. This app tails today’s Tomcat console log, keeps `ERROR` / `WARN` events, groups duplicates by fingerprint, and shows them in an Angular UI that does not scroll away.

**Local only.** No SAP Commerce Cloud, OpenSearch, Dynatrace, custom Hybris extension, Kafka, Docker, or wrapping `hybrisserver.bat`.

```text
hybrisserver.bat
       │
       ▼
<HYBRIS_HOME>\hybris\log\tomcat\console-YYYYMMDD.log
       │  tail from EOF (never load a multi-GB file)
       ▼
Spring Boot collector :8088  →  SQLite  →  Angular UI :4200
```

If `HYBRIS_HOME` is empty, the collector replays `sample-logs/console-20260809.log` (DEMO mode).

## Layout

```text
F:\grok\Commerce-Error-Radar\
  AGENTS.md                 this file
  README.md                 how a human runs the app
  pom.xml                   Maven parent (Java 21)
  parser/                   pure Java parser + JUnit fixtures (no Spring)
  collector/                Spring Boot 3.5 API, tailer, SQLite, SSE
  web/                      Angular 22 dashboard
  sample-logs/              demo console chunk
  Plan/plan.md              original phased plan
  start.bat / start-collector.bat / start-ui.bat
```

## Stack

| Piece | Choice |
|---|---|
| JDK | 21 (Adoptium). Machine `JAVA_HOME` may still be JDK 8 — set JDK 21 before Maven. |
| Parser | `parser` module, JUnit 5 |
| Collector | Spring Boot 3.5, JDBC + SQLite, SSE |
| UI | Angular 22, standalone components, signals, SCSS (not React, not Tailwind) |
| Ports | collector `8088`, UI `4200` (dev server proxies `/api` → 8088) |

## How to run

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot
set HYBRIS_HOME=D:\hybris
set RADAR_PREFIX=com.yourcompany
```

```bat
mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=--radar.hybris-home=%HYBRIS_HOME% --radar.custom-package-prefix=%RADAR_PREFIX%"
```

```bat
cd web
npm install
npm start
```

Open http://localhost:4200

Tests: `mvn test` from the repo root. UI build: `cd web && npm run build`.

Parent POM skips `spring-boot:run`; only `collector` runs the app. Always use `-pl collector -am`.

## Domain rules (keep these stable)

### Tailer

- Resolve newest `console-*.log` under `<hybris-home>\hybris\log\tomcat` (or `\log\tomcat`).
- Live: start at EOF. Demo / replay / new rotated file: start at beginning.
- Poll file length (~500ms). Do not load the whole file.
- On a newer `console-YYYYMMDD.log`, switch without restarting the collector.
- Ring buffer ~100 lines; attach ~30 lines of context when an event closes.
- Persist **only** WARN/ERROR. INFO/DEBUG stay in the ring buffer.

### Parser (`parser` module)

- A log header with ERROR / WARN / FATAL starts an event.
- Following `at `, `Caused by:`, `Suppressed:`, `... N more`, and exception-type lines belong to it.
- The next normal log header closes the event.
- Fingerprint = `ExceptionName` + first frame under `radar.custom-package-prefix`.
- If there is no custom frame, first non-framework frame. Never key off `de.hybris.*`, Spring, Tomcat, `java.*`.
- Hybris-only stacks fingerprint as `ExceptionName@hybris`.
- Extract order / product / user / cronjob / catalog version / `.impex` file when present.
- Classify: `CRONJOB`, `IMPEX`, `OCC`, `FLEXIBLE_SEARCH`, `SOLR`, `INTERCEPTOR`, `MODEL_SAVE`, `OTHER`.

### Ignore list

- Patterns live in `collector/src/main/resources/application.yml` (`radar.ignore-patterns`).
- Match **only the event’s own text** (raw + message + logger). Never match preceding context, or a Solr ping will hide later real errors.

### API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/runs/current` | tail status |
| POST | `/api/runs/open` | open a log file (`path`, `replay`) |
| GET | `/api/issues` | grouped issues (`level`, `kind`, `q`, `mineOnly`) |
| GET | `/api/issues/one?fingerprint=` | issue + recent events (query param, not path — fingerprints contain `@`) |
| POST | `/api/issues/mute?fingerprint=` | mute / unmute |
| GET | `/api/events` | flat search |
| GET | `/api/stream` | SSE (`issue`, `status`, `hello`) |

SQLite file: `collector/data/radar.db` (gitignored). Schema on startup in `SchemaInitializer`.

### UI (`web/`)

- Dark “night-shift console” UI: issue list left, detail right.
- Collapse Hybris/framework frames; highlight custom-package frames.
- Filters: ERROR/WARN, kind chips, mine-only, search.
- Copy stack, mute, open/replay an old log.
- SSE updates counts live.
- Distinctive type: Fraunces + Sora + IBM Plex Mono. Do not restyle as generic SaaS or acid-green terminal.

## Code conventions

- Java 21, constructor injection, `application.yml` + `@ConfigurationProperties`.
- Parser has **no** Spring dependency. New parse/fingerprint/classifier logic goes in `parser` with a fixture test.
- Do not expose JDBC rows directly; use the DTOs in `IssueDtos`.
- Angular: standalone components, signals, `@if`/`@for`, no NgModules.
- Compile with `-parameters` (already in parent POM).
- PowerShell: quote Maven `-D` flags; do not use `&&` if the shell rejects it — use `;`.

## Out of scope (do not build)

- SAP Commerce Cloud / CCv2
- OpenSearch / Kibana / Dynatrace
- Log4j HTTP appender inside Hybris
- Wrapping `hybrisserver.bat`
- Elasticsearch, Kafka, Grafana, Docker Compose
- Electron / JavaFX
- Storing every INFO line
- React UI (this project is Angular)

## When changing behavior

1. Edit this repo root only (`F:\grok\Commerce-Error-Radar`).
2. Add or update a parser fixture if log parsing changes.
3. Run `mvn test`.
4. Keep `README.md` run instructions in sync with flags and ports.
