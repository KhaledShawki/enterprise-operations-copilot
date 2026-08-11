package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record BusinessPartnerSynchronizedPayload(
    UUID businessPartnerId,
    String partnerNumber,
    String displayName,
    List<String> roles,
    SourceRecordEvidence source)
    implements OperationsIntegrationEventPayload {

  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public BusinessPartnerSynchronizedPayload {
    Objects.requireNonNull(businessPartnerId, "Event business partner id cannot be null");
    BusinessPartnerProfile profile =
        new BusinessPartnerProfile(partnerNumber, displayName, java.util.Optional.empty());
    partnerNumber = profile.partnerNumber();
    displayName = profile.displayName();
    Objects.requireNonNull(roles, "Event business partner roles cannot be null");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("Event business partner roles cannot be empty");
    }
    if (roles.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Event business partner roles cannot contain null");
    }
    List<String> canonicalRoles =
        roles.stream()
            .map(BusinessPartnerSynchronizedPayload::requireRole)
            .distinct()
            .sorted()
            .toList();
    if (canonicalRoles.size() != roles.size()) {
      throw new IllegalArgumentException("Event business partner roles cannot contain duplicates");
    }
    roles = canonicalRoles;
    Objects.requireNonNull(source, "Event business partner source cannot be null");
  }

  @Override
  public UUID aggregateId() {
    return businessPartnerId;
  }

  private static String requireRole(String role) {
    if (!ROLE.matcher(role).matches()) {
      throw new IllegalArgumentException(
          "Event business partner role must be an uppercase contract code");
    }
    return role;
  }
}
