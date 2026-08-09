# Commerce Error Radar

Local error inbox for **SAP Commerce (Hybris) + Spring** on Windows.

**Repo to edit:** `F:\grok\Commerce-Error-Radar` (this checkout).  
**Do not use git/grok worktrees.** Agents and humans make every change directly here. Full project context and agent rules: [`AGENTS.md`](AGENTS.md).

You start `hybrisserver` as usual. This app tails today’s Tomcat console log, keeps `ERROR` / `WARN` events, groups duplicates, and shows them in a browser UI that does not scroll away.

**Scope: local only.** No SAP Commerce Cloud, no OpenSearch, no Dynatrace, no custom Hybris extension.

```text
hybrisserver.bat
       │
       ▼
hybris\log\tomcat\console-YYYYMMDD.log
       │  tail
       ▼
Spring Boot collector  →  SQLite  →  http://localhost:4200  (Angular)
```

## Stack

| Piece | Choice |
|---|---|
| Collector + API | Java 21 + Spring Boot 3 |
| Ingest | Tail `hybris\log\tomcat\console-*.log` |
| Store | SQLite (WARN/ERROR only) |
| Live updates | Server-Sent Events |
| UI | Angular 22 + Tailwind CSS |
| Parser | Pure Java module, fixture-tested |

## Prerequisites

- JDK 21 on `PATH` (or `JAVA_HOME`)
- Maven 3.9+
- Node 20+ (24 is fine)
- A local Hybris install, **or** use the bundled `sample-logs/` demo

Set these before a live session:

| Setting | Example |
|---|---|
| Hybris home | `D:\hybris` |
| Custom package prefix | `com.yourcompany` |
| Collector | `http://localhost:8088` |
| UI | `http://localhost:4200` |

```bat
set HYBRIS_HOME=D:/dccp-digitalcommerce-customerportal/core-customize/hybris
set RADAR_PREFIX=com.marwan.radar
```

Or put the same values in `collector/src/main/resources/application.properties` (`radar.hybris-home`, `radar.custom-package-prefix`). Leave `radar.hybris-home` empty only when you want the bundled `sample-logs` demo.

## Run

**One-click (two extra terminals):** `start.bat`

Or three terminals:

```bat
cd /d D:\hybris\hybris\bin\platform
hybrisserver.bat
```

```bat
mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=--radar.hybris-home=D:/dccp-digitalcommerce-customerportal/core-customize/hybris --radar.custom-package-prefix=com.marwan.radar"
```

Do not pass `--radar.hybris-home=` when the variable is empty — an empty CLI flag overrides `application.properties` and forces DEMO.

```bat
cd web
npm start
```

Open [http://localhost:4200](http://localhost:4200).

If `radar.hybris-home` (and `HYBRIS_HOME`) are empty, the collector replays `sample-logs/console-20260809.log` so you can try the UI immediately. If the property is set, sample logs are never used.

### Tests

```bat
mvn test
```

### Build UI

```bat
cd web
npm run build
```

## What you get

- Live ERROR / WARN while the server runs (tail from EOF, never load a multi-GB file)
- Full Java stack + ~30 lines of preceding context
- Duplicates grouped (one issue, count 47)
- Fingerprint on *your* code, not `de.hybris.*`
- Order / product / user / cronjob / catalog version extracted when present
- Classifiers: CronJob, ImpEx, OCC, FlexibleSearch, Solr, Interceptor, Model save
- “Mine only” hides issues with no custom-package frame
- Ignore list in `collector/src/main/resources/application.properties` (Solr ping, session replication, HAC, actuator)
- Mute a fingerprint, copy stack, open an old `console-*.log` and replay it
- Log rotation: a newer `console-YYYYMMDD.log` is picked up without restarting the collector

## Layout

```text
F:\grok\Commerce-Error-Radar\
  AGENTS.md     project context + agent rules (always edit this repo, never a worktree)
  parser/       plain Java + JUnit fixtures
  collector/    Spring Boot 3 API + tailer + SQLite
  web/          Angular 22 dashboard
  sample-logs/  demo console chunk
  Plan/plan.md  original implementation plan
```

## For agents

Read `AGENTS.md` before changing code. Work only in this directory. Never create or use a worktree for Commerce Error Radar.

## Parser rules

| Input | Behavior |
|---|---|
| Line with ` ERROR ` / ` WARN ` | Start event |
| Next lines `at ` / `Caused by:` / `... N more` | Append to event |
| Next normal log line | Close event, persist, fingerprint |
| INFO / DEBUG | Ring buffer only |
| Fingerprint | `ExceptionName` + first `com.yourcompany...` frame |
| Persist | WARN/ERROR + ~30 context lines only |

## Configure

`collector/src/main/resources/application.properties`

```properties
radar.hybris-home=D:/dccp-digitalcommerce-customerportal/core-customize/hybris
radar.custom-package-prefix=com.marwan.radar
radar.tail-from-end=true
radar.ignore-patterns=Solr ping,session replication
```

Use forward slashes in `application.properties`. Flags:

- `--radar.hybris-home=D:/hybris` (omit this flag entirely if you want the file value)
- `--radar.custom-package-prefix=com.marwan.radar`
- `--radar.tail-from-end=false` to replay the current file from the start

## Out of scope

- SAP Commerce Cloud / CCv2
- OpenSearch / Kibana / Dynatrace
- Custom Log4j HTTP appender inside Hybris
- Wrapping `hybrisserver.bat` as a child process
- Storing every INFO line
