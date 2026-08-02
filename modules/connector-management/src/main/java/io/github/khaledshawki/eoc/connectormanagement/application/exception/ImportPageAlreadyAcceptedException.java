package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import java.util.Objects;

public final class ImportPageAlreadyAcceptedException extends RuntimeException {

  public ImportPageAlreadyAcceptedException(
      ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
    super(message(importRunId, acceptanceId));
  }

  public ImportPageAlreadyAcceptedException(
      ImportRunId importRunId, ImportPageAcceptanceId acceptanceId, Throwable cause) {
    super(message(importRunId, acceptanceId), cause);
  }

  private static String message(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId) {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(acceptanceId, "Import page acceptance id cannot be null");
    return "Import page acceptance %s was already recorded for run %s"
        .formatted(acceptanceId.value(), importRunId.value());
  }
}
