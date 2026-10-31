# API v1 của bản 3.0.0

Nguồn: `Operation.java`, `ApplicationApi.java`, `ApiServer.java`. Các thao tác đều POST JSON,
`Content-Type: application/json`; trừ login, cần `Authorization: Bearer <token>`.
Tên role: MANAGER = quản lý, CASHIER = thu ngân, WAREHOUSE = kho.
Service kiểm tra lại actor/role/Enabled/AuthVersion. Tài khoản MustChange chỉ đổi mật khẩu/đăng xuất.

## Envelope và lỗi

```json
{"data": {}}
```

```json
{"error":{"code":"VALIDATION","message":"...","correlationId":"..."}}
```

Request tối đa 1 MiB; client giới hạn response 8 MiB; JSON UTF-8 nghiêm ngặt.
400: input/revision; 401: phiên; 403: quyền; 404: route; 405: method; 409: ràng buộc SQL;
413/415: size/media; 429: login rate; 503: DB chưa xác nhận kết quả; 500: lỗi nội bộ.
Sau 503/lỗi mạng khi ghi, **không mặc định giao dịch thất bại**; dùng resolve cùng request.
`GET /health` chỉ liveness, trả `databaseChecked:false`.

## Allowlist 44 thao tác

| Operation | Path | Role cho phép |
|---|---|---|
| `LOGIN` | `/api/v1/auth/login` | Chưa đăng nhập |
| `LOGOUT` | `/api/v1/auth/logout` | MANAGER, CASHIER, WAREHOUSE |
| `PASSWORD` | `/api/v1/auth/password` | MANAGER, CASHIER, WAREHOUSE |
| `SETTINGS` | `/api/v1/settings` | MANAGER, CASHIER, WAREHOUSE |
| `PRODUCT_SEARCH` | `/api/v1/products/search` | MANAGER, CASHIER, WAREHOUSE |
| `PRODUCT_FIND` | `/api/v1/products/find` | MANAGER, CASHIER, WAREHOUSE |
| `PRODUCT_GET` | `/api/v1/products/get` | MANAGER, CASHIER, WAREHOUSE |
| `PRODUCT_SAVE` | `/api/v1/products/save` | MANAGER |
| `LIFECYCLE` | `/api/v1/admin/lifecycle` | MANAGER |
| `QUALITY_LIST` | `/api/v1/quality/list` | MANAGER |
| `QUALITY_REVIEW` | `/api/v1/quality/review` | MANAGER |
| `REFERENCES` | `/api/v1/quality/references` | MANAGER |
| `AUDIT_LOG` | `/api/v1/admin/audit` | MANAGER |
| `SERIALS` | `/api/v1/products/serials` | MANAGER, CASHIER, WAREHOUSE |
| `LOOKUPS` | `/api/v1/lookups/list` | MANAGER, CASHIER, WAREHOUSE |
| `LOOKUP_SAVE` | `/api/v1/lookups/save` | MANAGER |
| `CUSTOMERS` | `/api/v1/customers/search` | MANAGER, CASHIER |
| `CUSTOMER` | `/api/v1/customers/get` | MANAGER, CASHIER |
| `CUSTOMER_SAVE` | `/api/v1/customers/save` | MANAGER, CASHIER |
| `VENDORS` | `/api/v1/vendors/list` | MANAGER, WAREHOUSE |
| `VENDOR_SAVE` | `/api/v1/vendors/save` | MANAGER |
| `EMPLOYEES` | `/api/v1/employees/list` | MANAGER |
| `EMPLOYEE_SAVE` | `/api/v1/employees/save` | MANAGER |
| `PROMOTIONS` | `/api/v1/promotions/list` | MANAGER, CASHIER |
| `PROMOTION_SAVE` | `/api/v1/promotions/save` | MANAGER |
| `QUOTE` | `/api/v1/sales/quote` | MANAGER, CASHIER |
| `CHECKOUT` | `/api/v1/sales/checkout` | MANAGER, CASHIER |
| `SALE_CANCEL` | `/api/v1/sales/cancel` | MANAGER, CASHIER |
| `SALE_RESOLVE` | `/api/v1/sales/resolve` | MANAGER, CASHIER |
| `INVOICE` | `/api/v1/sales/get` | MANAGER, CASHIER |
| `INVOICES` | `/api/v1/sales/search` | MANAGER, CASHIER |
| `RECEIVE` | `/api/v1/inventory/receive` | MANAGER, WAREHOUSE |
| `TRANSFER` | `/api/v1/inventory/transfer` | MANAGER, WAREHOUSE |
| `STOCK_CANCEL` | `/api/v1/inventory/cancel` | MANAGER, WAREHOUSE |
| `STOCK_RESOLVE` | `/api/v1/inventory/resolve` | MANAGER, WAREHOUSE |
| `STOCK_HISTORY` | `/api/v1/inventory/history` | MANAGER, WAREHOUSE |
| `SERIAL_HISTORY` | `/api/v1/inventory/serial-history` | MANAGER, WAREHOUSE |
| `WARRANTIES` | `/api/v1/warranties/search` | MANAGER, CASHIER |
| `CLAIM_RECEIVE` | `/api/v1/warranties/receive` | MANAGER, CASHIER |
| `CLAIMS` | `/api/v1/warranties/claims` | MANAGER, CASHIER |
| `CLAIM_TRANSITION` | `/api/v1/warranties/transition` | MANAGER |
| `CLAIM_HISTORY` | `/api/v1/warranties/history` | MANAGER, CASHIER |
| `REPORT` | `/api/v1/reports/query` | MANAGER |
| `LOW_STOCK` | `/api/v1/reports/low-stock` | MANAGER, WAREHOUSE |

