-- READ ONLY. Select the intended database in your SQL tool before running.
SELECT DB_NAME() AS DatabaseName, Version, AppliedAt FROM dm.SchemaVersion ORDER BY Version;

-- Expected: zero rows. Stock quantity must equal the signed ledger total.
SELECT s.ProductId,s.Location,s.Quantity,
       COALESCE(SUM(CONVERT(bigint,m.Delta)),0) AS LedgerQuantity
FROM dm.Stock s LEFT JOIN dm.StockMovements m
  ON m.ProductId=s.ProductId AND m.Location=s.Location
GROUP BY s.ProductId,s.Location,s.Quantity
HAVING s.Quantity<>COALESCE(SUM(CONVERT(bigint,m.Delta)),0);

-- Expected: zero rows for serialized products.
SELECT s.ProductId,s.Location,s.Quantity,COUNT_BIG(u.Id) AS AvailableUnits
FROM dm.Stock s JOIN dm.Products p ON p.Id=s.ProductId AND p.TrackSerial=1
LEFT JOIN dm.ProductUnits u ON u.ProductId=s.ProductId AND u.Location=s.Location AND u.Status='IN_STOCK'
GROUP BY s.ProductId,s.Location,s.Quantity
HAVING CONVERT(bigint,s.Quantity)<>COUNT_BIG(u.Id);

-- Expected: zero rows. Group invoice details BEFORE comparing header values.
SELECT i.Id,i.Code,i.Subtotal,COALESCE(d.DetailGross,0) AS DetailGross
FROM dm.Invoices i LEFT JOIN
 (SELECT InvoiceId,SUM(CONVERT(decimal(38,2),Quantity)*UnitPrice) AS DetailGross
  FROM dm.InvoiceDetails GROUP BY InvoiceId) d ON d.InvoiceId=i.Id
WHERE i.Subtotal<>COALESCE(d.DetailGross,0);

-- Expected: zero rows. Cancellation and a corresponding committed request must not coexist.
SELECT c.Kind,c.RequestId,c.EmployeeId FROM dm.RequestCancellations c
WHERE (c.Kind='SALE' AND EXISTS(SELECT 1 FROM dm.Invoices i WHERE i.RequestId=c.RequestId))
   OR (c.Kind='STOCK' AND EXISTS(SELECT 1 FROM dm.InventoryRequests r WHERE r.RequestId=c.RequestId));

-- Expected: zero orphaned or stale search index codes (never repairs automatically).
SELECT p.Id,p.SKU FROM dm.Products p WHERE NOT EXISTS
    (SELECT 1 FROM dm.ProductCodes c WHERE c.ProductId=p.Id AND c.Code=p.SKU)
 OR (p.Barcode IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM dm.ProductCodes c WHERE c.ProductId=p.Id AND c.Code=p.Barcode));
SELECT c.Code,c.ProductId FROM dm.ProductCodes c JOIN dm.Products p ON p.Id=c.ProductId
WHERE c.Code<>p.SKU AND (p.Barcode IS NULL OR c.Code<>p.Barcode);
-- Expected: one sold unit is associated with exactly its own product invoice line.
SELECT u.Id,u.Serial,u.Status FROM dm.ProductUnits u
WHERE (u.Status='SOLD' AND NOT EXISTS(SELECT 1 FROM dm.InvoiceUnits iu WHERE iu.UnitId=u.Id))
 OR (u.Status='IN_STOCK' AND EXISTS(SELECT 1 FROM dm.InvoiceUnits iu WHERE iu.UnitId=u.Id));
SELECT iu.UnitId,iu.DetailId FROM dm.InvoiceUnits iu JOIN dm.InvoiceDetails d ON d.Id=iu.DetailId
JOIN dm.ProductUnits u ON u.Id=iu.UnitId WHERE u.ProductId<>d.ProductId;
-- Expected: no header/detail warranty mismatch; unit coverage stays on its invoice.
SELECT w.Id FROM dm.Warranties w JOIN dm.InvoiceDetails d ON d.Id=w.DetailId
WHERE w.WarrantyMonths<>d.WarrantyMonths OR w.EndDate<>DATEADD(month,w.WarrantyMonths,w.StartDate)
 OR (w.UnitId IS NOT NULL AND NOT EXISTS
    (SELECT 1 FROM dm.InvoiceUnits u WHERE u.UnitId=w.UnitId AND u.DetailId=w.DetailId));
SELECT s.ProductId,s.Location FROM dm.Stock s WHERE s.Quantity<0;
GO
