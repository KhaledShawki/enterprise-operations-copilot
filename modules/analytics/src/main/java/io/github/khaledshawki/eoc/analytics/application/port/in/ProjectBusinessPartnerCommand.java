package io.github.khaledshawki.eoc.analytics.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProjectBusinessPartnerCommand(
    UUID eventId,
    UUID tenantId,
    UUID businessPartnerId,
    long aggregateVersion,
    Instant occurredAt,
    String partnerNumber,
    String displayName,
    Set<String> roles) {

  public ProjectBusinessPartnerCommand {
    Objects.requireNonNull(eventId, "Business partner projection event id cannot be null");
    Objects.requireNonNull(tenantId, "Business partner projection tenant id cannot be null");
    Objects.requireNonNull(
        businessPartnerId, "Business partner projection business partner id cannot be null");
    Objects.requireNonNull(occurredAt, "Business partner projection occurredAt cannot be null");
    Objects.requireNonNull(
        partnerNumber, "Business partner projection partner number cannot be null");
    Objects.requireNonNull(displayName, "Business partner projection display name cannot be null");
    Objects.requireNonNull(roles, "Business partner projection roles cannot be null");
    roles = Set.copyOf(roles);
  }
}