## Payload cần chú ý

- `auth/login`: `username`, `password`. `auth/password`: `oldPassword`, `newPassword`.
  Không log password/token; thành công trả ApiSession.
- `products/save`: `{ "item": Item }`. Item đầy đủ trường trong common-dto.
  Các field `verificationStatus`, `barcodeStatus`, `demo`, `verificationSource` từ form
  **không cấp quyền duyệt**; server đặt PENDING và mode thực tế khi tạo.
  Thay đổi danh tính/mã làm mất duyệt liên quan; sửa bản ghi phải có revision hiện tại.
- `lookups/save`: `type` (brand/category), `id` (null khi thêm), `name`, `revision` khi sửa.
- `customers/save`: `id`, `name`, `phone`, `address`, `revision` khi sửa.
  `vendors/save` thêm `enabled`; `employees/save`: `id`, `username`, `name`, `phone`, `role`, `enabled`, `password`, `revision`.
  `promotions/save`: `id`, `code`, `name`, `from`, `to`, `percent`, `enabled`, `revision`.
  Khi sửa, không đổi enabled để lách lifecycle. Password rỗng khi sửa là giữ nguyên.
- `admin/lifecycle`: `type` (product/customer/vendor/employee/promotion/category/brand),
  `id`, `revision`, `enabled`, `reason`. Từ chối invoice/stock/history và các type ngoài allowlist.
- `quality/list`: `type` (product/brand/category). `quality/references` và `admin/audit`: `{}`.
- `quality/review`: `type`, `id`, `revision`, `field` (identity/barcode),
  `status` (PENDING/VERIFIED/INVALID/DEMO), `source`, `note`, `referenceId` (có thể null).
  VERIFIED cần nguồn HTTPS và ghi chú căn cứ; khi chọn reference, so cả brand/category/name/model/description/source.
- `inventory/receive`, `inventory/transfer`, `inventory/cancel`, `inventory/resolve`:
  `{ "request": InventoryRequest }` gồm `id`, `productId`, `quantity`, `serials`, `reference`, `vendorId`,
  `unitCost`, `from`, `to`, `identifierType` (SERIAL/IMEI).
  Payload pending cũ thiếu identifierType được đọc là SERIAL, giữ nguyên fingerprint cũ.
- `sales/quote`: `lines` (ItemQuantity), `code`, `manual`.
  `sales/checkout`, `sales/cancel`, `sales/resolve`: `{ "request": SaleRequest }` với `requestId`,
  `customerId`, `payment`, `manualDiscount`, `promotionCode`, `expectedDiscount`, `lines`.
  Mỗi ItemQuantity: `productId`, `sku`, `name`, `expectedPrice`, `quantity`, `serials`.
- `warranties/transition`: `claim`, `expected`, `next`, `notes`; actor phải quản lý.
  `warranties/receive`: `warranty`, `fault`. Chuyển trạng thái bảo hành không tự đảo bán hàng.
- API ngày dùng ISO `yyyy-MM-dd`; UI chuyển đổi dd/MM/yyyy. Tiền là JSON number / BigDecimal,
  mã/serial/SKU là string. Không đổi mã có số 0 đầu thành số.

Không có API bootstrap, SQL tùy ý, DROP, hard delete chứng từ, hoàn tiền hay điều chỉnh đã ghi sổ.
Bản 3.0.0 yêu cầu client/server/schema đồng bộ; ngoại lệ đọc pending cũ không phải cam kết tương thích mọi client cũ.
