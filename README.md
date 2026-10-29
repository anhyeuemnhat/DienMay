# DienMay Improved

**Bản cải tiến có mã nguồn thật, build và kiểm thử thật. Chưa nghiệm thu đầy đủ cả 5 tiêu chí.**

Nguồn tham chiếu: `anhyeuemnhat/DienMay`, nhánh `main`, HEAD đã kiểm tra qua GitHub Connector:
`83bec84a21714275296552a2f201962cc19089ba`.

## Phạm vi bàn giao

Project này có đủ mã nguồn cho các module đang được build: Swing client,
backend Java độc lập, DTO/model chung, công cụ convention và kiểm thử.
Các model, service và DAO được cải biên từ mã đã đọc tại commit trên;
không thay nghiệp vụ SQL Server bằng cơ sở dữ liệu giả.

**Giới hạn quan trọng:** không lấy được toàn bộ archive/clone của repository.
Chỉ các file ghi trong `docs/provenance/upstream-files.csv` đã được đọc trực tiếp.
Phần lớn GUI gốc, `.form`, ảnh, tài liệu DOCX và migration legacy chưa được lấy đầy đủ.
Giao diện bàn giao là Swing viết tay, không cần ảnh ngoài; **không phải bản clone đầy đủ
hay bằng chứng giữ nguyên khả năng chỉnh sửa form NetBeans**.

## Bắt đầu

```sh
ant clean verify
```

Build chính đã chạy thành công bằng JDK 21, `--release 17`; chưa chạy native JDK 17.
Ant build không cần tải dependency để compile/kiểm thử offline.
Driver Microsoft JDBC chỉ cần khi backend kết nối SQL Server và **chưa được kèm trong ZIP**.
Maven POM cũng được cung cấp cho IDE/build trực tuyến, nhưng Maven chưa chạy trong môi trường bàn giao.

Đọc **[README_RUN.md](README_RUN.md)** để tạo schema, bootstrap quản lý, cấu hình TLS và chạy hai tiến trình.
Không có tài khoản/mật khẩu mặc định. Không tự chạy migration trên dữ liệu thật.

## Kết quả kiểm chứng

| Bộ kiểm tra | Kết quả | Phạm vi |
|---|---:|---|
| Domain, recovery, JDBC boundary | 66/66 | JDBC test double, không phải SQL Server |
| JSON, HTTP, idempotency controller | 47/47 | HTTP thật, handler giả lập rõ ràng |
| Xác thực/phân quyền | 17/17 | Service/API thật, JDBC test double |
| Swing 1366×768 | 23/23 | Linux Xvfb/Openbox, dữ liệu API minh họa |
| Swing 1920×1080 | 23/23 | Linux Xvfb/Openbox, dữ liệu API minh họa |
| SourceStyle | 77 file, 0 lỗi | Quy tắc con của dự án; không tuyên bố full Google Style |
| SQL Server integration | NOT RUN | Cần SQL Server và driver |
| Windows scaling, NetBeans Designer | NOT RUN | Cần nghiệm thu bổ sung |

Tổng cộng **176 lượt test** đạt: 130 non-visual + 46 lượt GUI.
46 lượt GUI là cùng 23 test chạy hai lần, không phải 46 kịch bản khác nhau.
Bộ convention có self-test riêng, không cộng vào 176.
Còn ba cảnh báo `this-escape` của javac 21 trong constructor Swing; log giữ nguyên.

## Cấu trúc

```text
common-dto/       model/DTO, JSON, operation policy; khong JDBC
backend-server/   API, auth, services, transaction, SQL Server DAO
client-swing/     Swing, HTTP client, controllers, pending-file recovery
database/         schema v1, additive migration v2, synthetic demo, permissions
tests/            130 offline tests, UI tests, opt-in SQL Server integration
tools/            JDK-only convention checker/formatter
scripts/          build/run/test cho Windows va Linux
dist/             common-dto.jar, client-swing.jar, backend-server.jar
docs/             audit, UML, architecture, GUI, logs va 40 screenshot
```

Tham khảo **[ACCEPTANCE_REPORT.md](ACCEPTANCE_REPORT.md)** trước khi chấm đạt hoặc triển khai.
Repository GitHub gốc không bị push, tạo branch hay thay đổi dữ liệu.
