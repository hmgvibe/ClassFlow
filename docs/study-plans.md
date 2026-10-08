# Study plans / 學習計劃

The Android and Nextcloud apps include a separate Study plans page. The date strip
shows five days; use the previous/next controls or date picker to choose another
date. Plans are grouped by their **start time**:

- Morning: before 12:00.
- Afternoon: from 12:00 to before 18:00.
- Evening: from 18:00 onward.

Create a plan with a title, date, manual start/end time and optional notes. You may
link it to one course, homework item or exam. Linked timetable/agenda times are
shown for reference only and never change the plan's own time. Deleting a linked
item does not delete the plan; a missing-reference message is displayed instead.

## Upgrade / 升級

Install the original Android 2.0.0 APK over the existing app; do **not** uninstall first.
Room migrates the existing database from version 1 to 2 without clearing data.

Update the server-side ClassFlow Nextcloud app to 2.0.0 as well. Its study-plan migration
adds `classflow_studies`; the original courses, slots and agenda tables are not
replaced. Back up the Nextcloud database before deploying an update. For a Docker
installation, run the Nextcloud app upgrade through the actual Nextcloud
container's normal update/OCC workflow, not host PHP.

Until the server supports study plans, Android retains them locally and continues
syncing courses/agenda. It displays an upgrade message instead of sending unsupported
study mutations. Study plans use the same version/conflict-resolution flow as other
records after the server is upgraded.

新版 Android 可直接覆蓋安裝，請勿先卸載。伺服器上的 ClassFlow App 也需要更新到
2.0.0 並執行 Nextcloud 升級／資料庫遷移，才可同步學習計劃。更新前請備份資料庫；
尚未更新伺服器時，計劃會保留在手機，不會被舊伺服器的同步結果清除。

## Verification

- Android unit tests cover time boundaries, local dates, manual-time preservation,
  missing references, pending changes, deletions and conflict copies.
- `node --test scripts/test-study-migration.mjs` checks the migration SQL against the
  generated Room schema and verifies existing data/pending mutations survive.
- Android instrumentation tests cover the real Room upgrade and the Compose UI.
  They require an attached emulator/device and are separate from unit tests.
- Nextcloud: run `node --test tests/study.test.mjs` and
  `php tests/study-service.php` inside `nextcloud/classflow` for pure logic checks.
  These do not substitute for a real Nextcloud database/integration test.
