package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record OperationsOutboxPublicationRetry(
    UUID eventId,
    String claimOwner,
    int publicationAttempt,
    String failureCode,
    Instant nextPublishAt,
    Instant recordedAt) {

  private static final Pattern FAILURE_CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  public OperationsOutboxPublicationRetry {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    claimOwner = OperationsOutboxClaim.requireClaimOwner(claimOwner);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    failureCode = requireFailureCode(failureCode);
    Objects.requireNonNull(nextPublishAt, "Next publication timestamp cannot be null");
    Objects.requireNonNull(recordedAt, "Retry recording timestamp cannot be null");
    if (!nextPublishAt.isAfter(recordedAt)) {
      throw new IllegalArgumentException("Next publication timestamp must follow retry recording");
    }
  }

  public static String requireFailureCode(String value) {
    Objects.requireNonNull(value, "Publication failure code cannot be null");
    if (value.length() > 128 || !FAILURE_CODE.matcher(value).matches()) {
      throw new IllegalArgumentException("Publication failure code must be bounded kebab-case");
    }
    return value;
  }
}
