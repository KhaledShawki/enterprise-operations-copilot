package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.List;
import java.util.Objects;

/** Immutable result of verifying the source schema required by an adapter. */
public record SourceSchemaVerificationResult(List<SourceSchemaIssue> issues) {

  public SourceSchemaVerificationResult {
    Objects.requireNonNull(issues, "Source schema issues cannot be null");
    if (issues.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Source schema issues cannot contain null values");
    }
    issues = List.copyOf(issues);
  }

  public static SourceSchemaVerificationResult verified() {
    return new SourceSchemaVerificationResult(List.of());
  }

  public static SourceSchemaVerificationResult withIssues(List<SourceSchemaIssue> issues) {
    return new SourceSchemaVerificationResult(issues);
  }

  public boolean isCompatible() {
    return issues.isEmpty();
  }
}
