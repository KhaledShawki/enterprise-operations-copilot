package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import java.util.Objects;

public final class ImportRunNotExecutableException extends RuntimeException {

  public ImportRunNotExecutableException(ImportRunId importRunId, ImportStatus status) {
    super(message(importRunId, status));
  }

  private static String message(ImportRunId importRunId, ImportStatus status) {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(status, "Import run status cannot be null");
    return "Import run %s cannot execute while its status is %s"
        .formatted(importRunId.value(), status);
  }
}
