-- NON-DESTRUCTIVE REVIEW STAGING. Run on a BACKUP/COPY of the original sdaproject DB
-- after 001_schema.sql has been applied to that same DB. dbo is never modified.
-- No legacy plaintext PIN is copied. No old invoice price/date is invented.
SET XACT_ABORT ON;
IF OBJECT_ID(N'dm.SchemaVersion',N'U') IS NULL THROW 51100,N'Chạy schema dm trước.',1;
IF OBJECT_ID(N'dbo.StoreItems',N'U') IS NULL OR OBJECT_ID(N'dbo.WarehouseItems',N'U') IS NULL
    THROW 51101,N'Không tìm thấy các bảng nguồn gốc trong dbo.',1;
IF OBJECT_ID(N'dm.LegacyProductStage',N'U') IS NOT NULL
    THROW 51102,N'Đã có vùng đối soát. Không ghi đè bản đang duyệt.',1;
BEGIN TRANSACTION;
CREATE TABLE dm.LegacyProductStage(
    LegacySKU nvarchar(64) PRIMARY KEY,
    LegacyStoreName nvarchar(180) NULL, LegacyWarehouseName nvarchar(180) NULL,
    LegacyDepartment nvarchar(100) NULL, LegacyPerishable int NULL,
    LegacyStorePrice decimal(19,2) NULL, LegacyWarehousePrice decimal(19,2) NULL,
    SourceCounterQuantity bigint NULL, SourceWarehouseQuantity bigint NULL,
    SourceBadBatchCount int NOT NULL DEFAULT 0,
    TargetName nvarchar(180) NULL, Barcode nvarchar(128) NULL,
    CategoryId bigint NULL REFERENCES dm.Categories(Id), BrandId bigint NULL REFERENCES dm.Brands(Id),
    CostPrice decimal(19,2) NULL, SellingPrice decimal(19,2) NULL,
    WarrantyMonths int NULL, TrackSerial bit NULL, ReorderLevel int NOT NULL DEFAULT 5,
    Approved bit NOT NULL DEFAULT 0, ReviewNote nvarchar(1000) NOT NULL DEFAULT N'',
    NewProductId bigint NULL REFERENCES dm.Products(Id)
);
INSERT dm.LegacyProductStage(LegacySKU,LegacyStoreName,LegacyWarehouseName,LegacyDepartment,LegacyPerishable,LegacyStorePrice,LegacyWarehousePrice,SourceCounterQuantity,SourceWarehouseQuantity,SourceBadBatchCount,TargetName)
SELECT ids.SKU,s.Name,w.Name,COALESCE(s.Department,w.Department),COALESCE(s.Perishable,w.Perishable),
       TRY_CONVERT(decimal(19,2),s.Price),TRY_CONVERT(decimal(19,2),w.Price),
       CASE WHEN s.Item_SKU IS NULL THEN 0 ELSE CONVERT(bigint,s.Quantity) END,
       COALESCE(b.Quantity,0),COALESCE(b.BadCount,0),COALESCE(s.Name,w.Name)
FROM (SELECT Item_SKU AS SKU FROM dbo.StoreItems UNION SELECT Item_SKU FROM dbo.WarehouseItems) ids
LEFT JOIN dbo.StoreItems s ON s.Item_SKU=ids.SKU
LEFT JOIN dbo.WarehouseItems w ON w.Item_SKU=ids.SKU
OUTER APPLY(
    SELECT SUM(CONVERT(bigint,x.Number_of_Boxes)*CONVERT(bigint,x.Items_Per_Box)) AS Quantity,
           SUM(CASE WHEN x.Number_of_Boxes IS NULL OR x.Items_Per_Box IS NULL OR x.Number_of_Boxes<0 OR x.Items_Per_Box<0 THEN 1 ELSE 0 END) AS BadCount
    FROM dbo.Batch x WHERE x.Item_SKU=ids.SKU
) b;
CREATE TABLE dm.LegacySerialStage(
    LegacySKU nvarchar(64) NOT NULL REFERENCES dm.LegacyProductStage(LegacySKU),
    Serial nvarchar(128) NOT NULL PRIMARY KEY, Location varchar(8) NOT NULL CHECK(Location IN('KHO','QUAY'))
);
CREATE TABLE dm.LegacyCustomerStage(
    LegacyCNIC nvarchar(64) PRIMARY KEY, FullName nvarchar(120) NULL, Phone nvarchar(30) NULL, Address nvarchar(300) NULL,
    Approved bit NOT NULL DEFAULT 0, NewCustomerId bigint NULL REFERENCES dm.Customers(Id)
);
INSERT dm.LegacyCustomerStage(LegacyCNIC,FullName,Phone,Address) SELECT CNIC,Name,Phone_No,Address FROM dbo.Customers;
CREATE TABLE dm.LegacyVendorStage(
    LegacyCNIC nvarchar(64) PRIMARY KEY, LegacyContactName nvarchar(150) NULL, LegacyCompanyName nvarchar(150) NULL,
    TargetName nvarchar(150) NULL, Phone nvarchar(30) NULL, Address nvarchar(300) NOT NULL DEFAULT N'',
    Approved bit NOT NULL DEFAULT 0, NewVendorId bigint NULL REFERENCES dm.Vendors(Id)
);
INSERT dm.LegacyVendorStage(LegacyCNIC,LegacyContactName,LegacyCompanyName,TargetName,Phone)
SELECT CNIC,Name,Company_Name,COALESCE(NULLIF(Company_Name,''),Name),Phone_No FROM dbo.Vendors;
CREATE TABLE dm.LegacyAccountStage(
    LegacyCNIC nvarchar(64) NOT NULL, LegacyRole int NOT NULL,
    UserName nvarchar(64) NOT NULL, FullName nvarchar(120) NOT NULL,
    NewRole varchar(16) NULL, NewPasswordHash varchar(256) NULL,
    Enabled bit NOT NULL DEFAULT 0, Approved bit NOT NULL DEFAULT 0,
    NewEmployeeId bigint NULL REFERENCES dm.Employees(Id),
    PRIMARY KEY(LegacyCNIC,LegacyRole)
);
INSERT dm.LegacyAccountStage(LegacyCNIC,LegacyRole,UserName,FullName)
SELECT l.cnic,l.type,l.cnic,COALESCE(c.Name,l.cnic) FROM dbo.LoginInformation l LEFT JOIN dbo.Cashiers c ON c.CNIC=l.cnic;
CREATE TABLE dm.LegacyImportAudit(
    Id bigint IDENTITY PRIMARY KEY, AppliedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id), Entity nvarchar(50) NOT NULL,
    LegacyKey nvarchar(128) NOT NULL, NewId bigint NOT NULL, Note nvarchar(1000) NOT NULL
);
COMMIT;
-- These source invoices lack SoldAt and sold UnitPrice. Keep them as legacy records;
-- they are intentionally NOT mixed into the new revenue reports.
SELECT COUNT_BIG(*) AS LegacyReceiptCount FROM dbo.Receipts;
SELECT COUNT_BIG(*) AS LegacyReceiptLineCount FROM dbo.ReceiptItem;
SELECT * FROM dm.LegacyProductStage ORDER BY LegacySKU;
GO
