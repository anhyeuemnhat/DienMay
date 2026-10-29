# Changes from the reviewed pinned source

Delivery date: 2026-10-29. Upstream baseline: 83bec84a21714275296552a2f201962cc19089ba.
This is a local deliverable; no upstream push or branch mutation was performed.

## Implemented

- Fixed the ten-column/eleven-placeholder StockDao movement defect and added a
  real-method JDBC-double regression plus generic parameter-count guard.
- Moved SQL/config/driver/services into an independently launched backend; added
  explicit HTTP APIs and a JDBC-free desktop transport boundary.
- Replaced static desktop singleton access by instance composition, immutable
  contracts, injected transport/connection boundaries and focused controllers.
- Retained reviewed monetary, inventory, serial, promotion, invoice, warranty,
  role/auth-version and transactional idempotency rules.
- Added expiring opaque API sessions, endpoint permissions, bounded JSON/HTTP,
  loopback-only cleartext, TLS support and safe transport errors.
- Added server-fenced cancellation for uncertain sale/stock requests, client
  persistence-before-send, same-UUID retry and full response-body timeouts.
- Delivered handwritten Swing working screens with six functional menu groups,
  role shortcuts, asynchronous calls, readonly sortable/filterable tables,
  CSV export, printing, adaptive forms and maximized work windows.
- Added additive schema v2, random-password SQL IT fixtures, no public demo account
  passwords, dedicated-test-database safeguards and separate evidence scopes.
- Added executable project convention tooling, offline Ant build, optional Maven
  reactor, UML/navigation/API/runbook documentation and actual GUI screenshots.

## Fixes found during verification

- Headless offline test originally attempted an unavailable display; explicitly
  enabled headless mode for nonvisual suites rather than deleting the test.
- Forced-maximize/undecorated user WM config invalidated GUI geometry assertions;
  reran under the standard Openbox config, which does NOT maximize windows for us.
- Manager home initially scrolled/clipped shortcuts at 1366x768; reduced card
  density/padding, reset scroll position, then allocated spare area to action cards.
- Network response streaming now has a complete deadline and size bound, not only
  an initial connection/header timeout. Oversized requests are rejected before
  pending state is committed locally.

## Deliberately not represented as complete

No full original archive, no baseline build of the entire original repository,
no original NetBeans form/resources/DOCX/legacy migration parity proof, no actual
SQL Server execution, no Windows DPI/peripheral acceptance, no Maven run,
no full Google Java Style certification. See ACCEPTANCE_REPORT.md.
