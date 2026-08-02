package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

/**
 * Opaque durable starting position for a new incremental scan. Only the adapter that created the
 * cursor may interpret its value.
 */
public record IncrementalCursor(String value) {

  public static final int MAX_LENGTH = 2048;

  public IncrementalCursor {
    value = SourceContractValidation.requiredText(value, "Incremental cursor", MAX_LENGTH);
  }
}
