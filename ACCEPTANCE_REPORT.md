# BIÊN BẢN BÀN GIAO / TRẠNG THÁI NGHIỆM THU

Ngày: 29/10/2026. Baseline đã kiểm tra qua GitHub Connector:
`83bec84a21714275296552a2f201962cc19089ba` trên `main`.

**Kết luận: đã bàn giao project có code, build, test và giao diện thật;
KHÔNG đánh dấu ĐẠT ĐẦY ĐỦ cho cả năm tiêu chí.**

## 1. Năm tiêu chí

| Tiêu chí | Trước (theo đánh giá người dùng) | Kết quả đã kiểm chứng | Chưa đạt / chưa kiểm chứng |
|---|---|---|---|
| Java Coding Convention | Chưa đạt đầy đủ | Package lowercase; định dạng/import; checker AST + formatter chạy 77 file, 0 lỗi; build thành công | Chỉ quy tắc con của project, không full Google Style; không kiểm tra form gốc |
| OOP | Đạt cơ bản | Composition root, private dependencies, immutable contracts, DI/interface, controller riêng; test và UML khớp phần giao | Chưa audit đủ toàn bộ repository / chưa chạy nghiệp vụ SQL thật |
| 3-Tier | Phân lớp logic | Client/backend build độc lập, API HTTP thật; client không JDBC/DAO/DB credentials; SQL chỉ ở backend | Client → API → SQL Server thật chưa chạy; chưa triển khai ba host/TLS thật |
| GUI theo chức năng tin học | Đạt một phần | Sáu nhóm menu, 3 vai trò, 16 đích điều hướng; Swing/API smoke đạt | Dữ liệu GUI là fixture; chưa xác minh toàn bộ nghiệp vụ và parity GUI gốc |
| Phủ vùng làm việc | Chưa đạt | Maximize và layout thật, 23/23 test tại mỗi độ phân giải, 40 PNG thật | Windows scaling 100/125/150%, nhiều màn hình, NetBeans Designer chưa chạy |

## 2. Bằng chứng

- Build: `docs/evidence/build-final.log`, `dist/*.jar`.
- Rebuild from an extracted ZIP: `docs/evidence/package-rebuild.log` (PASS).
- Convention: `docs/evidence/style-check.log`, `style-self-test.log`; `docs/coding-convention/CONVENTION.md`.
- 66/66 unit + 47/47 API + 17/17 security: các log `unit-tests.log`, `api-tests.log`, `security-tests.log`.
- GUI: `docs/evidence/ui-1366x768.log`, `ui-1920x1080.log` và `docs/evidence/ui/`.
- SQL: `docs/evidence/sql-server-tests.log` ghi NOT RUN/SKIP, không phải PASS.
- Kiến trúc/API: `docs/architecture/`; UML: `docs/uml/`; GUI: `docs/gui-design/`.
- Nguồn/giáo trình: `docs/provenance/`, `docs/REFERENCES.md`.

Tổng **176 lượt kiểm thử đạt, 0 thất bại trong lần chạy cuối**.
Không cộng bộ SQL bị SKIP vào số PASS. Cùng 23 GUI test được chạy hai độ phân giải.
Log lỗi các lần đầu vẫn được giữ để truy vết; xem TEST_RESULTS.md.
Ba cảnh báo javac 21 `this-escape` trong constructor Swing chưa được loại bỏ.

## 3. Thiếu phạm vi nguồn: phải đọc trước khi nghiệm thu

**Không lấy được clone/archive đầy đủ.** GitHub Connector đã cung cấp các file
nguồn được liệt kê, nhưng không đủ toàn bộ GUI, `.form`, ảnh, DOCX, UML và
migration legacy gốc. Vì vậy:

- Không có baseline build cho toàn bộ repository gốc.
- Không thể nghiệm thu yêu cầu giữ đủ mọi resource/form/chức năng cũ từ gói này.
- UI mới là mã Swing viết tay chạy thật, không phải bản tái cấu trúc đã bảo toàn NetBeans form.
- Không thay migration legacy chưa đọc bằng script phá hủy hoặc tuyên bố di chuyển dữ liệu đã đạt.

Đây là **phần yêu cầu chưa hoàn thành**, không chỉ là một test chưa chạy.
Thư mục có đủ source cho các module bàn giao và build độc lập, nhưng không đồng nghĩa
với snapshot đầy đủ repository gốc.

## 4. Cổng nghiệm thu còn lại

Cần chạy SQL_SERVER_ACCEPTANCE.md trên DB `_Test`; sau đó chạy workflow thật
qua HTTPS từ máy client khác. Cần chạy WINDOWS_ACCEPTANCE.md, kiểm tra máy in/máy quét,
kiểm tra Maven/JDK17 và đối chiếu lại toàn bộ source/form/resource/DOCX gốc khi có đủ.
Chưa có chữ ký DBA, người dùng nghiệp vụ hay giảng viên nghiệm thu production.

Không push repository, không chạy migration trên DB người dùng, không đóng gói mật khẩu thật.
