# Commerce Error Radar

Local error inbox for **SAP Commerce (Hybris) + Spring** on your Windows machine.

You start `hybrisserver` as usual. This app tails today’s Tomcat console log, keeps `ERROR` / `WARN` events, groups duplicates, and shows them in a browser UI that does not scroll away.

**Scope: local only.** No SAP Commerce Cloud, no OpenSearch, no Dynatrace, no custom Hybris extension.

---

## Goal

Turn a noisy `console-YYYYMMDD.log` into an issue list you can act on:

- live ERROR / WARN while the server runs
- full Java stack + ~30 lines of context
- duplicates grouped (one issue, count 47)
- fingerprint on *your* code, not `de.hybris.*`
- extract order / product / cronjob IDs when present

```text
hybrisserver.bat
       │
       ▼
hybris\log\tomcat\console-YYYYMMDD.log
       │  tail
       ▼
Spring Boot collector  →  SQLite  →  http://localhost:5173
```

Do **not** wrap `hybrisserver.bat`. On Windows the useful stream is the log file.

---

## Stack

| Piece | Choice |
|---|---|
| Collector + API | Java 21 + Spring Boot 3 |
| Ingest | Tail `hybris\log\tomcat\console-*.log` |
| Store | SQLite |
| Live updates | Server-Sent Events (SSE) |
| UI | Vite + React + TypeScript + Tailwind |
| Parser | Pure Java module, tested with fixture logs |

---

## Target layout

Create this structure as you go. Do not build everything in one dump.

```text
commerce-error-radar/
  README.md
  collector/                 # Spring Boot 3 app
    src/main/java/.../tail/
    src/main/java/.../api/
    src/main/java/.../store/
    src/main/resources/application.yml
  parser/                    # no Spring dependency
    src/main/java/.../HybrisLogParser.java
    src/main/java/.../StackFingerprint.java
    src/main/java/.../BusinessIdExtractor.java
    src/test/resources/fixtures/
  web/                       # Vite + React
    src/
  sample-logs/               # real chunks copied from your console log
```

**Fill these in before you start coding:**

| Setting | Example | Yours |
|---|---|---|
| Hybris home | `D:\hybris` | `_TBD_` |
| Custom package prefix | `com.yourcompany` | `_TBD_` |
| App port | `8088` | `8088` |
| UI port | `5173` | `5173` |

---

## Implementation plan

Do the phases **in order**. Each phase has a clear “done” check. Do not start the next phase until that check passes.

---

### Phase 0 — Project setup

**Why:** empty repo you can run, test, and open in IntelliJ.

**Do:**

1. Create the folder `commerce-error-radar` (this repo).
2. Create two Maven modules (or one parent + two modules):
   - `parser` — plain Java, JUnit 5
   - `collector` — Spring Boot 3, depends on `parser`
3. Create `web` with Vite + React + TypeScript.
4. Add `.gitignore` for `target/`, `node_modules/`, `*.db`, `.idea/`.
5. Confirm Java 21 and Node 20+ are on PATH.

**Done when:**

- `mvn -pl parser test` runs (even with zero tests)
- `npm run dev` in `web/` opens a blank page
- IntelliJ can import the Maven parent

---

### Phase 1 — Find and tail the Hybris log

**Why:** ingest must work before any parser or UI.

**Do:**

1. Resolve Hybris home from `--hybris-home` or `application.yml`.
2. Locate the newest file matching:

   ```text
   <HYBRIS_HOME>\hybris\log\tomcat\console-*.log
   ```

3. Tail it from the end (do not replay the whole multi-GB file on startup).
4. Print each new line to the collector console first (no DB yet).
5. Handle log rotation: if a newer `console-YYYYMMDD.log` appears, switch to it.

**Done when:**

- You start `hybrisserver.bat`, then start the collector
- New lines from Hybris appear in the collector console within ~1 second
- Restarting Hybris (new log file) is picked up without restarting the collector

---

### Phase 2 — Parser + fixtures (the hard part)

**Why:** Hybris logs are multi-line. If the parser is wrong, the UI is useless.

**Do:**

1. Copy 3–5 **real** ERROR chunks from your local console log into `parser/src/test/resources/fixtures/`:
   - one OCC / controller stack
   - one CronJob failure
   - one ImpEx error
   - one `WARN`
   - one noisy `de.hybris` only stack (to prove we collapse it)
2. Write `HybrisLogParser`:
   - a line containing ` ERROR ` or ` WARN ` starts an event
   - following lines that start with `at `, `Caused by:`, tab + `...`, or `... N more` belong to that event
   - any other normal log line closes the previous event
3. Write `StackFingerprint`:
   - exception type + first frame whose class starts with your package prefix
   - if no custom frame exists, fall back to the first non-framework frame
   - ignore: `de.hybris.*`, `org.springframework.*`, `org.apache.catalina.*`, `org.apache.tomcat.*`, `java.base`, `jdk.internal`
4. Write `BusinessIdExtractor` (simple regex):
   - order code, product code, user id, cronjob name, catalog version when present
5. Keep a ring buffer of the last ~100 lines in the tailer; attach ~30 lines of context when an ERROR/WARN closes.

**Done when:**

- Unit tests pass on every fixture
- Two identical NPEs in the same custom class produce the **same** fingerprint
- A Hybris-only stack does not fingerprint on `de.hybris.platform...`

---

### Phase 3 — SQLite + REST API

**Why:** the browser needs history, not only a live stream.

**Do:**

1. Add SQLite (JDBC) and Flyway (or schema-on-startup).
2. Tables:

   ```text
   runs     id, hybris_home, log_path, started_at, ended_at
   events   id, run_id, ts, level, logger, thread, message,
            exception, fingerprint, raw_text, context_text,
            business_ids_json
   issues   fingerprint PK, title, level, count, first_seen, last_seen
   ```

