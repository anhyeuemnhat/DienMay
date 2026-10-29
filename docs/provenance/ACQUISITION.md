# Acquisition and parity limitations

GitHub Connector verified `main` at 83bec84a21714275296552a2f201962cc19089ba.
Commit timestamp: 2026-10-20T06:25:15Z. Root tree:
06e16223ad1d5f651fbb7b80431c2055e2ead386.
Every file read was pinned to that commit; the prior reference SHA was not simply
assumed current. The CSV records 36 reviewed files and upstream blob IDs.
These are upstream Git SHA-1 blob identifiers, NOT hashes of modified output files.

The container could not resolve external hosts for git/archive/dependency downloads.
Individual text reads through the connector worked. No complete clone or archive
was acquired; source was adapted locally from those reviewed texts. The delivered
folder is a standalone project with all of ITS active Java sources, not a complete
copy of every upstream asset or a reproducible upstream checkout.

## Not acquired/reviewed completely

- Most original GUI classes and every NetBeans `.form` file.
- Original `Images/` binary resources.
- `Project.docx`, `usecasedescriptions.docx` and original UML diagram files.
- Original README/build.xml contents and complete test files byte-for-byte.
- Legacy migrations 003_stage_legacy, 004_apply_reviewed_legacy,
  005_reconciliation and 006_permissions; names/tree metadata were seen only.

No missing file was fabricated and labeled as original. The new UI is handwritten
and self-contained, with new SQL/runbooks/UML clearly marked as delivery material.
Not acquiring original `.form` means Designer preservation and full original UI
function parity cannot be accepted. This is a material deviation from the user's
whole-repository preservation requirement, not just an environmental test skip.

## Source-to-delivery mapping

| Original | Delivered change |
|---|---|
| Code model records/utilities | common-dto/.../model; package/style, immutable contracts, retained rules |
| Service.InventoryService.Request | common model InventoryRequest, independent of backend |
| ImplementationDB.SQL | server/db/SqlDatabase; backend-only credentials/connection lifecycle |
| Other ImplementationDB DAOs | server/db; parameter guard and movement defect correction |
| Business/auth services | server/service; API sessions, role validation, request cancellation |
| Service.PendingStore | client/state; no service dependency, durable recovery retained |
| Code.InventorySystem | server composition root with private final fields |
| Code.SingletonPattern | removed; client AppContext and explicit server instances |
| Code.Store / Warehouse | redundant construction wrappers removed, services composed directly |
| GUI.App / Ui | concepts reviewed, new API-backed application/view utilities implemented |
| Other GUI screens | new handwritten working views, not verified NetBeans-compatible adaptations |
| Original unit/SQL tests | reviewed scenarios adapted; new explicit test runners, not every old file retained |
| database/001_schema.sql | existing dm schema structure retained; additive version 2 supplied |
| database/002_demo.sql | catalog/stock demonstration; public default credentials removed |

No GitHub write action was used. No live user database was accessed, modified,
migrated or deleted. A complete original baseline build was not possible and is
not represented as having passed. Upstream licensing was not fully inspected;
retain attribution and obtain repository-owner approval before redistribution
outside this requested project handover.
