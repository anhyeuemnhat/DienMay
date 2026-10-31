# Màn hình và điều hướng

```text
AppShell (JFrame duy nhat)
  Dang nhap -> Doi mat khau bat buoc (neu can) -> Trang chu theo vai tro
  Trang chu -> Danh sach nghiep vu -> Chi tiet / Sua (JPanel)
  Quay lai <-> Tien ; Trang chu ; Nhap chua luu
```

Quản lý có 17 đích điều hướng nghiệp vụ; thu ngân 6; kho 7.
Danh sách thực tế được sinh bởi `Navigation.destinations`; quyền không chỉ ở menu.
Nhân viên / sản phẩm / khách / nhà cung cấp / loại / thương hiệu / nhập / khuyến mãi /
bán / chuyển / hóa đơn / kho / Serial / bảo hành / doanh thu / cảnh báo tồn và xác minh dữ liệu
đều dùng panel trong shell.

Form sửa không mở JFrame/JDialog nghiệp vụ mới. Hộp thoại ngắn vẫn dùng cho xác nhận,
chọn nháp, chọn khách nhanh, chọn file xuất và thông báo.
Bản nháp được giữ khi Back; hủy form chấm dứt bản nháp sau xác nhận.
Nhãn môi trường DEMO/PRODUCTION/UNKNOWN ở shell, bản in và CSV giúp phân biệt dữ liệu.

Ảnh: `../evidence/ui/1366x768/` và `../evidence/ui/1920x1080/`.
Bộ UiSmoke kiểm tra một JFrame, các đích theo role, Back/Forward, giữ form/bộ lọc/dòng chọn,
cảnh báo thay đổi và kích thước viewport. Không phải kiểm thử mọi thao tác thủ công/mọi độ phân giải.
