package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

/**
 * Opaque, potentially temporary continuation token for the current source scan. Only the adapter
 * that created the token may interpret its value.
 */
public record SourcePageToken(String value) {

  public static final int MAX_LENGTH = 2048;

  public SourcePageToken {
    value = SourceContractValidation.requiredText(value, "Source page token", MAX_LENGTH);
  }
}
