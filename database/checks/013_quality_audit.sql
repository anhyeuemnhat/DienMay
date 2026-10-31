-- READ ONLY. Review locally: output may contain store identifiers. Do not publish it.
SELECT DB_NAME() AS DatabaseName,Mode,DatasetVersion FROM dm.DeploymentInfo;
SELECT Id,Name,VerificationStatus,VerificationSource,VerifiedAt FROM dm.Brands
WHERE VerificationStatus<>'VERIFIED' OR Name LIKE N'%demo%' OR Name LIKE N'%test%';
SELECT p.Id,p.SKU,p.Name,b.Name AS Brand,c.Name AS Category,p.Model,p.VerificationStatus,
    p.BarcodeType,p.BarcodeStatus,p.IsDemo,
    CASE WHEN p.Name LIKE N'%demo%' OR p.Name LIKE N'%test%' OR b.Name LIKE N'%demo%' OR b.Name LIKE N'%test%'
        THEN N'REVIEW_CANDIDATE_NOT_PROOF_OF_FALSE_DATA' ELSE N'PENDING_REVIEW' END AS ReviewReason
FROM dm.Products p JOIN dm.Brands b ON b.Id=p.BrandId JOIN dm.Categories c ON c.Id=p.CategoryId
WHERE p.VerificationStatus<>'VERIFIED' OR (p.Barcode IS NOT NULL AND p.BarcodeStatus<>'VERIFIED')
    OR b.VerificationStatus<>'VERIFIED' OR c.VerificationStatus<>'VERIFIED'
    OR p.Name LIKE N'%demo%' OR p.Name LIKE N'%test%' OR b.Name LIKE N'%demo%' OR b.Name LIKE N'%test%';
-- Candidate mismatches with the curated reference; absence from reference is NOT proof of invalidity.
SELECT p.Id,p.SKU,p.Model,b.Name AS Brand,r.BrandName AS ReferenceBrand,r.ProductName AS ReferenceName
FROM dm.Products p JOIN dm.Brands b ON b.Id=p.BrandId JOIN dm.ReferenceProducts r ON r.Model=p.Model
WHERE b.Name<>r.BrandName OR p.Name<>r.ProductName;
SELECT p.Id,p.SKU,p.IsDemo,d.Mode FROM dm.Products p CROSS JOIN dm.DeploymentInfo d
WHERE (d.Mode='DEMO' AND p.IsDemo=0) OR (d.Mode='PRODUCTION' AND p.IsDemo=1);
GO
