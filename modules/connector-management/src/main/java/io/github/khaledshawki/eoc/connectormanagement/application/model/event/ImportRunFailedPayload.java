package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ImportRunFailedPayload(
    UUID connectorId,
    String importType,
    String importMode,
    ImportFailurePayload failure,
    int attemptCount)
    implements ConnectorIntegrationEventPayload {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public ImportRunFailedPayload {
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    importType = requireCode(importType, "Import type");
    importMode = requireCode(importMode, "Import mode");
    Objects.requireNonNull(failure, "Import failure cannot be null");
    if (attemptCount < 1) {
      throw new IllegalArgumentException("Attempt count must be positive");
    }
  }

  private static String requireCode(String value, String field) {
    Objects.requireNonNull(value, field + " cannot be null");
    if (!CODE.matcher(value).matches()) {
      throw new IllegalArgumentException(field + " must be an uppercase contract code");
    }
    return value;
  }
}
