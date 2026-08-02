package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.Optional;

/** One sanitized incompatibility between the expected and available source schema. */
public record SourceSchemaIssue(SourceEntity entity, Optional<String> field, Type type) {

  public static final int MAX_FIELD_LENGTH = 255;

  public SourceSchemaIssue {
    Objects.requireNonNull(entity, "Schema issue source entity cannot be null");
    field = SourceContractValidation.optionalText(field, "Schema issue field", MAX_FIELD_LENGTH);
    Objects.requireNonNull(type, "Schema issue type cannot be null");
    if (type == Type.MISSING_REQUIRED_ENTITY && field.isPresent()) {
      throw new IllegalArgumentException("A missing source entity issue cannot contain a field");
    }
    if (type != Type.MISSING_REQUIRED_ENTITY && field.isEmpty()) {
      throw new IllegalArgumentException("A source field issue requires a field");
    }
  }

  public static SourceSchemaIssue missingEntity(SourceEntity entity) {
    return new SourceSchemaIssue(entity, Optional.empty(), Type.MISSING_REQUIRED_ENTITY);
  }

  public static SourceSchemaIssue missingField(SourceEntity entity, String field) {
    return new SourceSchemaIssue(entity, Optional.of(field), Type.MISSING_REQUIRED_FIELD);
  }

  public static SourceSchemaIssue incompatibleFieldType(SourceEntity entity, String field) {
    return new SourceSchemaIssue(entity, Optional.of(field), Type.INCOMPATIBLE_FIELD_TYPE);
  }

  public enum Type {
    MISSING_REQUIRED_ENTITY,
    MISSING_REQUIRED_FIELD,
    INCOMPATIBLE_FIELD_TYPE
  }
}
