# Architecture and design decisions

## Deployment tiers, not renamed folders

Tier 1 is a desktop JVM containing `client-swing.jar` and `common-dto.jar`.
Tier 2 is another JVM containing `backend-server.jar`, `common-dto.jar` and the
Microsoft JDBC driver. Tier 3 is Microsoft SQL Server. The independent backend
listens on an API socket; no database URL, JDBC driver, DAO or SQL connection is
needed by the desktop. `ant compile-client` has only common on its classpath.

Logical layers also exist inside each process. They do not, by themselves,
constitute the deployment tiers. `common-dto` is a shared library, not a fourth
tier/service. SQL Server stays the production database; HTTP fixtures/JDBC doubles
are confined to `tests/`. No production in-memory database fallback exists.

## Composition and OO changes

`InventorySystem` is a backend composition root with private final dependencies
and explicit accessors. The desktop owns an instance of `AppContext`; there is
no process-global `SingletonPattern` or client `InventorySystem.db` reference.
The original Store/Warehouse classes only wrapped service construction. Their
roles are composed directly by InventorySystem rather than retaining redundant
wrappers across the network. Their removal is recorded in provenance, not hidden.

`ApiGateway` enables the UI/controllers to depend on an application boundary;
`HttpApiClient` implements it. `ApiHandler` separates HTTP concerns from dispatch;
`ApplicationApi` implements it. `SqlDatabase.Factory` supplies JDBC connections
and `Work<T>` scopes unit-of-work ownership; test doubles exercise failure paths.
`SalesService.FailurePoint` injects transaction failures only through constructors.
Concrete DAO injection is supported by SalesService; no artificial interface was
created for every trivial class. These are deliberate abstraction boundaries,
not a claim of exhaustive SOLID certification.

`Item` and `Sale` are immutable data snapshots; ItemQuantity/SaleRequest protect
collections and validate price/quantity/request identity. `Cart` owns a private
line map and exposes a copied list. `PendingStore` owns one OS file lock. Closing
it releases the lock but never silently discards an unresolved request.
Inheritance is limited to reusable Swing views (WorkFrame, RowsFrame, StockFrame,
DashboardFrame); business rules use composition. SaleController/StockController
separate durable workflow from widgets. Shared input checks improve feedback;
backend service validation remains authoritative.

## Why no Spring Boot / ORM

The existing project already implements its transaction semantics with JDBC,
SQL Server application locks and atomic stock predicates. Retaining that service
logic behind a small explicit HTTP dispatcher reduces semantic migration.
The JDK server keeps the offline build possible. Trade-offs: there is no mature
framework-provided observability, distributed session store, connection pool or
schema-migration engine. The custom JSON codec and HTTP layer have bounded-input
and contract tests but still require security/load review before production.
Do not mistake passing offline tests for production certification.

## Authentication and trust

Login validates stored PBKDF2 hashes. The server owns opaque UUID sessions with
an eight-hour lifetime and a maximum number of in-memory sessions. Services
recheck DB Role, Enabled, AuthVersion and MustChange. Password change invalidates
the prior session; account administration increments AuthVersion. A client cannot
send an employee ID/role in lieu of a bearer token. UI menu filtering is convenience,
not authorization. Login throttling exists by peer and per-account failed attempts.

All credentials for SQL stay on the backend. API tokens live in client RAM, not
pending files. Client HTTP is loopback-only; nonlocal endpoints require HTTPS.
The server likewise rejects nonloopback plaintext binding. TLS certificates/trust
and host/network policy remain deployment responsibilities. `/health` is liveness,
not readiness; it explicitly returns `databaseChecked:false`. ServerMain performs
a schema-v2 DB check before opening its listening socket.

## Transactions and uncertain results

Checkout obtains the same SQL Server application lock for a request UUID on each
attempt, checks existing owner+fingerprint, locks products in deterministic order,
checks fresh price/promotion/serial state, then writes invoice, details, stock,
movement ledger, units and warranty in one transaction. Stock decrements are
conditional on sufficient quantity. Stock receiving/transfers use a corresponding
request lock and atomic transaction. SQL unique/check/FK constraints are retained.

The corrected StockDao movement SQL has ten columns and ten placeholders. A
JDBC-double regression invokes the real method and verifies ten bound arguments;
the general statement helper catches mismatched placeholder counts before prepare.
This proves statement shape, not SQL Server query execution.

Client controllers save the canonical request and UUID before sending. A timeout,
connection loss, invalid response or uncertain commit retains that same file.
Retry never generates a new UUID. Resolve returns a committed result when present.
An absent result DOES NOT justify clearing the request: an older network request
might still arrive. Explicit cancel locks that UUID and writes an append-only
RequestCancellations marker. Only a confirmed cancellation permits clearing; a
late execution must check the marker and fail. Committed transactions cannot be
cancelled by this mechanism; resolve their receipt instead.

The durable cancellation path is additional to the baseline; its real database
locking/race behavior is included in SqlServerIT but NOT executed here. CRUD and
warranty intake do not have request-id replay protection and are not automatically
retried. After an uncertain result for these operations, search/reconcile first.

## Operational limits

One backend instance owns sessions; restart logs users out. Multi-instance session
sharing is not implemented. Requests are bounded at 1 MiB; client responses at
8 MiB; JSON depth/count limits apply. Broad searches are capped by the existing
SQL query limits (500/1000), not a full pagination UI. API POST endpoints are
command-oriented rather than a fully REST-resource model. No refunds/returns or
accounting logic beyond the reviewed upstream services is invented.
