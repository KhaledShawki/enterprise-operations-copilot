package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import java.util.Objects;

public final class ConcurrentImportRunModificationException extends RuntimeException {

  public ConcurrentImportRunModificationException(ImportRunId importRunId) {
    super(message(importRunId));
  }

  public ConcurrentImportRunModificationException(ImportRunId importRunId, Throwable cause) {
    super(message(importRunId), cause);
  }

  private static String message(ImportRunId importRunId) {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    return "Import run %s was modified concurrently".formatted(importRunId.value());
  }
}
