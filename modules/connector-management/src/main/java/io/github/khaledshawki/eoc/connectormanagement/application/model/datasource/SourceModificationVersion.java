package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

/**
 * Opaque source-provided version used for equality and idempotency. Versions from heterogeneous
 * sources are deliberately not comparable.
 */
public record SourceModificationVersion(String value) {

  public static final int MAX_LENGTH = 512;

  public SourceModificationVersion {
    value = SourceContractValidation.requiredText(value, "Source modification version", MAX_LENGTH);
  }
}
