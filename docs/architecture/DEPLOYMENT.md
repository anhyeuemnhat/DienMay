# Triển khai và ranh giới tin cậy

Client chỉ giữ URL API (`DIENMAY_API_URL`), backend giữ SQL credential.
Giữ nguyên `dist/common-dto.jar` cạnh các JAR và `backend-server/lib/` trong cấu trúc dự án.
Scripts chuyển working directory về gốc dự án; `java -jar` client cũng đã kiểm tra từ thư mục khác.

Backend bind loopback mặc định. Khi đổi bind để các máy khác kết nối,
cần TLS thật và firewall. Backend hỗ trợ `DIENMAY_TLS_KEYSTORE`, `DIENMAY_TLS_PASSWORD`;
keystore và mật khẩu không được đóng gói trong bản bàn giao. Xem `ServerMain.java`.
Không tắt xác thực chứng chỉ client hoặc SQL khi triển khai thật.

`GET /health` là liveness, không chứng nhận DB / seed / dữ liệu sạch.
Dùng DatabaseTool check, bộ reconciliation và giao dịch nghiệm thu để xác nhận readiness.
Không tự động migrate hoặc seed khi backend khởi động.

Cấp user SQL vào `dienmay_runtime_role`, không dùng sa/db_owner; DBA chịu trách nhiệm backup,
restore, mã hóa lưu trữ, ACL, thời gian lưu log, nhật ký truy cập và xoay bí mật.
Runtime permission script chưa kiểm tra trên SQL Server; phải thử các transaction với chính user runtime.
Pending state trong hồ sơ người dùng có thể chứa mã thiết bị / nội dung giao dịch;
bảo vệ thư mục cá nhân, không đưa vào kho mã hay gửi công khai.
