# Evidence-based audit and closure

The prior five ratings are supplied by the user, not independently certified
historical results. The concrete source observations below came from the pinned
reviewed files, not unseen GUI/diagram files.

| Observation before changes | Actual change | Verification | Remaining gap |
|---|---|---|---|
| StockDao.movement: 10 columns / 11 question marks / 10 arguments | Corrected SQL and statement-shape guard | UnitTests invokes actual DAO with JDBC double | Must execute real INSERT/rollback on SQL Server |
| GUI.App obtains SingletonPattern.system and reads system.db settings | Independent ApiGateway/HttpApiClient, backend-only SqlDatabase | Separate module compilation + architecture test + jdeps report | Real three-host deployment not run |
| InventorySystem exposes public final SQL/service fields | Private composition root and explicit getters | Source and class diagram | No exhaustive whole-repository design audit |
| One work menu plus account menu in reviewed GUI.App | Six functional groups; shared permission registry | GUI menu/role tests; screenshots | Missing legacy screen/form parity |
| No maximize policy in reviewed GUI.App.menu | WorkFrame maximum state and responsive layouts | Both 1366x768 and 1920x1080 actual windows | Windows scaling and multi-monitor behavior |
| Dense one-line source and wildcard imports | Explicit imports, readable statements, project naming/formatter | 77-file checker and full compile | Full Google Style / generated-code tooling unverified |
| Existing UUID/hash locks and conditional stock updates | Preserved service rules; added cancel fence | Controller/JDBC/API tests | SQL concurrency locks/constraints not exercised |
| Existing durable pending-file recovery | Kept file lock/fingerprint, changed profile to API URL | Persistence/tamper/retry tests | Legacy pending-file cutover requires reconciliation |

## Priority and risk decisions

The plan in IMPLEMENTATION_PLAN.md predates implementation. Critical correctness
(placeholder, atomicity, uncertain outcome, auth boundaries) preceded UI polish.
No SQLite/H2 replacement was introduced to obtain artificial integration PASS.
No original test was deleted and then used to claim a full original-suite PASS;
the report states exactly which newly delivered/adapted suites executed.

Remaining blockers: complete source/form/resource acquisition; real SQL Server
regression and transaction races; Windows DPI/printer/scanner; native JDK17;
Maven dependency resolution; TLS deployment/load testing; operational/DBA sign-off.
These are tracked in ACCEPTANCE_REPORT.md and testing runbooks.
