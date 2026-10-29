# Screen / use-case / event mapping

The six groups are Hệ thống, Danh mục, Cập nhật, Xử lý, Tra cứu, Báo cáo.
This adapts the functional grouping shown in ptud p.159; the textbook's example
uses Tìm kiếm/Báo biểu. It does not dictate DienMay's specific role matrix;
that comes from the reviewed source and the user's requirements.

M=manager, C=cashier, W=warehouse. Shared API permission registry filters the menu
and shortcut cards; server dispatch and services independently enforce permissions.
Every main view inherits WorkFrame and opens maximized. Small forms use EditDialog,
not exclusive full screen. A scrollable editor can shrink to the work area.

| Use case | GUI / group | APIs -> service | Permission / key events |
|---|---|---|---|
| Login / initial password | MainUI, mandatory EditDialog | LOGIN/PASSWORD -> AuthService | Enter/click Login off EDT; must-change prevents home |
| Home / logout | StoreManagerMain / CashierMain / WarehouseManagerMain | LOGOUT -> AuthService | Role shortcuts; busy-operation logout guard |
| Account administration | PeopleUI(EMPLOYEES), System | EMPLOYEES/EMPLOYEE_SAVE -> PeopleService | M only; create/edit role/enabled/password; no self-demotion |
| Product / stock lookup | ProductsUI, Catalog | PRODUCT_SEARCH/GET/FIND/SERIALS -> CatalogService | M/C/W read; M PRODUCT_SAVE; stale revision rejected |
| Customers | PeopleUI(CUSTOMERS), Catalog | CUSTOMERS/CUSTOMER_SAVE -> PeopleService | M/C; selected row -> prefilled editor |
| Vendors | PeopleUI(VENDORS), Catalog | VENDORS/VENDOR_SAVE -> PeopleService | M/W read, M save; enabled toggle |
| Categories / brands | PeopleUI(CATEGORIES/BRANDS), Catalog | LOOKUPS/LOOKUP_SAVE -> CatalogService | M management; named lookup selections elsewhere |
| Receive stock | StockReceiveUI, Update | RECEIVE/STOCK_RESOLVE/STOCK_CANCEL -> InventoryService | M/W; SKU, quantity, vendor, cost, reference, serials |
| Promotions | PeopleUI(PROMOTIONS), Update | PROMOTIONS/PROMOTION_SAVE -> PromotionService | M/C read; M save; dates/rate validation |
| Sell / invoice | MakeSaleUI, Processing | PRODUCT_FIND, CUSTOMER, QUOTE, CHECKOUT, SALE_RESOLVE/CANCEL -> Catalog/People/Sales | M/C; barcode/serial input, cart, customer picker, quote confirmation, durable pending |
| Transfer stock | TransferFromWarehouse, Processing | TRANSFER/STOCK_RESOLVE/STOCK_CANCEL -> InventoryService | M/W; distinct source/destination, positive quantity, serial validation |
| Invoice search / reprint | ReceiptsUI, Lookup | INVOICES/INVOICE -> SalesService/InvoiceDao | M/C; date and keyword search, persisted snapshot receipt |
| Stock / serial history | HistoryUI, Lookup | STOCK_HISTORY/SERIAL_HISTORY -> InventoryService | M/W; keyword or exact normalized serial |
| Warranty / claims | WarrantyUI, Lookup | WARRANTIES/CLAIMS/CLAIM_RECEIVE/TRANSITION/HISTORY -> WarrantyService | M/C lookup/intake; M state transitions; stale state rejection |
| Sales report | ReportsUI, Reports | REPORT -> ReportService | M; range plus summary/employee/best type |
| Low stock | HistoryUI(LOW_STOCK), Reports | LOW_STOCK -> ReportService | M/W; server stock threshold report |

## Common components and behavior

WorkFrame: heading/subtitle, JMenuBar, BorderLayout body, status line, minimum
760x520 and maximized-both state. Closing is guarded while work is in flight.
DashboardFrame: four-column functional shortcut cards fill spare workspace; a
compact workflow guide remains at the bottom. Views are reused per title by
AppContext rather than multiplying duplicate stock/sale windows.

RowsPanel: readonly JTable, sortable rows, literal local filter, visible/total
row count, horizontal scrolling, CSV export and table print. Selection is converted
from view row to model row before editing. CSV protects formula-like cells.
Money, role/status and dates have presentation mappings. Server searches cap their
results; the local filter narrows already-loaded rows and is not server pagination.

Products and people editors: label/input pairs, combo choices for known values,
checkboxes for flags, validation before API save, success refresh. Keep structured
choices instead of requiring memorized IDs where a picker is provided. Some IDs
remain visible in table/detail views for audit and troubleshooting.

POS: top barcode/quantity/serial inputs; center cart and scrollable payment/customer
panel; bottom action row stays visible. Enter/add merges a valid product into Cart.
Checkout requests a quote, asks confirmation, persists request, sends in worker.
Pending status freezes conflicting changes; retry/resolve/cancel retain identity.
Receipt printing uses the persisted Sale, not a new calculation from current prices.

Stock: guided source/destination/vendor controls, item lookup, serial input,
reference and quantity. Pending file is loaded at open and cannot be overwritten.
Confirmed cancellation or committed result unlocks a new request.

Ui/AppContext: all network calls use SwingWorker; callbacks run on EDT. Owner is
blocked during its request and stale-session callbacks are ignored. Status/error
messages are visible; no SQL stack trace is displayed. Token expiry requires login,
not re-entering database credentials in the desktop.

## Acceptance boundary

Actual screens and navigation were run at 1366x768 and 1920x1080 with synthetic
HTTP data. Each run has 23 assertions and 20 screenshots, including all 16 role
menu destinations plus login/three homes. Tests check visible button bounds,
window maximize state, table space, role menus, one real navigation action, POS
barcode addition and EDT responsiveness. They do not prove every label at every
font/scaling, print hardware, or successful DB transactions. See Windows checklist.

The original NetBeans forms and most original GUI files were unavailable. These
new handwritten views are functional code, not mockups, but Designer round-trip
and whole-original-UI parity remain explicitly unaccepted.
