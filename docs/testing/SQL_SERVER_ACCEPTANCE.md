# SQL Server acceptance (NOT RUN here)

Use a disposable dedicated database; never point this runner at a shop database.
A DBA creates e.g. DienMay_Test, applies schema/001_schema.sql and
migration/007_api_transition.sql and grants test-fixture access. The test principal needs the runtime grants plus
UPDATE on dm.Warranties solely to simulate historical expiry; do NOT add that
fixture privilege to the production runtime role. Do not
install demo. Install the Microsoft JDBC jar in backend-server/lib.

Set DIENMAY_DB_URL/USER/PASSWORD to that database, DIENMAY_IT_ALLOW_WRITES=YES and
DIENMAY_IT_DATABASE=DienMay_Test. `ant sql-it` refuses an actual DB_NAME different
from the exact allowed name or a name not ending _Test. The runner creates random
prefixed fixtures and random test passwords, retaining audit rows. It never
cleans production records. The DBA can drop the whole disposable database after
review; the application does not do so automatically.

Collect stdout, SQL Server/JDBC versions, schema history, runtime role permissions,
server collation/isolation settings and a copy of read-only reconciliation output.
Require all SqlServerIT cases to pass, including three injected rollback phases,
concurrent duplicate and overselling cases, serial collision rollback, cancellation
before late execution and concurrent cancel-versus-checkout, and HTTP checkout.

Then run manual end-to-end employee workflows on separate client/backend hosts
with TLS: bootstrap/login/password rotation; product/customer/vendor/promotions;
receive + transfer + sell with and without serials; lookup/reprint; warranty intake
and transitions; report totals. Simulate response loss/restart, keep pending UUIDs,
re-authenticate and reconcile. Inspect ledger totals and rollback after failures.

Test constraint rejection with duplicate SKU/barcode/serial; disabled products and
vendors; insufficient stock; identical source/destination; permission changes mid-
session; expired promotions; stale product revision; malformed serial count; date
ranges; no accidental double document on retry. Do not mark this gate complete
using an H2/SQLite replacement, a JDBC proxy, or fixture HTTP responses.
