-- DBA ONLY. Select DienMay (or the specific target DB) in SSMS before execution.
-- First create a dedicated SQL Server login named dienmay_app using SSMS, with a strong private password.
-- Do not grant sysadmin or db_owner to this login. Do not run the Java GUI as the migration/DBA login.
SET XACT_ABORT ON;
IF SUSER_ID(N'dienmay_app') IS NULL
    THROW 51300,N'Tạo SQL login dienmay_app trong SSMS trước; không ghi mật khẩu vào script này.',1;
IF OBJECT_ID(N'dm.SchemaVersion',N'U') IS NULL
    THROW 51301,N'Chưa khởi tạo schema dm trong database đang chọn.',1;
IF DATABASE_PRINCIPAL_ID(N'dienmay_app') IS NULL CREATE USER dienmay_app FOR LOGIN dienmay_app;
IF DATABASE_PRINCIPAL_ID(N'dm_runtime') IS NULL CREATE ROLE dm_runtime AUTHORIZATION dbo;
IF NOT EXISTS(SELECT 1 FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID(N'dm_runtime') AND member_principal_id=DATABASE_PRINCIPAL_ID(N'dienmay_app'))
    ALTER ROLE dm_runtime ADD MEMBER dienmay_app;
GRANT CONNECT TO dm_runtime;
GRANT SELECT ON SCHEMA::dm TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Employees TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Customers TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Categories TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Brands TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Vendors TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Products TO dm_runtime;
GRANT INSERT, DELETE ON dm.ProductCodes TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Stock TO dm_runtime;
GRANT INSERT, UPDATE ON dm.ProductUnits TO dm_runtime;
GRANT INSERT, UPDATE ON dm.Promotions TO dm_runtime;
GRANT INSERT ON dm.Invoices TO dm_runtime;
GRANT INSERT ON dm.InvoiceDetails TO dm_runtime;
GRANT INSERT ON dm.InvoiceUnits TO dm_runtime;
GRANT INSERT ON dm.StockMovements TO dm_runtime;
GRANT INSERT ON dm.StockMovementUnits TO dm_runtime;
GRANT INSERT ON dm.InventoryRequests TO dm_runtime;
GRANT INSERT ON dm.Warranties TO dm_runtime;
GRANT INSERT, UPDATE ON dm.WarrantyClaims TO dm_runtime;
GRANT INSERT ON dm.ClaimHistory TO dm_runtime;
-- Application employees share this runtime DB identity. Role checks are enforced by the Java services.
-- This is not protection against someone stealing the DB credential or modifying the executable.
-- All staging/import DDL and backup/restore tasks must remain under a separate DBA identity.
GO
