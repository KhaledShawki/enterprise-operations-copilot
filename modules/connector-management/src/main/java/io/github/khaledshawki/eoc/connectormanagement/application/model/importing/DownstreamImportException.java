package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import java.util.Objects;

/** Sanitized expected failure raised by a downstream import adapter. */
public final class DownstreamImportException extends RuntimeException {

  private final ImportFailure failure;

  public DownstreamImportException(ImportFailure failure, Throwable cause) {
    super(requireFailure(failure).diagnosticCode(), cause);
    this.failure = failure;
  }

  public ImportFailure failure() {
    return failure;
  }

  private static ImportFailure requireFailure(ImportFailure failure) {
    return Objects.requireNonNull(failure, "Downstream import failure cannot be null");
  }
}
