# Các vấn đề còn mở

## Chặn nghiệm thu toàn bộ

1. **Chưa chạy SQL Server.** DDL, migration, seed, khóa SQL, quyền runtime, backend↔DB,
   các giao dịch và reconciliation chưa có bằng chứng runtime.
   JDBC doubles chỉ kiểm tra nhánh Java; không xác nhận cú pháp hay ngữ nghĩa T-SQL.
2. **Chưa triển khai hủy/hoàn/điều chỉnh chứng từ đã ghi sổ.**
   SALE_CANCEL / STOCK_CANCEL là chốt cho yêu cầu chưa commit, không phải nghịch đảo bán/nhập/chuyển.
   Chưa có luồng trả hàng, hoàn tiền, đổi thiết bị, điều chỉnh kho/doanh thu.
   Cần chính sách nghiệp vụ, chứng từ bù trừ, audit và kiểm thử SQL riêng.
3. **Chưa có dữ liệu vận hành thật được cửa hàng cung cấp.**
   Không thể coi hai sản phẩm tham chiếu và giao dịch DEMO là bộ dữ liệu kinh doanh thật.
   Chưa rà soát từng cell trong DB thật; có script phân loại, không có kết quả giả.

## Giới hạn vận hành cần nghiệm thu

- Danh sách dùng TOP 500/1000/2000 ở một số endpoint; chưa có phân trang toàn diện.
  Xuất bảng chỉ gồm dữ liệu đã tải, không đại diện toàn bộ DB khi vượt giới hạn.
- Revision bảo vệ sửa bản ghi cũ, nhưng CRUD danh mục và tiếp nhận bảo hành
  chưa có request-id durable như bán/nhập/chuyển. Không tự retry khi mất kết nối sau khi gửi lệnh ghi.
- Phiên đăng nhập lưu trong bộ nhớ backend; chưa thiết kế chia sẻ phiên nhiều backend.
  Khởi động lại backend yêu cầu đăng nhập lại; request giao dịch được đối soát bằng DB.
- Duyệt VERIFIED là quy trình con người có quyền và căn cứ; URL HTTPS đúng cú pháp
  không chứng minh nội dung là thật. Chưa có tự động truy cập hãng / Verified by GS1,
  lưu file chứng từ hoặc quy trình hai người duyệt độc lập.
- Ràng buộc SQL không tự chứng minh sự thật ngoài đời. Tài khoản SQL là ranh giới tin cậy,
  không được cấp cho client hay nhân viên bán hàng.
- Bản nháp form thông thường không tự lưu qua lần thoát; hồ sơ pending request lưu cục bộ
  cần quyền hệ điều hành và chính sách sao lưu phù hợp.
- Chưa nghiệm thu Windows/DPI cao, máy in, máy quét, IME tiếng Việt, trợ năng,
  mạng thật, TLS thật, tải lớn, backup/restore, xâm nhập, bảo vệ dữ liệu cá nhân.
- Bộ tham chiếu hiện có 2 model. Thương hiệu khác không bị kết luận giả;
  cần nhập và duyệt thủ công. Chưa có màn hình nhập khẩu danh mục tham chiếu hàng loạt.
- Thiết kế màn hình là Java viết tay; không cam kết round-trip NetBeans GUI Designer `.form`.
  Giữ 4 cảnh báo `this-escape` của javac; không che bằng suppress toàn bộ.

Không tự động giảm các yêu cầu trên thành đã hoàn thành. Bản này không phải chứng nhận sẵn sàng vận hành production.
