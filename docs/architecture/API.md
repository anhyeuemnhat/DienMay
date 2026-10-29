# API contract (implemented dispatch)

The explicit allowlist is `Operation.java`; `ApplicationApi.java` defines dispatch.
This table is generated from the delivered enum. Every route is POST JSON; only
GET `/health` is different. No SQL text/class names/method names are accepted as
arbitrary executable input. All monetary values are parsed exactly as BigDecimal.
Dates use `yyyy-MM-dd`; Instant values use ISO-8601 UTC. IDs are positive numbers;
optional IDs may be null. Passwords are never part of response DTOs.

Authorization: `Authorization: Bearer <opaque token>` except login. A login result
returns token, employeeId, name, role, mustChange and expiresAt. Those profile
fields are display data; authorization resolves the server-owned token and then
checks the database again. MustChange sessions may only change password/logout.

Success HTTP 200: `{"data": ...}`; void methods use `{"data":null}`.
Error: `{"error":{"code":"...","message":"...","correlationId":"..."}}`.
Headers include no-store, nosniff and X-Request-Id. Errors distinguish 400 invalid
input, 401 absent/expired session, 403 denied/initial-password requirement,
404 unknown path, 405 non-POST, 409 SQL uniqueness/constraint conflict,
413 oversized input, 415 wrong content type, 429 login throttling, 500 unexpected
failure and 503 database unavailability. SQL details and secrets are not returned.
Login rejection from AuthService is a generic 403; do not enumerate accounts.

Limits: 1 MiB request, 8 MiB client response, JSON depth 48 and collection limits.
Client full-response deadline defaults to 35 seconds. No request is automatically
retried by application logic; explicit same-UUID retry is offered for sale/stock.
A 503/timeout is NOT proof that a write rolled back. Resolve/retry the saved request.
`GET /health` is liveness only, `databaseChecked:false`, no credential-free DB probe.

