-- Adapted from DienMay 83bec84a: same dm schema and constraints, no change to dbo legacy tables.
-- Select an EMPTY target database before running this script.
SET XACT_ABORT ON;
GO
IF SCHEMA_ID(N'dm') IS NULL EXEC(N'CREATE SCHEMA dm AUTHORIZATION dbo');
GO
IF OBJECT_ID(N'dm.SchemaVersion',N'U') IS NOT NULL
    THROW 51000,N'dm already exists. Use migrations, never rerun the initial schema.',1;
BEGIN TRANSACTION;
CREATE TABLE dm.SchemaVersion(
    Version int NOT NULL PRIMARY KEY,
    AppliedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dm.Employees(
    Id bigint IDENTITY PRIMARY KEY,
    UserName nvarchar(64) COLLATE Latin1_General_100_CI_AS NOT NULL UNIQUE,
    FullName nvarchar(120) NOT NULL,
    Phone nvarchar(30) NOT NULL DEFAULT N'',
    PasswordHash varchar(256) NOT NULL,
    Role varchar(16) NOT NULL,
    Enabled bit NOT NULL DEFAULT 1,
    MustChange bit NOT NULL DEFAULT 1,
    AuthVersion int NOT NULL DEFAULT 1,
    FailedAttempts int NOT NULL DEFAULT 0,
    LockUntil datetime2(3) NULL,
    CONSTRAINT CK_Employee_Role CHECK(Role IN ('MANAGER','CASHIER','WAREHOUSE')),
    CONSTRAINT CK_Employee_Hash CHECK(PasswordHash LIKE 'pbkdf2_sha256$%'),
    CONSTRAINT CK_Employee_Attempts CHECK(FailedAttempts>=0)
);
CREATE TABLE dm.Customers(
    Id bigint IDENTITY PRIMARY KEY,
    FullName nvarchar(120) NOT NULL,
    Phone nvarchar(30) NOT NULL DEFAULT N'',
    Address nvarchar(300) NOT NULL DEFAULT N''
);
CREATE INDEX IX_Customers_Phone ON dm.Customers(Phone);
CREATE TABLE dm.Categories(Id bigint IDENTITY PRIMARY KEY,Name nvarchar(100) NOT NULL UNIQUE);
CREATE TABLE dm.Brands(Id bigint IDENTITY PRIMARY KEY,Name nvarchar(100) NOT NULL UNIQUE);
CREATE TABLE dm.Vendors(
    Id bigint IDENTITY PRIMARY KEY,
    Name nvarchar(150) NOT NULL,
    Phone nvarchar(30) NOT NULL DEFAULT N'',
    Address nvarchar(300) NOT NULL DEFAULT N'',
    Enabled bit NOT NULL DEFAULT 1
);
CREATE TABLE dm.Products(
    Id bigint IDENTITY PRIMARY KEY,
    SKU nvarchar(64) COLLATE Latin1_General_100_CI_AS NOT NULL UNIQUE,
    Barcode nvarchar(128) COLLATE Latin1_General_100_CI_AS NULL,
    Name nvarchar(180) NOT NULL,
    CategoryId bigint NOT NULL REFERENCES dm.Categories(Id),
    BrandId bigint NOT NULL REFERENCES dm.Brands(Id),
    Model nvarchar(100) NOT NULL DEFAULT N'',
    Description nvarchar(1000) NOT NULL DEFAULT N'',
    CostPrice decimal(19,2) NOT NULL,
    SellingPrice decimal(19,2) NOT NULL,
    WarrantyMonths int NOT NULL DEFAULT 0,
    TrackSerial bit NOT NULL DEFAULT 0,
    Enabled bit NOT NULL DEFAULT 1,
    ReorderLevel int NOT NULL DEFAULT 5,
    Revision int NOT NULL DEFAULT 1,
    CONSTRAINT CK_Product_Money CHECK(CostPrice>=0 AND SellingPrice>=0),
    CONSTRAINT CK_Product_Warranty CHECK(WarrantyMonths BETWEEN 0 AND 120),
    CONSTRAINT CK_Product_Reorder CHECK(ReorderLevel>=0)
);
CREATE UNIQUE INDEX UX_Products_Barcode ON dm.Products(Barcode) WHERE Barcode IS NOT NULL;
CREATE TABLE dm.ProductCodes(
    Code nvarchar(128) COLLATE Latin1_General_100_CI_AS NOT NULL PRIMARY KEY,
    ProductId bigint NOT NULL REFERENCES dm.Products(Id)
);
CREATE TABLE dm.Stock(
    ProductId bigint NOT NULL REFERENCES dm.Products(Id),
    Location varchar(8) NOT NULL,
    Quantity int NOT NULL DEFAULT 0,
    CONSTRAINT PK_Stock PRIMARY KEY(ProductId,Location),
    CONSTRAINT CK_Stock_Location CHECK(Location IN ('KHO','QUAY')),
    CONSTRAINT CK_Stock_Quantity CHECK(Quantity>=0)
);
CREATE TABLE dm.ProductUnits(
    Id bigint IDENTITY PRIMARY KEY,
    ProductId bigint NOT NULL REFERENCES dm.Products(Id),
    Serial nvarchar(128) COLLATE Latin1_General_100_CI_AS NOT NULL UNIQUE,
    Location varchar(8) NOT NULL,
    Status varchar(16) NOT NULL DEFAULT 'IN_STOCK',
    CONSTRAINT CK_Unit_Location CHECK(Location IN ('KHO','QUAY')),
    CONSTRAINT CK_Unit_Status CHECK(Status IN ('IN_STOCK','SOLD'))
);
CREATE INDEX IX_Units_ProductLocation ON dm.ProductUnits(ProductId,Location,Status);
CREATE TABLE dm.Promotions(
    Id bigint IDENTITY PRIMARY KEY,
    Code nvarchar(50) COLLATE Latin1_General_100_CI_AS NOT NULL UNIQUE,
    Name nvarchar(150) NOT NULL,
    StartDate date NOT NULL,
    EndDate date NOT NULL,
    PercentOff decimal(5,2) NOT NULL,
    Enabled bit NOT NULL DEFAULT 1,
    CONSTRAINT CK_Promotion_Dates CHECK(EndDate>=StartDate),
    CONSTRAINT CK_Promotion_Rate CHECK(PercentOff BETWEEN 0 AND 100)
);
CREATE TABLE dm.Invoices(
    Id bigint IDENTITY PRIMARY KEY,
    Code AS (N'HD-'+CONVERT(nvarchar(20),Id)) PERSISTED,
    RequestId uniqueidentifier NOT NULL UNIQUE,
    RequestHash char(64) NOT NULL,
    SoldAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
    CustomerId bigint NULL REFERENCES dm.Customers(Id),
    CustomerName nvarchar(120) NOT NULL,
    CustomerPhone nvarchar(30) NOT NULL,
    Subtotal decimal(19,2) NOT NULL,
    Discount decimal(19,2) NOT NULL,
    Total decimal(19,2) NOT NULL,
    PaymentMethod varchar(12) NOT NULL,
    PromotionCode nvarchar(50) COLLATE Latin1_General_100_CI_AS NULL,
    CONSTRAINT CK_Invoice_Payment CHECK(PaymentMethod IN ('CASH','TRANSFER')),
    CONSTRAINT CK_Invoice_Money CHECK(Subtotal>=0 AND Discount>=0
        AND Discount<=Subtotal AND Total=Subtotal-Discount)
);
CREATE UNIQUE INDEX UX_Invoices_Code ON dm.Invoices(Code);
CREATE INDEX IX_Invoices_SoldAt ON dm.Invoices(SoldAt) INCLUDE(EmployeeId,Total);
CREATE INDEX IX_Invoices_Phone ON dm.Invoices(CustomerPhone);
CREATE TABLE dm.InvoiceDetails(
    Id bigint IDENTITY PRIMARY KEY,
    InvoiceId bigint NOT NULL REFERENCES dm.Invoices(Id),
    ProductId bigint NOT NULL REFERENCES dm.Products(Id),
    SKU nvarchar(64) COLLATE Latin1_General_100_CI_AS NOT NULL,
    ProductName nvarchar(180) NOT NULL,
    UnitPrice decimal(19,2) NOT NULL,
    Quantity int NOT NULL,
    WarrantyMonths int NOT NULL,
    CONSTRAINT UX_Detail_Product UNIQUE(InvoiceId,ProductId),
    CONSTRAINT CK_Detail_Quantity CHECK(Quantity>0),
    CONSTRAINT CK_Detail_Money CHECK(UnitPrice>=0),
    CONSTRAINT CK_Detail_Warranty CHECK(WarrantyMonths BETWEEN 0 AND 120)
);
CREATE TABLE dm.InvoiceUnits(
    DetailId bigint NOT NULL REFERENCES dm.InvoiceDetails(Id),
    UnitId bigint NOT NULL UNIQUE REFERENCES dm.ProductUnits(Id),
    CONSTRAINT PK_InvoiceUnits PRIMARY KEY(DetailId,UnitId)
);
CREATE TABLE dm.StockMovements(
    Id bigint IDENTITY PRIMARY KEY,
    RequestId uniqueidentifier NOT NULL,
    ProductId bigint NOT NULL REFERENCES dm.Products(Id),
    Location varchar(8) NOT NULL,
    Delta int NOT NULL,
    Kind varchar(16) NOT NULL,
    Reference nvarchar(200) NOT NULL,
    EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
    InvoiceId bigint NULL REFERENCES dm.Invoices(Id),
    VendorId bigint NULL REFERENCES dm.Vendors(Id),
    UnitCost decimal(19,2) NULL,
    CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_Movement_Location CHECK(Location IN ('KHO','QUAY')),
    CONSTRAINT CK_Movement_Kind CHECK(Kind IN ('RECEIVE','TRANSFER','SALE','OPENING')),
    CONSTRAINT CK_Movement_Delta CHECK(Delta<>0),
    CONSTRAINT CK_Movement_Cost CHECK(UnitCost IS NULL OR UnitCost>=0),
    CONSTRAINT UX_Movement_Request UNIQUE(RequestId,ProductId,Location)
);
CREATE INDEX IX_Movements_Product ON dm.StockMovements(ProductId,CreatedAt);
CREATE TABLE dm.StockMovementUnits(
    MovementId bigint NOT NULL REFERENCES dm.StockMovements(Id),
    UnitId bigint NOT NULL REFERENCES dm.ProductUnits(Id),
    CONSTRAINT PK_StockMovementUnits PRIMARY KEY(MovementId,UnitId)
);
CREATE TABLE dm.InventoryRequests(
    RequestId uniqueidentifier PRIMARY KEY,
    RequestHash char(64) NOT NULL,
    EmployeeId bigint NOT NULL REFERENCES dm.Employees(Id),
    CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dm.Warranties(
    Id bigint IDENTITY PRIMARY KEY,
    Code AS (N'BH-'+CONVERT(nvarchar(20),Id)) PERSISTED,
    DetailId bigint NOT NULL REFERENCES dm.InvoiceDetails(Id),
    UnitId bigint NULL REFERENCES dm.ProductUnits(Id),
    StartDate date NOT NULL,
    EndDate date NOT NULL,
    WarrantyMonths int NOT NULL,
    CoveredQuantity int NOT NULL,
    CONSTRAINT UX_Warranty_Unit UNIQUE(DetailId,UnitId),
    CONSTRAINT CK_Warranty_Quantity CHECK(CoveredQuantity>0),
    CONSTRAINT CK_Warranty_Dates CHECK(EndDate>=StartDate AND WarrantyMonths BETWEEN 1 AND 120)
);
CREATE UNIQUE INDEX UX_Warranty_Code ON dm.Warranties(Code);
CREATE TABLE dm.WarrantyClaims(
    Id bigint IDENTITY PRIMARY KEY,
    WarrantyId bigint NOT NULL REFERENCES dm.Warranties(Id),
    ReceivedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    ReceivedBy bigint NOT NULL REFERENCES dm.Employees(Id),
    Fault nvarchar(1000) NOT NULL,
    Status varchar(16) NOT NULL DEFAULT 'RECEIVED',
    Notes nvarchar(1000) NOT NULL DEFAULT N'',
    CompletedAt datetime2(3) NULL,
    CONSTRAINT CK_Claim_Status CHECK(Status IN ('RECEIVED','PROCESSING','COMPLETED','REJECTED'))
);
CREATE TABLE dm.ClaimHistory(
    Id bigint IDENTITY PRIMARY KEY,
    ClaimId bigint NOT NULL REFERENCES dm.WarrantyClaims(Id),
    ChangedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    ChangedBy bigint NOT NULL REFERENCES dm.Employees(Id),
    OldStatus varchar(16) NULL,
    NewStatus varchar(16) NOT NULL,
    Notes nvarchar(1000) NOT NULL
);
CREATE INDEX IX_Claims_Warranty ON dm.WarrantyClaims(WarrantyId,ReceivedAt);
CREATE INDEX IX_ClaimHistory_Claim ON dm.ClaimHistory(ClaimId,ChangedAt);
INSERT dm.SchemaVersion(Version) VALUES(1);
COMMIT;
GO
