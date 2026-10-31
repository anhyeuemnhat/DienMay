# Thay đổi bản 3.0.0 - 31/10/2026

Base chỉnh sửa: RAR đính kèm, không phải bản clone main đã xác minh từng byte.
Danh sách file/hash thay đổi trong `docs/provenance/changes.json`.

## Giao diện

Thêm AppShell; chuyển WorkFrame/EditDialog sang JPanel; Back/Forward/Home/drafts,
cache màn hình, cảnh báo dirty/busy, giữ state, mode banner.
Nối lifecycle/quality editor qua API; không giả vờ nút đã ghi SQL khi chưa có response.
RowsPanel chuẩn hóa header/kiểu sắp xếp/enum/ngày, empty schema và mode xuất/in.
GuidedInput và combo typeahead; field typed barcode/IMEI/evidence.
Sửa cold start X11 bằng usable display bounds, validate/repaint; không bắt maximize trước map.

## Backend/API/common

CatalogRules, QualityService, LifecycleService, RecordGuard; thêm 5 Operation quản trị.
Kiểm tra active/verified/mode trước giao dịch, reset duyệt khi đổi danh tính/mã,
reference tuple 6 field, không chấp nhận forged approval qua save.
Revision/row lock cho master writes; lifecycle tách khỏi enabled form;
phone, self/last-manager protection, audit; AuthVersion khi thay đổi tài khoản/mật khẩu.
InventoryRequest thêm identifierType; đọc pending SERIAL cũ giữ fingerprint.
Không thay hủy pending request thành nghiệp vụ đảo hóa đơn.

## Database và vận hành

Migration 010/011, ReferenceProducts/DeploymentInfo/AuditLog, GTIN/IMEI CHECK,
installer mục tiêu chính xác và chốt DEMO/Test, không auto seed/DDL khi backend start.
Danh mục Sony/LG có nguồn, demo linked flow, barcode trống, serial DEMO tường minh,
readonly reconciliation/audit. Driver Ant/Maven khớp 13.6.0.jre11; kèm giấy phép MIT.

## Kiểm thử/tài liệu

Thêm QualityTests, cập nhật SecurityTests/SqlServerIT/UiSmoke/PackagedAppSmoke,
chuẩn hóa fixture minh bạch, evidence hiện tại, ma trận 182 field và 44 API,
hướng dẫn cài đặt và báo cáo chưa nghiệm thu SQL.
Không đính kèm config thật, log cũ, các file build/IDE thừa hoặc token GitHub.
