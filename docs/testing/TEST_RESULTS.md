# Kết quả kiểm thử thực tế

Đợt bàn giao 31/10/2026 (Asia/Ho_Chi_Minh; log hệ thống có thể ghi 30/10 UTC).
JDK/Ant thực tế trong `../evidence/environment.log`.

| Phạm vi | Kết quả | Bằng chứng |
|---|---|---|
| UnitTests | 66 đạt, 0 lỗi | `unit-tests.log` |
| ApiTests | 47 đạt, 0 lỗi | `api-tests.log` |
| SecurityTests | 22 đạt, 0 lỗi | `security-tests.log` |
| QualityTests | 56 đạt, 0 lỗi | `quality-tests.log` |
| Tổng offline | **191 đạt** | `ant-clean-verify.log` |
| Swing 1366x768, 3 role | 105 đạt, 0 lỗi, process exit 0 | `ui-1366x768.log`, `ui/1366x768/` |
| Swing 1920x1080, 3 role | 105 đạt, 0 lỗi, process exit 0 | `ui-1920x1080.log`, `ui/1920x1080/` |
| Cold JAR startup từ working directory khác | Đạt ở cả hai độ phân giải; exit 0 | `packaged-*.log`, `packaged/` |
| Quy ước mã nguồn | 94 tệp; 0 vi phạm; self-test đạt | `style-check.log`, `style-self-test.log` |
| SQL Server integration | **SKIP / CHƯA CHẠY** | `sql-server-integration.log` |
| Maven / Windows / máy in / máy quét / TLS thật | **Chưa chạy** | Checklist, không có log đạt |

Tất cả tên log trên nằm trong `docs/evidence/`.

## Đúng phạm vi bằng chứng

Unit/API/security/quality gồm các hàm thật, local HTTP, file pending thật,
UI component và JDBC doubles theo từng bộ; **không bộ nào trong 191 test kết nối SQL Server**.
QualityTests kiểm tra check digit/IMEI, phân loại/nguồn, lifecycle/revision,
từ chối duyệt giả trong CRUD, so tuple tham chiếu, semantics cell và guided input.

UiSmoke mở Swing thật, gọi local HTTP với `UiFixture` mô phỏng.
Nó kiểm tra một JFrame, 17/6/7 đích theo role, Back/Forward, dirty prompt,
form/filter/selection state và viewport. **Không thay thế E2E DB hoặc UAT nhân viên thật.**

Cold startup gọi đúng `java -jar` và entrypoint ứng dụng từ thư mục tạm,
kiểm tra process sống, ảnh nền được tải và không lỗi startup.
Không kiểm tra đăng nhập production, giấy phép tài nguyên hay mọi cell trong ảnh.

## Lỗi đã phát hiện trong đợt kiểm tra

Cold startup ban đầu trả cửa sổ trắng trên X11 khi maximize trước/sát thời điểm native peer
được map. Chuyển sang đặt usable display bounds, validate/repaint sau show/resize;
kiểm thử JAR lại cả hai kích thước đạt. UiSmoke được sửa để installTheme trước khi tạo view,
không dùng mixed Look-and-Feel khác ứng dụng. Cleanup test dispose cả window owner ẩn để process thoát.
Một lệnh gộp chạy cả hai UI vượt timeout của công cụ; đã chạy lại từng độ phân giải và ghi exit 0.
Không dùng dòng RESULT đơn lẻ để che process timeout.

Còn 4 javac this-escape warnings trong constructor Swing. Không ẩn warnings và không tuyên bố code không còn lỗi.
