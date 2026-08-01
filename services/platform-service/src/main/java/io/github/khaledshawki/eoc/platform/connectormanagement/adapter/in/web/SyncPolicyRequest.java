package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record SyncPolicyRequest(
    @NotNull(message = "Sync policy mode is required") SyncPolicy.Mode mode,
    @NotNull(message = "Sync policy interval is required") Duration interval) {}
