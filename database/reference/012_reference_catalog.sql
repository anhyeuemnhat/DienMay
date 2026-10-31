-- Reference FACTS only, not a store stock/catalog import. No price, device ID or GTIN is inferred.
-- Idempotent insert-only: never overwrites a reference edited by a DBA.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=4)
    THROW 51200,N'Schema version 4 required.',1;
IF NOT EXISTS(SELECT 1 FROM dm.ReferenceProducts WHERE BrandName=N'Sony' AND Model=N'WH-CH520')
    INSERT dm.ReferenceProducts(BrandName,CategoryName,ProductName,Model,Specifications,SourceUrl,CheckedOn)
    VALUES(N'Sony',N'Tai nghe',N'Tai nghe không dây Sony WH-CH520',N'WH-CH520',N'Driver 30 mm; Bluetooth 5.2; khối lượng khoảng 147 g.',N'https://www.sony.com.vn/electronics/support/wireless-headphones-bluetooth-headphones/wh-ch520/specifications',N'2026-10-31');
IF NOT EXISTS(SELECT 1 FROM dm.ReferenceProducts WHERE BrandName=N'LG' AND Model=N'24MR400-B')
    INSERT dm.ReferenceProducts(BrandName,CategoryName,ProductName,Model,Specifications,SourceUrl,CheckedOn)
    VALUES(N'LG',N'Màn hình máy tính',N'Màn hình LG 24MR400-B',N'24MR400-B',N'23,8 inch; IPS; 1920 x 1080; tần số quét tối đa 100 Hz (D-sub tối đa 75 Hz).',N'https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/',N'2026-10-31');
COMMIT;
GO
