package vn.dienmay.common.model;

import java.util.UUID;

/** Durable, immutable administrative intent; no authentication secrets are persisted. */
public interface GuardedRequest {
  UUID id();
  String fingerprint();
}
