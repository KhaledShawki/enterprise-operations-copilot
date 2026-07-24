package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;

public record ProvisionPlatformUserResult(
    PlatformUserId userId,
    ExternalIdentity externalIdentity,
    PlatformUserStatus status,
    boolean created) {}
