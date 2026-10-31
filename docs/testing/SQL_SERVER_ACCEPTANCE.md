# Nghiệm thu SQL Server - CHƯA CHẠY

`docs/evidence/sql-server-integration.log` ghi `SKIP`: môi trường bàn giao không có SQL Server.
`BUILD SUCCESSFUL` khi target SQL bỏ qua **không phải nghiệm thu SQL đạt**.

## Chuẩn bị an toàn

DBA tạo database trống tên `DienMayDo_Test`, cấp tài khoản cài đặt, cấu hình
DIENMAY_DB_URL/USER/PASSWORD tới đúng DB này. Không dùng database khách hàng/production.

```sh
ant clean verify
sh scripts/database-tool.sh init-test DienMayDo_Test --confirm-test
# Sau khi cấp runtime role và xác nhận chỉ để kiểm thử:
export DIENMAY_IT_ALLOW_WRITES=YES
export DIENMAY_IT_DATABASE=DienMayDo_Test
ant sql-it
```

PowerShell dùng `$env:DIENMAY_IT_ALLOW_WRITES='YES'`, `$env:DIENMAY_IT_DATABASE='DienMayDo_Test'`.
Test tạo tài khoản và fixture riêng với mật khẩu ngẫu nhiên trong bộ nhớ, không cần seed demo và không cần bootstrap trước.
Giữ lại hồ sơ test để đối soát, không DELETE/DROP để dọn lịch sử.
Sau khi chạy, bỏ các biến opt-in khỏi terminal. Không sao chép fixture sang DB thật.

## Checklist phải có bằng chứng

| Nhóm | Cách kiểm tra / điều kiện đạt | Hiện tại |
|---|---|---|
| Khởi tạo trống | 001+007+010+011+012 chạy; SchemaVersion=1..4, mode đúng | Chưa chạy |
| Seed DEMO | bootstrap, seed, chạy lại không trùng; từ chối tên/mode/dữ liệu không hợp lệ | Chưa chạy |
| Migration cũ | backup/restore bản sao; giữ dữ liệu/lịch sử, chờ duyệt; lặp lại an toàn | Chưa chạy |
| JDBC / TLS / role | check + backend khởi động bằng user runtime, không db_owner | Chưa chạy |
| CRUD / lifecycle | ba role; có/không liên kết; khôi phục, audit, stale revision, self/last-manager | Có mã test, chưa chạy SQL |
| Duyệt / định danh | pending, tuple sai, barcode/IMEI sai, trùng, DEMO/PROD lẫn | Có mã test/một phần offline; chưa chạy SQL |
| Giao dịch | nhập -> chuyển -> bán -> bảo hành -> báo cáo | Chưa chạy SQL |
| Đồng thời / khôi phục | bán trùng serial, tồn cuối, request/hash trùng, rollback, mất kết nối trước/sau commit | Chưa chạy SQL |
| Đối soát | 009 không trả vi phạm; 013 được phân loại, không xóa giá trị chờ xác minh | Chưa chạy |
| Backup / restore | restore thật trên máy khác và chạy lại luồng | Chưa chạy |
| Hủy/hoàn đã ghi sổ | Cần thiết kế/chính sách và test riêng | Chưa triển khai |

Khi bổ sung bằng chứng: ghi phiên bản SQL Server/JDBC/JDK, OS, database test, user role,
commit/hash mã, lệnh, exit code, log đã che bí mật và kết quả reconciliation.
Không đổi chữ CHƯA CHẠY thành ĐẠT chỉ vì code đã biên dịch.
