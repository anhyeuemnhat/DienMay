# Nguồn mã và phạm vi đối chiếu

Base chỉnh sửa: **DienMay-Do-UI.rar do người dùng đính kèm**.
Bản giải nén gốc được giữ riêng; thay đổi thực hiện trên bản sao.
Mã gốc được đọc trước khi sửa. Build và ba bộ test nền ban đầu chạy được;
bằng chứng đó không thay thế các log kiểm thử bản cuối trong `docs/evidence/`.

GitHub connector đã kiểm tra read-only repository `anhyeuemnhat/DienMay`, nhánh `main`:
- Commit quan sát: `233b11bf84705781b7f074847a3d8210a191a059`.
- Commit timestamp: `2026-10-29T21:56:21Z`.
- Đã đọc metadata repository/branch và root listing.
- **Không clone đầy đủ, không so khớp từng byte RAR với main, không push/ghi GitHub.**

SHA256 và kích thước hai tệp đầu vào nằm trong `inputs.json`.
`changes.json` so sánh mã/thành phần triển khai của RAR với bản bàn giao, không phải Git diff với main.
Root `SHA256SUMS.txt` ghi hash các tệp cuối cùng, không phải chữ ký số hay chứng nhận bảo mật.

## Thành phần giữ lại / loại bỏ

Giữ kiến trúc Java Swing / common DTO / HTTP backend / SQL Server, các phân hệ,
tài nguyên giao diện và JDBC JAR có trong RAR. Nâng cấp điều hướng, lifecycle,
quality, migration, seed, tests và tài liệu theo changelog.

Không gói cấu hình DB cụ thể của người dùng, IDE caches, build trung gian,
Git metadata, hash/báo cáo cũ, các ảnh/diagram chỉ mô tả kiến trúc cũ.
Bằng chứng UI mới được sinh từ bản chạy hiện tại với dữ liệu mô phỏng.
Tài liệu gốc nói về giáo trình/commit cũ không được dùng làm bằng chứng của đợt này.

Không truy cập hay thay đổi database vận hành. SQL Server không có trong môi trường;
DDL/seed/migration/integration vẫn chờ kiểm thử. Giấy phép tài nguyên gốc chưa được
kiểm toán độc lập; xem `../../THIRD_PARTY_NOTICES.md`.
