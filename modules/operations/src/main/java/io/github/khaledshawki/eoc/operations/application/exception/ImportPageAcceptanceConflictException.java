package io.github.khaledshawki.eoc.operations.application.exception;

import java.util.UUID;

public final class ImportPageAcceptanceConflictException extends RuntimeException {

  public ImportPageAcceptanceConflictException(UUID pageAcceptanceId) {
    super("Import page acceptance id was reused with a different payload: " + pageAcceptanceId);
  }
}