3. Persist **only** WARN/ERROR events. Never store every INFO line.
4. REST endpoints:

   | Method | Path | Purpose |
   |---|---|---|
   | GET | `/api/runs/current` | active tail status |
   | GET | `/api/issues` | grouped issues, newest first |
   | GET | `/api/issues/{fingerprint}` | one issue + recent events |
   | GET | `/api/events?level=&q=` | flat search |

5. Optional query params: `level=ERROR`, `q=CartFacade`, `mineOnly=true` (your package only).

**Done when:**

- You can hit `/api/issues` with curl/Postman after a real ERROR appears in the log
- Restarting the collector still shows previous issues from SQLite

---

### Phase 4 — Live dashboard (minimum UI)

**Why:** this is the product. Keep v1 ugly-but-usable, not a design playground.

**Do:**

1. Vite dev server proxies `/api` and `/api/stream` to Spring Boot (`8088`).
2. Layout:
   - left: issue list (title, count, last seen, level badge)
   - right: selected issue (message, stack, context, business IDs)
3. SSE endpoint `/api/stream` pushes new events; UI prepends / updates counts without refresh.
4. Filters: ERROR / WARN / all. Search box for class or message.
5. Button: copy stack to clipboard.

**Done when:**

- Browser at `http://localhost:5173` shows new Hybris errors live
- Clicking an issue shows the full stack with Hybris frames visually collapsed
- Refreshing the page still shows history from SQLite

---

### Phase 5 — Grouping polish + Commerce classifiers

**Why:** generic Java grouping is not enough for Hybris.

**Do:**

1. Classify issue type from message/exception:
   - `CRONJOB`
   - `IMPEX`
   - `OCC`
   - `FLEXIBLE_SEARCH`
   - `SOLR`
   - `INTERCEPTOR` / `MODEL_SAVE`
   - `OTHER`
2. Add UI chips to filter by those types.
3. Title format examples:
   - `CronJob solrIncrementalUpdate failed — ModelNotFoundException`
   - `OCC DefaultCartFacade.addToCart — NPE`
   - `ImpEx products-delta.impex — unknown catalog version`
4. “Mine only” toggle: hide issues with no custom-package frame.
5. Hide known noise (optional ignore list in `application.yml`):
   - Solr ping
   - session replication
   - HAC / actuator probes

**Done when:**

- A CronJob that fails 14 times is **one** issue with count 14
- You can filter “only OCC” and “only my packages”
- A noisy Hybris WARN you added to the ignore list no longer appears

---

### Phase 6 — Daily-driver extras (only after Phase 5)

Do these only if you use the app for a real week.

1. “Open log file…” to analyze an old `console-*.log` without tailing.
2. Start-at-end vs replay-whole-file toggle (replay is for historical dumps only).
3. Mark issue read / mute fingerprint for this run.
4. Dark UI that is easy on the eyes during long server boots.
5. Tray or a single `start.bat` that opens the browser and starts the collector.

**Done when:** you prefer this UI over scrolling the console for real debugging.

---

## Suggested build order (checklist)

Use this as the working backlog:

- [ ] Phase 0 — Maven parent + `parser` + `collector` + Vite app
- [ ] Phase 1 — Tail newest `console-*.log`, handle rotation
- [ ] Phase 2 — Parser fixtures + fingerprint + business IDs
- [ ] Phase 3 — SQLite schema + REST
- [ ] Phase 4 — React issue list + detail + SSE
- [ ] Phase 5 — CronJob / ImpEx / OCC classifiers + mine-only
- [ ] Phase 6 — Open file, mute, start script

---

## How you will run it (once built)

Terminal 1 — Commerce, as you already do:

```bat
cd /d D:\hybris\hybris\bin\platform
hybrisserver.bat
```

Terminal 2 — collector:

```bat
cd collector
mvn spring-boot:run -Dhybris.home=D:\hybris
```

Terminal 3 — UI (dev):

```bat
cd web
npm run dev
```

Open `http://localhost:5173`.

Later you can serve the built UI from Spring Boot so only one process is needed.

---

## Parser rules (keep this stable)

| Input | Behavior |
|---|---|
| Line with ` ERROR ` or ` WARN ` | Start event |
| Next lines `at ` / `Caused by:` / `... N more` | Append to event |
| Next normal log line | Close event, persist, fingerprint |
| INFO / DEBUG | Keep only in the in-memory ring buffer |
| Fingerprint | `ExceptionName` + first `com.yourcompany...` frame |
| Persist | WARN/ERROR + ~30 context lines only |

---

## Out of scope (do not build)

- SAP Commerce Cloud / CCv2
- OpenSearch / Kibana / Dynatrace
- Custom Log4j HTTP appender inside Hybris
- Wrapping `hybrisserver.bat` as a child process
- Elasticsearch, Kafka, Grafana, Docker Compose
- Electron / JavaFX desktop shell
- Storing every INFO line

---

## Risks to handle early

| Risk | Mitigation |
|---|---|
| Console log is huge | Tail from EOF; never load the whole file in Phase 1 |
| Multi-line stacks get split wrong | Fixtures first; parser is its own module |
| Log rotation at midnight / restart | Watch the `tomcat` directory for a newer `console-*.log` |
| INFO flood | Ring buffer only; SQLite gets WARN/ERROR |
| Fingerprint on Hybris frames | Explicit ignore prefixes + unit tests |

---

## Next action

Start **Phase 0**, then **Phase 1** against your real Hybris home.

Before coding, set:

1. Hybris home path  
2. Custom package prefix (`com.???`)
