# Kết quả bàn giao và kiểm thử — Điện Máy

Ngày kiểm chứng: **19/10/2026**. Nguồn: ZIP Sales-And-Inventory-Management-System-main do người dùng cung cấp. Thực hiện trên working branch `upgrade/dienmay`, baseline `df3c2cb`.

## Kết luận

**63 kiểm thử offline PASS, 0 FAIL trong bộ offline.** Ant và script shell đều build được 46 Java source chính, bốn file test và tạo `dist/DienMay.jar`. Có bằng chứng mở JAR vào màn hình đăng nhập; sáu cửa sổ Swing được render/chụp bằng Robot trên Xvfb.

**Chưa đạt nghiệm thu toàn bộ Definition of Done trên SQL Server.** Kiểm tra kết nối thực tế dừng do thiếu Microsoft JDBC Driver; môi trường không có SQL Server được cấu hình để kiểm thử. Tải driver thử lại vẫn thất bại DNS Maven Central. Không có xác nhận transaction/khóa/SQL script chạy đúng trên engine thật, không có xác nhận máy quét/máy in vật lý hay NetBeans/Windows. Các trạng thái dưới đây không được diễn giải thành PASS toàn hệ thống.

## Bảng thực thi

| Hạng mục | Trạng thái | Kết quả/bằng chứng |
|---|---|---|
| `ant clean dist` | PASS | 46 source chính, 4 test source, 63/63 offline, JAR tạo thành công. `evidence/final-build-test.log`. |
| `sh scripts/build.sh` | PASS | Build không cần Maven/Ant; cùng 63 test case chạy lại, không cộng thành 126 test khác nhau. `evidence/standalone-build-test.log`. |
| 63 test logic/offline | PASS | Không có SQL Server hoặc phần cứng tham gia. Chi tiết từng dòng PASS có trong hai log build. |
| `ant ui-smoke` dưới Xvfb | PASS | Sáu ảnh từ màn hình ảo; kiểm tra wiring/hiển thị, không xác thực login hay giao dịch thật. `evidence/final-ui-smoke.log`, `evidence/ui/*.png`. |
| Chạy `java -jar dist/DienMay.jar` | PASS — khởi động UI | Tiến trình còn hoạt động, X11 có cửa sổ “Đăng nhập — Điện Máy”, class GUI-App, 1147×640; dừng chủ động sau kiểm tra. Java stderr trống. `evidence/jar-launch-window.log`, `jar-launch-stderr.log`. |
| XML của 6 `.form`, Ant/POM | PASS — cú pháp XML | Parse được, không đồng nghĩa Designer/Maven đã chạy. `evidence/xml-validation.log`. |
| Tải JDBC JAR bằng HTTPS | FAIL — môi trường | curl exit 6: không phân giải được `repo.maven.apache.org`. Không đưa file tải hỏng vào lib. `evidence/driver-download.log`. |
| `Code.DatabaseTool check` qua Ant | FAIL — thiếu dependency | ClassNotFoundException cho Microsoft JDBC Driver; không có kết nối thành công. `evidence/connection-check.log`. |
| SQL Server integration A–E | NOT RUN | Runner thoát ở guard yêu cầu opt-in DB `_Test`; không chạy các assertion SQL. Dòng BUILD SUCCESSFUL của Ant ở log này không phải PASS SQL. `evidence/final-integration-status.log`. |
| 000–006 trên engine SQL Server | NOT RUN | Chưa tạo schema, seed, migrate, cấp quyền hoặc chạy reconciliation thực tế. |
| Đồng thời/rollback/durability SQL thật | NOT RUN | Có code test, chưa thực thi. Test double chỉ chứng minh control flow ở Java. |
| Đóng/mở tiến trình sau giao dịch thật | NOT RUN | Mở lại file pending offline đã thử; khởi động JAR đã thử; chưa có giao dịch SQL thật để kiểm tra persistence qua restart. |
| NetBeans Designer, Maven, Windows `.bat`/PowerShell | NOT RUN | Môi trường Linux không có Maven/NetBeans/Windows. Có cấu hình và script, chưa xác nhận nền tảng này. |
| Quét USB HID, in giấy, khổ K58/K80 | NOT RUN | Không có thiết bị trong môi trường. Không coi Enter listener hoặc receipt text test là phần cứng đã hoạt động. |

