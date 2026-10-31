# Gợi ý nhập liệu đã tích hợp

`GuidedInput` vẽ placeholder trên border, **không chèn nội dung vào Document**.
Gợi ý biến mất khi focus/nhập; tooltip và accessible description vẫn còn.
Mật khẩu không vẽ gợi ý bằng văn bản rõ trên nội dung nhập.
`Ui.fields` áp dụng helper chung, kể cả JTextArea trong JScrollPane.

| Nhóm field | Hướng dẫn / lựa chọn | Kiểm tra có căn cứ |
|---|---|---|
| SKU | Mã nội bộ duy nhất, giữ số 0 | String, duy nhất; không tự coi là GTIN |
| Tên sản phẩm / model | Tên chính thức / model hãng | Duyệt cùng brand/category/spec, không chỉ đúng chính tả |
| Brand / category | Combo không editable; gõ tiền tố để chọn, bỏ dấu khi tìm | Form sản phẩm chọn danh mục hợp lệ; mục mới nhập qua quản lý và duyệt |
| Barcode | Quét mã trên bao bì; chưa xác minh thì để trống; chọn loại | Check digit khi GTIN; NONE/OTHER tường minh; duyệt mã tách riêng |
| Serial / IMEI | Nhập/quét thiết bị; SERIAL/IMEI ở nhập hàng | Chặn trùng; IMEI 15 số/Luhn; không tự gắn verified cho mã đúng cấu trúc |
| Nhà cung cấp | Combo dữ liệu có sẵn, chỉ hồ sơ cửa hàng | Không tạo nhà cung cấp từ suy đoán web |
| Khách hàng | Tra cứu/chọn; khách lẻ khi không chọn | Không cần tạo khách/điện thoại giả để bán |
| Điện thoại | Thông tin được cung cấp; không rõ để trống | BE kiểm tra 7-15 chữ số/định dạng, không khẳng định chủ sở hữu |
| Giá / tiền | VND không âm, không dấu phân cách nghìn trong ô nhập | BigDecimal chính xác; không trộn giá DEMO và thật |
| Ngày | dd/MM/yyyy ở UI | Parse nghiêm ngặt, ngày cuối không trước ngày đầu; API ISO |
| Nguồn / căn cứ duyệt | HTTPS chính hãng/có thẩm quyền; ghi chú cụ thể | Không kèm bí mật; reference so đủ tuple; người duyệt chịu trách nhiệm |
| Ngừng / khôi phục | Form riêng nêu hậu quả, nhập lý do, xác nhận | Quản lý, revision hiện tại, không xóa lịch sử |
| Bảng tìm kiếm | Lọc, sắp xếp, giữ dòng chọn/cột/cuộn | Enum chỉ dịch theo cột; không dịch nhầm SKU/tên giống SALE hoặc chuỗi ngày |

Autocomplete là **chọn tiền tố trong combo có kiểm soát**, không phải mô hình tự sinh tên sản phẩm.
Không có gợi ý giả vờ đã được hãng xác minh.
Bằng chứng helper: QualityTests (placeholder không làm đổi nội dung, gõ chọn danh mục,
cell đúng kiểu); form/điều hướng: UiSmoke. Chưa có UAT thủ công mọi field trên máy Windows.