| Operation | Route | Roles | Body fields |
|---|---|---|---|
| LOGIN | `/api/v1/auth/login` | Unauthenticated login | username, password |
| LOGOUT | `/api/v1/auth/logout` | MANAGER, CASHIER, WAREHOUSE | {} |
| PASSWORD | `/api/v1/auth/password` | MANAGER, CASHIER, WAREHOUSE | oldPassword, newPassword |
| SETTINGS | `/api/v1/settings` | MANAGER, CASHIER, WAREHOUSE | {} |
| PRODUCT_SEARCH | `/api/v1/products/search` | MANAGER, CASHIER, WAREHOUSE | text |
| PRODUCT_FIND | `/api/v1/products/find` | MANAGER, CASHIER, WAREHOUSE | code |
| PRODUCT_GET | `/api/v1/products/get` | MANAGER, CASHIER, WAREHOUSE | id |
| PRODUCT_SAVE | `/api/v1/products/save` | MANAGER | item: Item |
| SERIALS | `/api/v1/products/serials` | MANAGER, CASHIER, WAREHOUSE | id, location (KHO / QUAY) |
| LOOKUPS | `/api/v1/lookups/list` | MANAGER, CASHIER, WAREHOUSE | type (category / brand / vendor) |
| LOOKUP_SAVE | `/api/v1/lookups/save` | MANAGER | type, id?, name |
| CUSTOMERS | `/api/v1/customers/search` | MANAGER, CASHIER | text |
| CUSTOMER | `/api/v1/customers/get` | MANAGER, CASHIER | id |
| CUSTOMER_SAVE | `/api/v1/customers/save` | MANAGER, CASHIER | id?, name, phone, address |
| VENDORS | `/api/v1/vendors/list` | MANAGER, WAREHOUSE | {} |
| VENDOR_SAVE | `/api/v1/vendors/save` | MANAGER | id?, name, phone, address, enabled |
| EMPLOYEES | `/api/v1/employees/list` | MANAGER | {} |
| EMPLOYEE_SAVE | `/api/v1/employees/save` | MANAGER | id?, username, name, phone, role, enabled, password (empty retains password) |
| PROMOTIONS | `/api/v1/promotions/list` | MANAGER, CASHIER | {} |
| PROMOTION_SAVE | `/api/v1/promotions/save` | MANAGER | id?, code, name, from, to, percent, enabled |
| QUOTE | `/api/v1/sales/quote` | MANAGER, CASHIER | lines: ItemQuantity[], code, manual |
| CHECKOUT | `/api/v1/sales/checkout` | MANAGER, CASHIER | request: SaleRequest |
| SALE_CANCEL | `/api/v1/sales/cancel` | MANAGER, CASHIER | request: SaleRequest |
| SALE_RESOLVE | `/api/v1/sales/resolve` | MANAGER, CASHIER | request: SaleRequest |
| INVOICE | `/api/v1/sales/get` | MANAGER, CASHIER | id |
| INVOICES | `/api/v1/sales/search` | MANAGER, CASHIER | text, from, to |
| RECEIVE | `/api/v1/inventory/receive` | MANAGER, WAREHOUSE | request: InventoryRequest |
| TRANSFER | `/api/v1/inventory/transfer` | MANAGER, WAREHOUSE | request: InventoryRequest |
| STOCK_CANCEL | `/api/v1/inventory/cancel` | MANAGER, WAREHOUSE | request: InventoryRequest |
| STOCK_RESOLVE | `/api/v1/inventory/resolve` | MANAGER, WAREHOUSE | request: InventoryRequest |
| STOCK_HISTORY | `/api/v1/inventory/history` | MANAGER, WAREHOUSE | sku |
| SERIAL_HISTORY | `/api/v1/inventory/serial-history` | MANAGER, WAREHOUSE | serial |
| WARRANTIES | `/api/v1/warranties/search` | MANAGER, CASHIER | text |
| CLAIM_RECEIVE | `/api/v1/warranties/receive` | MANAGER, CASHIER | warranty, fault |
| CLAIMS | `/api/v1/warranties/claims` | MANAGER, CASHIER | warranty? |
| CLAIM_TRANSITION | `/api/v1/warranties/transition` | MANAGER | claim, expected, next, notes |
| CLAIM_HISTORY | `/api/v1/warranties/history` | MANAGER, CASHIER | claim |
| REPORT | `/api/v1/reports/query` | MANAGER | type (summary / employee / best), from, to |
| LOW_STOCK | `/api/v1/reports/low-stock` | MANAGER, WAREHOUSE | {} |

## Request DTOs

`SaleRequest`: requestId (UUID), customerId (nullable), payment (CASH/TRANSFER),
manualDiscount, promotionCode, expectedDiscount and lines. Each ItemQuantity has
productId, sku, name, expectedPrice, quantity and serials. The server reloads item
price/enabled state/stock policy and verifies expected price/discount at checkout;
client display names are not trusted as persisted product snapshots.

`InventoryRequest`: id (UUID), productId, quantity, serials, reference, vendorId
(nullable except receiving), unitCost, from (nullable for receiving), to. Receive
requires from=null, to=KHO and a vendor; transfer requires distinct KHO/QUAY sides.
Client preflights serialized size BEFORE persisting an unsendable pending request.

Product save uses the delivered Item record fields. New items use id=0 and a
revision placeholder; edits carry the revision read from the backend. Backend
rejects concurrent stale updates and a serial-policy change after stock history.

## Replay / cancellation semantics

For checkout/receive/transfer, request ID + canonical fingerprint + authenticated
employee identify one operation. Replaying the same request returns its committed
result without changing stock again. Reusing an ID with another payload/owner is
rejected. Sale resolve yields Sale or null; stock resolve yields boolean. Negative
resolve retains pending state. Cancel true means a durable cancellation fence is
committed; false means the transaction was already committed, so resolve it.
Cancellation is not a financial refund or stock reversal.

Customer/vendor/product/account/promotion CRUD and warranty intake do not offer
this replay guarantee. After losing their reply, search current state before
submitting again. This limitation is explicit in the runbook and acceptance.

Vendor lookups have an additional service-level restriction (manager/warehouse),
even though generic LOOKUPS also covers categories/brands for cashiers.
