package vn.dienmay.common.model;

import java.math.BigDecimal;

/** Snapshot is informational only; every condition is rechecked under locks when executing. */
public record ReversalPreview(long movementId, long productId, int productRevision, String sku, String name,
    int quantity, int availableStock, BigDecimal receiptCost, BigDecimal restoredCost,
    boolean allowed, String blockReason) {}
