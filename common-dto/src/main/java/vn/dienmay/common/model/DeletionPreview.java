package vn.dienmay.common.model;

/** Current snapshot and human-readable blocker from the database guard. Not a permission token. */
public record DeletionPreview(String type, long entityId, int revision, String code, String name,
    boolean allowed, String blockReason) {}
