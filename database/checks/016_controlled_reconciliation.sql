-- READ ONLY: run after v5 in the explicitly selected database. Every violation query expects zero rows.
IF NOT EXISTS(SELECT 1 FROM dm.SchemaVersion WHERE Version=5) THROW 51600,N'Schema v5 required.',1;
-- An original and its compensating movement remain linked, with the exact opposite delta.
SELECT r.Id,r.OriginalMovementId,r.ReversalMovementId FROM dm.InventoryReversals r
JOIN dm.StockMovements original ON original.Id=r.OriginalMovementId
JOIN dm.StockMovements reversal ON reversal.Id=r.ReversalMovementId
WHERE original.Kind<>'RECEIVE' OR reversal.Kind<>'RECEIVE_REVERSAL'
 OR original.ProductId<>reversal.ProductId OR original.Location<>reversal.Location
 OR CONVERT(bigint,original.Delta)+CONVERT(bigint,reversal.Delta)<>0
 OR reversal.RequestId<>r.RequestId OR reversal.EmployeeId<>r.EmployeeId
 OR original.PreviousCostPrice IS NULL OR original.CostRevisionAfter IS NULL
 OR original.PreviousCostPrice<>r.RestoredCostPrice OR original.UnitCost<>r.PreviousCostPrice;
-- Every original unit is retained and linked to the compensating movement, never sold again.
SELECT r.Id,u.Id AS UnitId,u.Status FROM dm.InventoryReversals r
JOIN dm.StockMovementUnits mu ON mu.MovementId=r.OriginalMovementId
JOIN dm.ProductUnits u ON u.Id=mu.UnitId
WHERE u.Status<>'REVERSED' OR u.Location<>'KHO'
 OR NOT EXISTS(SELECT 1 FROM dm.StockMovementUnits reverseUnit
     WHERE reverseUnit.MovementId=r.ReversalMovementId AND reverseUnit.UnitId=mu.UnitId)
 OR EXISTS(SELECT 1 FROM dm.InvoiceUnits sold WHERE sold.UnitId=u.Id)
 OR EXISTS(SELECT 1 FROM dm.Warranties warranty WHERE warranty.UnitId=u.Id);
SELECT u.Id FROM dm.ProductUnits u WHERE u.Status='REVERSED' AND NOT EXISTS(
 SELECT 1 FROM dm.StockMovementUnits mu JOIN dm.InventoryReversals r ON r.ReversalMovementId=mu.MovementId
 WHERE mu.UnitId=u.Id);
-- No orphan compensating movement or cancelled-and-committed administrative request.
SELECT m.Id FROM dm.StockMovements m WHERE m.Kind='RECEIVE_REVERSAL'
 AND NOT EXISTS(SELECT 1 FROM dm.InventoryReversals r WHERE r.ReversalMovementId=m.Id);
SELECT c.Kind,c.RequestId FROM dm.RequestCancellations c
WHERE (c.Kind='DELETE' AND EXISTS(SELECT 1 FROM dm.DeletionRequests d WHERE d.RequestId=c.RequestId))
 OR (c.Kind='REVERSAL' AND EXISTS(SELECT 1 FROM dm.InventoryReversals r WHERE r.RequestId=c.RequestId));
-- Both audit and durable receipt exist; no old audit row is removed by deletion.
SELECT d.RequestId FROM dm.DeletionRequests d WHERE NOT EXISTS(
 SELECT 1 FROM dm.AuditLog a WHERE a.EntityType=d.EntityType AND a.EntityId=d.EntityId
  AND a.Action='HARD_DELETE' AND a.EmployeeId=d.EmployeeId AND a.Reason=d.Reason);
SELECT r.Id FROM dm.InventoryReversals r WHERE NOT EXISTS(
 SELECT 1 FROM dm.AuditLog a WHERE a.EntityType='receipt' AND a.EntityId=r.OriginalMovementId
  AND a.Action='RECEIPT_REVERSED' AND a.EmployeeId=r.EmployeeId AND a.Reason=r.Reason);
-- Also run 009_reconciliation.sql: signed movement totals include all compensations automatically.
GO
