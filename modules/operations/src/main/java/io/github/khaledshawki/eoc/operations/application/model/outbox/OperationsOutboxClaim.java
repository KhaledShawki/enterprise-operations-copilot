package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record OperationsOutboxClaim(
    String claimOwner, int batchSize, Instant claimedAt, Duration claimLease) {

  public static final int MAX_BATCH_SIZE = 100;
  public static final Duration MAX_CLAIM_LEASE = Duration.ofHours(1);
  private static final Pattern CLAIM_OWNER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

  public OperationsOutboxClaim {
    claimOwner = requireClaimOwner(claimOwner);
    Objects.requireNonNull(claimedAt, "Claim timestamp cannot be null");
    Objects.requireNonNull(claimLease, "Claim lease cannot be null");
    if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Operations outbox batch size must be between 1 and 100");
    }
    if (claimLease.isZero()
        || claimLease.isNegative()
        || claimLease.compareTo(MAX_CLAIM_LEASE) > 0) {
      throw new IllegalArgumentException(
          "Operations outbox claim lease must be positive and at most one hour");
    }
  }

  public Instant staleBefore() {
    return claimedAt.minus(claimLease);
  }

  public static String requireClaimOwner(String value) {
    Objects.requireNonNull(value, "Claim owner cannot be null");
    if (!CLAIM_OWNER.matcher(value).matches()) {
      throw new IllegalArgumentException("Claim owner must be a bounded worker identifier");
    }
    return value;
  }
}
