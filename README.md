# Commerce Error Radar

Local error inbox for **SAP Commerce (Hybris) + Spring** on Windows.

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

After a clone, copy the example properties and set your machine values (the copy is gitignored):

```bat
copy collector\src\main\resources\application.properties.example collector\src\main\resources\application.properties
```

Edit `radar.hybris-home` and `radar.custom-package-prefix` in that file. Leave `radar.hybris-home` empty only when you want the bundled `sample-logs` demo.

Or set the same values in the environment:

```bat
set HYBRIS_HOME=D:/hybris
set RADAR_PREFIX=com.yourcompany
```

## Run

**One-click (two extra terminals):** `start.bat`

Or three terminals:

```bat
cd /d D:\hybris\hybris\bin\platform
hybrisserver.bat
```

```bat
mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=--radar.hybris-home=D:/hybris --radar.custom-package-prefix=com.yourcompany"
```

Do not pass `--radar.hybris-home=` when the variable is empty — an empty CLI flag overrides `application.properties` and forces DEMO.

```bat
cd web
npm start
```

Open [http://localhost:4200](http://localhost:4200).

The collector includes **Spring Boot DevTools**. After a Java change, recompile onto the classpath and the app restarts in a couple of seconds (same SQLite session is resumed). In IntelliJ / VS Code, turn on compile-on-save / automatic build. From another terminal:

```bat
mvn -pl collector -am compile
```

DevTools is `optional` and is stripped from the packaged jar. It is not true JVM hot-swap — it is a fast process restart.

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

- Live ERROR / WARN for the current log-file session (one `console-*.log` = one session; History lists other files)
- Tail from EOF, never load a multi-GB file
- Full Java stack + ~30 lines of preceding context
- Duplicates grouped (one issue, count 47)
- Fingerprint on *your* code, not `de.hybris.*`
- Order / product / user / cronjob / catalog version / `.impex` extracted when present — click a chip to see everything for that order or ImpEx file
- Copy the selected issue as Markdown or a Teams-ready paste
- Classifiers: CronJob, ImpEx, OCC, FlexibleSearch, Solr, Interceptor, Model save
- Ignore list in `collector/src/main/resources/application.properties` (Solr ping, session replication, HAC, actuator)
- Mute a fingerprint, copy stack, open an old `console-*.log` and replay it
- Optional Windows toast + tab favicon badge when a new ERROR arrives while this tab is in the background (bell in the header; collector fires the toast, UI draws the badge)
- Log rotation: a newer `console-YYYYMMDD.log` is picked up without restarting the collector

## Layout

```text
Commerce-Error-Radar/
  README.md                 how a human runs the app
  pom.xml                   Maven parent (Java 21)
  parser/                   domain: parse / fingerprint / classify (no Spring)
  collector/                Spring Boot adapters: web, SQLite, tailer
  web/                      Angular 22 dashboard
  sample-logs/              demo console chunk
  start.bat / start-collector.bat / start-ui.bat
```

`collector/src/main/resources/application.properties` is local (gitignored). Start from `application.properties.example`.

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

Copy `collector/src/main/resources/application.properties.example` to `application.properties` and set:

```properties
radar.hybris-home=D:/hybris
radar.custom-package-prefix=com.yourcompany
radar.tail-from-end=true
radar.notify-on-error=false
radar.ignore-patterns=Solr ping,session replication
```

Use forward slashes in `application.properties`. Flags:

- `--radar.hybris-home=D:/hybris` (omit this flag entirely if you want the file value)
- `--radar.custom-package-prefix=com.yourcompany`
- `--radar.tail-from-end=false` to replay the current file from the start
- `--radar.notify-on-error=true` to default the header bell on (the UI still persists the toggle)

## Out of scope

- SAP Commerce Cloud / CCv2
- OpenSearch / Kibana / Dynatrace
- Custom Log4j HTTP appender inside Hybris
- Wrapping `hybrisserver.bat` as a child process
- Storing every INFO line
