# Postman

Collection for the collector on **http://localhost:8088**. Start the collector before you send anything.

## Import

1. Postman → **Import**
2. Add both files in this folder:
   - `Commerce-Error-Radar.postman_collection.json`
   - `Commerce-Error-Radar.postman_environment.json`
3. Select the environment **Commerce Error Radar — local** in the top-right picker.

Or drag the collection file onto the Postman sidebar.

## Suggested order

1. **Current run** — saves `runId`
2. **List issues** — saves `fingerprint` from the first row
3. **Get one issue** / **Mute issue** — uses that fingerprint

Fingerprints contain `@`. Detail and mute must use the `fingerprint` query param, not a path segment.

`Open log` ships with an example Hybris console path. Change it to a file on your machine, or replay `sample-logs/console-20260809.log` from the repo.

`SSE stream` stays open until you cancel it.
