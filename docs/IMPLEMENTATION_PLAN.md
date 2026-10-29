# Implementation plan - recorded before source changes

Baseline: anhyeuemnhat/DienMay main, verified via GitHub connector at
83bec84a21714275296552a2f201962cc19089ba (commit 2026-10-20).
The shell cannot resolve external hosts; GitHub connector reads remain available.
A whole-repository clone/archive has NOT been acquired. Retrieval coverage must
be recorded rather than represented as a successful clone.

## Evidence-based audit

- StockDao.movement: ten INSERT columns/arguments but eleven placeholders.
- GUI.App depends on Code.InventorySystem and reads SQL connection settings.
- InventorySystem exposes public service and SQL fields; static SingletonPattern
  supplies the desktop-wide instance.
- GUI.App.menu groups all work in one menu; no maximize policy there.
- AuthService verifies roles/auth version on each service call; preserve this.
- SalesService and InventoryService already use application locks, conditional
  stock updates and request fingerprints; preserve atomic database boundaries.
- No SQL Server endpoint or driver and no Windows environment is provided.

## Ordered implementation

1. Retrieve/record source from the pinned commit through the connector.
2. Fix movement placeholders and add a JDBC statement-shape regression test.
3. Separate common data contracts, backend services/SQL, and Swing transport.
   Use constructor-injected application composition, not a process-global DB.
4. Provide an independent HTTP server with bounded requests, opaque expiring
   sessions, server-side authorization, explicit endpoint allowlist, and safe
   error mapping. Production transport requires TLS; local HTTP is loopback only.
5. Adapt actual screens to remote calls and asynchronous Swing workers. Group
   navigation by System, Catalog, Update, Processing, Lookup, Reports.
6. Introduce responsive maximized work windows and table search/export.
7. Keep SQL Server schema and non-destructive migrations. Never execute against
   a real database here. No embedded production password.
8. Add offline domain/JDBC-proxy/API/architecture/UI tests and SQL Server-only
   integration commands that explicitly skip without a configured test database.
9. Document source-derived textbook references with PDF page AND printed slide
   numbers where different; update UML from the classes actually delivered.
10. Compile, test, inspect UI in Xvfb/Openbox at both requested resolutions,
    retain genuine logs/screenshots, then verify the delivery ZIP contents.

## Acceptance and rollback

A successful offline test is NOT SQL Server integration evidence. Linux/Xvfb is
NOT Windows scaling evidence. Google-style recommendations are not a claim that
an unavailable external formatter has run. Retain these distinctions in the
acceptance report. Keep original GitHub main unchanged; deliver local files only.
Migrations are additive; rollback means restore the application release and
use a verified backup if a later schema change requires it. Never drop user data.

## Refinement before pending-workflow implementation

A negative status read alone is not a cancellation fence for a delayed request.
Add an append-only RequestCancellations ledger (schema version 2) under the SAME
application lock as checkout/stock execution. A successful cancellation response
allows the client to clear its pending file; any delayed retry is rejected.
No automatic retry is added for non-idempotent CRUD/warranty-intake endpoints.
