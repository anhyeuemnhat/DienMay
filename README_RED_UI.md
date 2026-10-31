# Giao diện một cửa sổ

Tài nguyên giao diện đỏ trong tệp RAR được giữ lại.
`AppShell` là JFrame duy nhất; `WorkFrame` và `EditDialog` giữ tên tương thích nhưng hiện là JPanel.
Cửa sổ phụ chỉ dùng cho xác nhận / chọn nhanh / chọn file.

Xem `docs/gui-design/SCREENS.md`, `docs/data/GUIDED_INPUT.md` và `docs/testing/TEST_RESULTS.md`.
Ảnh hiện tại ở `docs/evidence/ui/`; được tạo bằng Swing thật với HTTP mô phỏng,
không phải ảnh nghiệm thu SQL Server.
Báo cáo / log giao diện cũ trong RAR đã được loại khỏi gói này để không trộn bằng chứng.
