# Hướng dẫn kiểm thử tái lập

Kết quả đã chạy ở `TEST-RESULTS.md`; không xem một kịch bản có code là đã PASS. Offline suite dùng Java/JDBC test double cho một phần boundary; nó không chứng minh SQL Server khóa đúng hoặc transaction thật rollback.

## 1. Offline và UI

```sh
ant clean dist
# Hoặc: sh scripts/build.sh
xvfb-run -a -s '-screen 0 1440x1000x24' ant ui-smoke
# Máy có desktop thật: ant ui-smoke
```

`UnitTests` hiện có 63 test case: tiền, giỏ, chuẩn hóa Serial, fingerprint, chính sách bảo hành, ngày Việt Nam, PBKDF2, role predicate/session giả bị từ chối, hash demo, pending file/reopen/khóa file/file hỏng, receipt và boundary commit/rollback/close với test double. Không cần SQL Driver để compile/chạy nhóm này.

`UiSmoke` dùng phiên nhân viên giả **chỉ để render**, không được đăng ký ở AuthService và không thể cấp quyền JDBC. Robot chụp sáu cửa sổ sau khi EDT rảnh; kiểm tra menu không đè content, role picker bị ẩn, password không điền sẵn, POS chỉ một Enter listener và bảng readonly. Không gọi DB, không nhập thanh toán giả, không in thật. Xem ảnh ở `build/ui-smoke` hoặc ảnh đã bàn giao `docs/evidence/ui`.

## 2. SQL Server integration A–E

**Chỉ chạy trên DB riêng, có thể bỏ đi, tên kết thúc `_Test`.** Runner tạo dữ liệu và giữ lịch sử, không tự dọn hoặc xóa database. Tài khoản SQL kiểm thử cần quyền tạo fixture/điều chỉnh fixture, khác tài khoản runtime tối thiểu; không dùng tài khoản production.

Trong SSMS tạo `DienMayAcceptance_Test`, chọn DB đó rồi chạy **001_schema.sql**, không chạy 000 vì 000 chuyển sang DienMay, không chạy demo. Trỏ file cấu hình riêng vào DB test, cấu hình TLS/SQL login đúng, tải driver vào lib. Kiểm tra `Code.DatabaseTool check` trước.

Windows PowerShell:

```powershell
$env:DIENMAY_CONFIG = 'config/database-test.properties'
# Tạo file trên từ file mẫu, trỏ databaseName=DienMayAcceptance_Test.
# Cấp DIENMAY_DB_PASSWORD bằng phương thức riêng tư như README.
$env:DIENMAY_IT_ALLOW_WRITES = 'YES'
ant integration
Remove-Item Env:\DIENMAY_IT_ALLOW_WRITES
```

Linux:

```sh
DIENMAY_CONFIG=config/database-test.properties DIENMAY_IT_ALLOW_WRITES=YES ant integration
```

Có thể chạy sau build không cần Ant:

```powershell
java -ea -cp "build\classes;build\test-classes;lib\*" tests.SqlServerIT
```

Suite có đăng nhập thật và quyền ba vai trò; nhập 10; chuyển cộng dồn; bán 2; lặp request; hai giao dịch đồng thời tranh tồn; hai thread cùng UUID; giả lập lỗi AFTER_INVOICE/AFTER_STOCK/BEFORE_COMMIT; hết tồn; đổi giá/khuyến mãi; Serial trùng; liên kết bảo hành 12 tháng; không bán lại Serial; snapshot chính sách; tra cứu; phiếu hết hạn vẫn được tiếp nhận; trạng thái/lịch sử; khóa tài khoản/đổi mật khẩu; tổng doanh thu và ledger; mở dependency graph mới để đọc lại dữ liệu đã commit.

Một fixture kiểm thử cố tình đổi ngày bảo hành trong DB test để mô phỏng hết hạn; tuyệt đối không mang thao tác đó sang dữ liệu thật. Test tải lại bằng một graph mới không thay thế bài **đóng/mở tiến trình Java thật** ở mục 3. Mỗi lần chạy tạo prefix IT riêng; ghi lại prefix và log. Nếu fixture đầu bị lỗi, các bài phụ thuộc có thể FAIL theo; sửa lỗi đầu, dùng DB test sạch rồi chạy lại.

Nếu thiếu biến opt-in, runner in `NOT RUN` rồi thoát, Ant có thể vẫn ghi BUILD SUCCESSFUL. **Đây không phải PASS integration.** Khi đã opt-in, lỗi kết nối/schema hoặc assertion phải được coi là thất bại, không đổi sang database giả để lách test.

## 3. Nghiệm thu thao tác và dữ liệu thật

