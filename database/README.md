# Database lifecycle (Microsoft SQL Server only)

Scripts are supplied for review and execution by the user's DBA. None was executed
against real SQL Server in this environment. They must not be described as proven
migrations on live data. All queries use the retained `dm` schema.

## Fresh install

`schema/000_create_database.sql` creates DienMay only when absent. Select that
DB explicitly in your SQL tool, then apply `schema/001_schema.sql`. It defines the
20-table baseline with PK/FK/check/unique/index constraints and inserts schema
history version 1. It refuses an existing SchemaVersion table rather than wiping it.
Apply `migration/007_api_transition.sql`: it adds RequestCancellations as table 21
and version-history row 2. Both version 1 and 2 rows remain; backend readiness
requires version 2 and the transaction opener deliberately selects version 1.

## Existing database

Existing dm v1 can be upgraded by 007 after verified backup, restore rehearsal,
legacy pending reconciliation, legacy-client shutdown and access revocation.
If already version 2, 007 does not recreate the marker table or delete records.
Unexpected partial schema state fails for human review. No auto-migration runs
when starting the application. Pre-dm legacy databases are NOT handled by this
package: original migrations 003-006 were not acquired in full. Do not silently
replace those migrations with a fresh-schema script.

`security/008_runtime_role.sql` defines a backend-only role. DBA separately creates
login/user, manages its secret and adds membership; the script embeds no password.
DDL/migration credentials must not be put in the desktop or used routinely by
employees. App services authorize employee roles independently of this SQL role.

## Bootstrap and synthetic data

DatabaseTool bootstrap <username> works only on an empty Employees table and asks
for a password on the console. No default application login/password is supplied.
`demo/002_demo.sql` contains synthetic products/stock/promotion only, requires an
existing bootstrap manager and empty catalog, and is NOT for a live shop database.
Public upstream demo password hashes were deliberately not included.

`checks/009_reconciliation.sql` is read-only: compare stock with movements,
serialized in-stock units, invoice subtotals and schema version. A zero-mismatch
result is evidence only after the script actually executes on the target DB.

No DROP/truncate/destructive automatic rollback is supplied. Adding a cancellation
fence makes rollback to a client that ignores it unsafe. See DEPLOYMENT.md.
