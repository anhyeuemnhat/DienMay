# Kiến trúc hiện tại

```text
JFrame AppShell -> CardLayout -> WorkFrame/EditDialog (JPanel)
  -> AppContext + SwingWorker -> HttpApiClient
  -> /api/v1/ (POST JSON, bearer token)
  -> ApiServer / ApplicationApi / AuthService
  -> service + validation + QualityService / LifecycleService / RecordGuard
  -> DAO / JDBC transaction / SQL Server dm schema v4
```

`common-dto`: record DTO, enum quyền, JSON nghiêm ngặt, tiền BigDecimal, validation.
`client-swing`: chỉ gọi HTTP; không import JDBC / service backend. `backend-server`: cấu hình DB,
phiên, xác thực, các giao dịch nghiệp vụ và công cụ DBA.
Ant biên dịch client và server độc lập với common, sau đó mới biên dịch tests.

## Điều hướng và trạng thái

`AppShell` giữ instance panel và lịch sử Back/Forward. Home không phá hủy màn hình hiện tại.
`EditDialog` giữ snapshot dirty, không lưu nội dung mật khẩu trong snapshot.
Bản nháp có thể quay lại qua Forward hoặc danh sách Nháp chưa lưu.
`RowsPanel` giữ bộ lọc, sắp xếp, chọn dòng, kích thước cột và cuộn khi refresh.
`AppContext.generation` ngăn callback cũ sửa phiên mới; `jobs` chặn rời màn hình khi đang ghi.
Native window được đặt theo vùng hiển thị khả dụng, tránh lỗi maximize trước khi X11 peer ổn định.

## Quyền và dữ liệu

`Operation` allowlist định tuyến và quyền; service kiểm tra lại tài khoản đang hoạt động,
role và AuthVersion. Ẩn nút trong Swing không được xem là phân quyền.
Mật khẩu lưu PBKDF2; bearer token chỉ ở bộ nhớ phiên.
SQL runtime role không có DDL, không sửa/xóa AuditLog và chứng từ đã ghi sổ.
Không coi runtime SQL credential là public API.

`Revision` + UPDLOCK/HOLDLOCK bảo vệ cập nhật danh mục / con người / khuyến mãi.
Lifecycle tách khỏi form sửa: có lý do, chặn tự khóa và khóa quản lý cuối cùng.
Duyệt dữ liệu tách khỏi ghi sản phẩm: PENDING mặc định, có nguồn/ghi chú/người/thời điểm.
Khi chọn reference, so khớp brand, category, name, model, description và source URL.
Không chọn reference vẫn cần quản lý tự đối chiếu; chưa có crawler xác minh tự động.

## Giao dịch

Nhập/chuyển/bán chạy transaction SQL, kiểm tra quầy/kho, số lượng, serial,
duyệt sản phẩm và mode trước ghi. RequestId + hash ràng buộc payload,
application lock theo request / nghiệp vụ và ràng buộc SQL giảm nguy cơ ghi trùng.
Client giữ pending request trên đĩa với khóa file. Sau lỗi mạng, resolve/cancel trả kết quả
cho cùng request; không phát sinh request ID mới để che lấp trạng thái chưa rõ.
Cancel đã commit là thất bại an toàn, không đảo sổ.

Những cơ chế SQL trên đã có mã và integration tests, **chưa được chạy thực tế trong đợt này**.
Xem `KNOWN_LIMITATIONS.md` và checklist SQL.
