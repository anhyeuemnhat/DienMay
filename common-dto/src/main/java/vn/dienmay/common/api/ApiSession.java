package vn.dienmay.common.api;

import java.time.Instant;
import vn.dienmay.common.model.Role;

/** Display metadata plus an opaque bearer token; server never trusts client-supplied role fields. */
public record ApiSession(String token, long employeeId, String name, Role role,
    boolean mustChange, Instant expiresAt) {}