Dùng database demo riêng, ghi lại tồn/số hóa đơn ban đầu và ngày test. Không cộng kết quả test vào dữ liệu sản xuất.

| Nhóm | Thao tác | Kết quả cần xác minh |
|---|---|---|
| A | Login từng tài khoản, đổi mật khẩu; nhập sai; thu ngân gọi quản trị; nhân viên kho sửa quyền; quản lý khóa tài khoản đang mở ở máy khác | Sai mật khẩu/quyền bị từ chối ở Service; role từ DB; phiên cũ bị thu hồi. |
| B | Nhập thêm 10 cáp với chứng từ, chuyển 6 rồi chuyển thêm 2, xem KHO/QUAY | Tồn cộng chính xác, hai vị trí thay đổi đối ứng; mỗi thay đổi có ledger. |
| C | Tại ô Mã hàng, gõ `0000000000017` rồi Enter hai lần | Một dòng cáp, SL=2, tạm tính 200.000 VND; các số 0 đầu không mất. |
| C | Giảm tay 10.000, tiền mặt, xác nhận | Hóa đơn 190.000; tồn QUAY giảm 2; một header + một detail; request duy nhất. |
| C | Gõ mã không có, số lượng 0/âm/vượt tồn, giỏ trống | Không ghi đơn/kho; thông báo rõ; không tự thêm hàng. |
| C | Hai máy tranh mua số tồn cuối; bấm thanh toán nhiều lần; thử cùng request sau timeout | Không âm kho, không có hai invoice cho một RequestId; lỗi giữ request để đối soát. |
| C | In từ receipt, đóng Java, mở lại, tra mã HD và in lại | Đọc được cùng hóa đơn, tổng tiền/đơn giá đúng; số invoice không tăng vì in lại. |
| D | Bán `DT-DEMO-01` ở QUAY với `PHONE-DEMO-0001` | Đúng Serial liên kết HD; bảo hành 12 tháng; không bán lại Serial đã SOLD. |
| D | Đổi mặc định sản phẩm thành 24 tháng rồi xem hóa đơn trước; tra BH/HD/Serial/điện thoại | Hồ sơ trước vẫn 12 tháng; đúng khách/thiết bị; không mất sau restart. |
| D | Thu ngân tiếp nhận; quản lý xử lý/hoàn tất hoặc từ chối | Không xóa history; thu ngân không được cập nhật trạng thái; hết hạn vẫn có thể tiếp nhận. |
| E | Báo cáo ngày/tháng/khoảng ngày, nhân viên, tồn thấp | Header COUNT/SUM đúng SQL, không nhân theo số dòng/Serial; đối soát 005 không có sai lệch. |

### Máy quét và máy in

Cấu hình máy quét USB HID gửi hậu tố **Enter**, đặt focus đúng ô Mã hàng. Kiểm tra quét liên tiếp, mã có 0 đầu, mã không tồn tại, và thiết bị theo Serial. Mô phỏng bằng bàn phím kiểm tra cùng event path nhưng không chứng minh phần cứng đã tương thích. Không cần camera hoặc thư viện giải mã ảnh.

In bằng máy in đã cấu hình trên hệ điều hành. Kiểm tra tiếng Việt, tổng tiền, mã HD, Serial, xuống dòng, trang tiếp theo, hủy hộp thoại in và in lại. Java Print gửi job không đồng nghĩa giấy đã ra máy. Chưa có mẫu riêng cho khổ nhiệt 58/80 mm; cần kiểm tra/căn chỉnh với driver và giấy sử dụng thực tế.

### Mất kết nối / tắt tiến trình

Chỉ thử lỗi mạng trên DB demo: gửi thanh toán rồi làm gián đoạn mạng/kết nối, không tạo request mới. Mở lại POS với cùng nhân viên, cùng máy và cùng config; dùng Đối soát. SQL Server trả hóa đơn cũ nếu đã commit, hoặc xác nhận không có sau khi request lock giải phóng. Không tự xóa pending để bấm lại. Ghi lại RequestId, invoice, tổng kho/ledger trước và sau. Chưa xác minh kịch bản này với mạng/SQL Server thật tại bàn giao.

## 4. Migration và vận hành

003/004 phải thử trên bản restore có đúng các bảng legacy; đối chiếu số nguồn, số đã duyệt, mapping và audit. Các receipt thiếu dữ liệu không nhập vào doanh thu dm. Chạy 005 sau seed, migration và test nghiệp vụ. Kiểm tra restore từ backup và quyền `dienmay_app` trước cutover; không cấp db_owner chỉ để làm biến mất lỗi permission.
