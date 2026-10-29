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
GO
