-- READ-ONLY integrity checks. Empty result sets are the expected result.
-- Compare ledger sum to current stock (includes OPENING/RECEIVE/TRANSFER/SALE).
SELECT s.ProductId,s.Location,s.Quantity,COALESCE(SUM(CONVERT(bigint,m.Delta)),0) AS LedgerQuantity
FROM dm.Stock s LEFT JOIN dm.StockMovements m ON m.ProductId=s.ProductId AND m.Location=s.Location
GROUP BY s.ProductId,s.Location,s.Quantity HAVING s.Quantity<>COALESCE(SUM(CONVERT(bigint,m.Delta)),0);
-- Serialized stock must equal available physical units in each location.
SELECT s.ProductId,s.Location,s.Quantity,COUNT(u.Id) AS AvailableUnits
FROM dm.Stock s JOIN dm.Products p ON p.Id=s.ProductId AND p.TrackSerial=1
LEFT JOIN dm.ProductUnits u ON u.ProductId=s.ProductId AND u.Location=s.Location AND u.Status='IN_STOCK'
GROUP BY s.ProductId,s.Location,s.Quantity HAVING s.Quantity<>COUNT(u.Id);
-- Every paid invoice's subtotal must equal its line snapshots, without serial joins.
SELECT i.Id,i.Subtotal,SUM(d.UnitPrice*d.Quantity) AS DetailSubtotal
FROM dm.Invoices i LEFT JOIN dm.InvoiceDetails d ON d.InvoiceId=i.Id GROUP BY i.Id,i.Subtotal
HAVING COUNT(d.Id)=0 OR i.Subtotal<>SUM(d.UnitPrice*d.Quantity);
-- ProductUnits may be linked only to a detail of the same product and sold status.
SELECT iu.DetailId,iu.UnitId FROM dm.InvoiceUnits iu JOIN dm.InvoiceDetails d ON d.Id=iu.DetailId JOIN dm.ProductUnits u ON u.Id=iu.UnitId
WHERE d.ProductId<>u.ProductId OR u.Status<>'SOLD';
-- Sold serial quantities must match detail quantity.
SELECT d.Id,d.Quantity,COUNT(iu.UnitId) AS LinkedUnits FROM dm.InvoiceDetails d JOIN dm.Products p ON p.Id=d.ProductId AND p.TrackSerial=1
LEFT JOIN dm.InvoiceUnits iu ON iu.DetailId=d.Id GROUP BY d.Id,d.Quantity HAVING d.Quantity<>COUNT(iu.UnitId);
-- Warranty snapshots should cover every sold quantity with nonzero warranty.
SELECT d.Id,d.Quantity,COALESCE(SUM(w.CoveredQuantity),0) AS Covered FROM dm.InvoiceDetails d LEFT JOIN dm.Warranties w ON w.DetailId=d.Id
WHERE d.WarrantyMonths>0 GROUP BY d.Id,d.Quantity HAVING d.Quantity<>COALESCE(SUM(w.CoveredQuantity),0);
GO
