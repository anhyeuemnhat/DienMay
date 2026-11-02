-- Run after 014 and the base 008 runtime role script. No login, password or broad DELETE grant.
SET XACT_ABORT ON;
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=5)
    THROW 51580,N'Schema v5 is required.',1;
IF DATABASE_PRINCIPAL_ID(N'dienmay_runtime_role') IS NULL
    THROW 51581,N'Apply security/008_runtime_role.sql first.',1;
GRANT EXECUTE ON OBJECT::dm.ControlledDelete TO dienmay_runtime_role;
-- Static DML inside dbo-owned procedure uses ownership chaining, not EXECUTE AS OWNER.
-- Do not change procedure/table ownership independently; do not grant ALTER on the procedure to runtime.
DENY DELETE ON OBJECT::dm.Products TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Stock TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Employees TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Customers TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Vendors TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Categories TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Brands TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.Promotions TO dienmay_runtime_role;
GRANT INSERT ON OBJECT::dm.InventoryReversals TO dienmay_runtime_role;
DENY UPDATE, DELETE ON OBJECT::dm.InventoryReversals TO dienmay_runtime_role;
DENY INSERT, UPDATE, DELETE ON OBJECT::dm.DeletionRequests TO dienmay_runtime_role;
DENY UPDATE, DELETE ON OBJECT::dm.StockMovements TO dienmay_runtime_role;
DENY UPDATE, DELETE ON OBJECT::dm.StockMovementUnits TO dienmay_runtime_role;
DENY UPDATE, DELETE ON OBJECT::dm.InventoryRequests TO dienmay_runtime_role;
DENY UPDATE, DELETE ON OBJECT::dm.RequestCancellations TO dienmay_runtime_role;
DENY DELETE ON OBJECT::dm.ProductUnits TO dienmay_runtime_role;
-- ProductCodes retains the existing narrow DELETE privilege needed by catalog code-index replacement.
-- No DELETE/UPDATE permission is added to invoices, warranty history or AuditLog.
GO
