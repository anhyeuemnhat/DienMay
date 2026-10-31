# Quy ước mã nguồn được kiểm tra

Package `vn.dienmay`, UTF-8 / LF, hai dấu cách thụt lề, giới hạn 120 cột;
import tường minh; tên lớp UpperCamelCase, biến/phương thức lowerCamelCase,
hằng UPPER_SNAKE_CASE. DTO sao chép collection khi cần bảo vệ tính bất biến.

`tools/.../SourceStyle.java` là bộ kiểm tra giới hạn của dự án, chạy bằng `ant style`;
không phải Google Java Format hay Checkstyle đầy đủ.
Nó kiểm tra tên, đường dẫn/package, import, thụt lề, độ dài dòng và ranh giới client SQL.
`ant format` sửa mã: xem diff và chạy lại `ant clean verify` sau khi dùng.
Bộ format có giới hạn, không tự áp dụng vào mã generated/text block/third-party.

Phạm vi hiện tại: 94 tệp Java. Kết quả thật ở `docs/evidence/style-check.log`,
`style-self-test.log` và `ant-clean-verify.log`. javac `-Xlint:all` giữ lại 4 cảnh báo this-escape
trong constructor Swing. Không xem thành công style check là bằng chứng nghiệp vụ/SQL/production đã đúng.