Môi trường đã chạy: Linux, OpenJDK 21.0.12.1, Ant 1.10.15, Xvfb; build target `--release 17`. Runtime JDK 17 chưa chạy. Xem `evidence/build-environment.txt`.

## Phần offline thực sự kiểm tra

Tiền thập phân chính xác và giới hạn; không chấp nhận giá âm/sai độ chính xác; giảm giá/làm tròn; chuỗi barcode giữ 0 đầu; gộp giỏ; từ chối số lượng sai/vượt tồn của snapshot giỏ; Serial chuẩn hóa/trùng/số lượng; UUID/fingerprint bất biến; không cộng hai loại giảm; thời hạn bảo hành cuối tháng/năm nhuận; expiry inclusive; chuyển trạng thái; ngày Việt Nam; hash có salt, sai mật khẩu, hash không hợp lệ; role predicate và session giả không thể cấp quyền; ba hash demo khớp mật khẩu công khai; file pending round-trip, reopen, khóa OS, chống ghi đè/hỏng payload; nội dung receipt tiếng Việt; control flow commit/rollback/close/suppressed exception bằng JDBC test double; escape LIKE và không còn đoạn UI ghi sale vào RAM theo kiểu cũ.

**Không** dùng các test trên để khẳng định SQL CHECK/FK, sp_getapplock, các hint khóa, kế hoạch query, cú pháp migration, thông lượng, máy quét hoặc máy in đã được kiểm chứng.

## Nội dung bàn giao

Mã nguồn Java, sáu cặp form chính, JAR, Images, Ant/POM/script build/run/tải driver, cấu hình mẫu không có SQL password, schema/demo/migration/reconciliation/quyền runtime, bộ test offline + SQL integration + UI smoke, README tiếng Việt, kiến trúc, audit thay đổi, hướng dẫn migration và test. Diff đối chiếu baseline được cung cấp riêng; không push hoặc sửa repository GitHub của tác giả.

## Hạn chế chức năng/triển khai đã biết

- Driver không được bundle; phải tải đúng dependency, cấu hình SQL Server và chạy test. Ứng dụng không tự tạo database hoặc tự bỏ qua xác thực chứng chỉ để kết nối.
- Receipt legacy thiếu ngày/đơn giá giao dịch không được đưa vào doanh thu mới. Bảng dbo vẫn giữ nguyên; ứng dụng mới không có màn hình tra receipt legacy. Không sinh dữ liệu giả để lấp phần thiếu.
- Danh sách tìm kiếm có giới hạn 500/1.000 bản ghi và cần bộ lọc; chưa có phân trang tổng quát.
- Hàng không Serial bảo hành theo chi tiết hóa đơn và CoveredQuantity, không tạo danh tính từng chiếc giả.
- In text bằng Java Print, chưa có mẫu nhiệt riêng hoặc chứng minh giấy đã in. Không có e-invoice thuế, payment gateway, đổi trả hoặc RMA; đây là ngoài phạm vi triển khai.
- Login SQL runtime chung cần được bảo vệ ở máy trạm/mạng. Kiểm tra role Java không chống được người đã chiếm SQL credential, sửa client hoặc có quyền DBA.

## Điều kiện để nghiệm thu tiếp

Bổ sung driver, dùng DB `_Test`, chạy `docs/TESTING.md` và kiểm tra đúng log có kết quả assertion SQL. Thử migration trên bản restore được duyệt; chạy 005 và đối chiếu chứng từ/ledger/Serial/bảo hành. Thử hai máy bán cùng tồn cuối, lỗi mạng trong thanh toán, đóng/mở Java sau commit, barcode có 0 đầu, Serial và in lại trên máy in thật. Sau đó mới quyết định triển khai trên dữ liệu sản xuất.
