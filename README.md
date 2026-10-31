# Điện Máy Đỏ - bản nâng cấp 3.0.0

**Trạng thái: bản bàn giao mã nguồn để tiếp tục nghiệm thu, CHƯA đạt nghiệm thu toàn bộ.**

Dự án thật gồm Java Swing, backend REST Java, DTO dùng chung, bộ SQL Server,
kiểm thử, tài nguyên và hướng dẫn trong cùng thư mục này.
Không có mật khẩu mặc định, tài khoản thật hoặc database sản xuất kèm theo.

## Bắt đầu

1. Đọc **[README_RUN.md](README_RUN.md)** để build, cấu hình và chạy.
2. Khởi tạo SQL Server theo **[database/README.md](database/README.md)**.
   Có schema, migration, danh mục tham chiếu và dataset DEMO riêng; không chỉ có schema rỗng.
3. Xem **[ACCEPTANCE_REPORT.md](ACCEPTANCE_REPORT.md)** trước khi dùng dữ liệu thật.

```sh
ant clean verify
# Sau khi đã cấu hình và khởi tạo SQL Server:
sh scripts/database-tool.sh check
sh scripts/run-backend.sh
# Terminal khác:
sh scripts/run-client.sh
```

Windows dùng các tệp `.bat` cùng tên. JDK 17+ và Apache Ant cần được cài trên máy.
`dist/` có các JAR đã build; vẫn giữ đầy đủ mã nguồn để build lại.

## Các thay đổi chính

- Một cửa sổ chính `AppShell`; các màn hình và biểu mẫu là panel.
  Quay lại, Tiến, Trang chủ, Nháp chưa lưu; cảnh báo trước khi rời màn hình có thay đổi.
- Ngừng sử dụng / khôi phục danh mục và tài khoản có lý do, phiên bản bản ghi,
  kiểm tra quyền và nhật ký. Không xóa lịch sử chứng từ.
- Quy trình quản lý xác minh thương hiệu / loại / sản phẩm / mã vạch,
  lưu nguồn, ghi chú, người duyệt, thời điểm. API không tự tin trạng thái duyệt gửi từ form sản phẩm.
- GTIN/EAN/UPC và IMEI có kiểm tra cấu trúc; đúng cấu trúc **không** đồng nghĩa đúng sản phẩm/thiết bị.
- Bảng dữ liệu phân biệt mã chuỗi, ngày, tiền, trạng thái; gợi ý nhập liệu không bị lưu như giá trị thật.
- SQL Server schema v4: 24 bảng / 182 cột, migration bổ sung, công cụ khởi tạo có chốt chặn DEMO,
  hai sản phẩm tham chiếu có nguồn hãng, giao dịch mô phỏng liên kết xuyên suốt.

## Đã kiểm chứng / chưa kiểm chứng

191 kiểm thử không cần SQL Server đã đạt. Kiểm thử Swing dùng HTTP mô phỏng,
không dùng database thật; kết quả và ảnh ở `docs/evidence/`.
**Môi trường thực hiện không có SQL Server**: tạo DB, chạy migration/seed,
giao dịch SQL, khóa đồng thời và kết nối backend với DB chưa được chạy thực tế.
Bộ SQL integration test đã biên dịch nhưng kết quả hiện tại là `SKIP`, không phải `PASS`.

**Chưa có nghiệp vụ hủy / hoàn / điều chỉnh chứng từ đã ghi sổ.**
Nút hủy yêu cầu chỉ chặn giao dịch chưa hoàn tất; không được coi là hoàn tiền hay đảo doanh thu.
Xem [các vấn đề còn mở](docs/KNOWN_LIMITATIONS.md). Không chạy migration trực tiếp lên dữ liệu thật khi chưa sao lưu và nghiệm thu.

## Tài liệu trong dự án

| Nội dung | Vị trí |
|---|---|
| Cài đặt / build / chạy | `README_RUN.md`, `database/README.md` |
| Hiện trạng, thay đổi, nghiệm thu | `docs/AUDIT.md`, `CHANGELOG.md`, `ACCEPTANCE_REPORT.md` |
| Ma trận tất cả cột, nguồn sự thật, quy tắc | `docs/data/FIELD_MATRIX.md`, `docs/data/schema-inventory.json` |
| Gợi ý nhập liệu và điều hướng | `docs/data/GUIDED_INPUT.md`, `docs/gui-design/SCREENS.md` |
| Kiến trúc, 44 API, triển khai | `docs/architecture/` |
| Bằng chứng kiểm thử | `docs/testing/`, `docs/evidence/` |
| Nguồn danh mục / nguồn mã | `docs/REFERENCES.md`, `docs/provenance/` |
| Toàn vẹn tệp / thư viện | `SHA256SUMS.txt`, `THIRD_PARTY_NOTICES.md` |
