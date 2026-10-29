-- DBA REVIEW REQUIRED. Run in the intended dm schema-v2 database, never in master.
-- Does not create a login/password/user. No SQL credentials are embedded.
SET XACT_ABORT ON;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=2)
    THROW 51080,N'Apply and review dm schema version 2 first.',1;
IF DATABASE_PRINCIPAL_ID(N'dienmay_runtime_role') IS NULL
    CREATE ROLE dienmay_runtime_role AUTHORIZATION dbo;
GRANT SELECT ON SCHEMA::dm TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Employees TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Customers TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Vendors TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Categories TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Brands TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Products TO dienmay_runtime_role;
GRANT INSERT, DELETE ON OBJECT::dm.ProductCodes TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Stock TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.ProductUnits TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.Promotions TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.Invoices TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.InvoiceDetails TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.InvoiceUnits TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.StockMovements TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.StockMovementUnits TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.InventoryRequests TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.RequestCancellations TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.Warranties TO dienmay_runtime_role;
GRANT INSERT, UPDATE ON OBJECT::dm.WarrantyClaims TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.ClaimHistory TO dienmay_runtime_role;
-- DBA creates a SQL login and maps it to a database user by an approved secret process.
-- Then, after checking the chosen user name:
-- ALTER ROLE dienmay_runtime_role ADD MEMBER [dienmay_runtime];
-- No ALTER/CONTROL/db_owner is granted by this script.
-- Validate sp_getapplock execution and all application operations in the test environment.
GO
