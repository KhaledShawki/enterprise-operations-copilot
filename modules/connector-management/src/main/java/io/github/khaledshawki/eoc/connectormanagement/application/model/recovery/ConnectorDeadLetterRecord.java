package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ConnectorDeadLetterRecord(
    ConnectorDeadLetterReference reference,
    String deadLetterTopic,
    Optional<String> key,
    Optional<String> value,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    Instant sourceTimestamp,
    String failureCode,
    boolean retryable,
    String failureType,
    Optional<String> failureMessage,
    int replayGeneration,
    List<ConnectorDeadLetterHeader> replayHeaders) {

  public ConnectorDeadLetterRecord {
    Objects.requireNonNull(reference, "Dead-letter reference cannot be null");
    deadLetterTopic = requireText(deadLetterTopic, "Dead-letter topic", 249);
    Objects.requireNonNull(key, "Dead-letter key optional cannot be null");
    Objects.requireNonNull(value, "Dead-letter value optional cannot be null");
    sourceTopic = requireText(sourceTopic, "Dead-letter source topic", 249);
    if (sourcePartition < 0 || sourceOffset < 0) {
      throw new IllegalArgumentException("Dead-letter source coordinates are invalid");
    }
    Objects.requireNonNull(sourceTimestamp, "Dead-letter source timestamp cannot be null");
    failureCode = requireText(failureCode, "Dead-letter failure code", 160);
    failureType = requireText(failureType, "Dead-letter failure type", 500);
    Objects.requireNonNull(failureMessage, "Dead-letter failure message optional cannot be null");
    failureMessage = failureMessage.map(message -> requireText(message, "Failure message", 2000));
    if (replayGeneration < 0 || replayGeneration > 100) {
      throw new IllegalArgumentException("Dead-letter replay generation is invalid");
    }
    replayHeaders = List.copyOf(replayHeaders);
  }

  public String fingerprint() {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
    update(digest, deadLetterTopic);
    update(digest, Integer.toString(reference.partition()));
    update(digest, Long.toString(reference.offset()));
    updateOptional(digest, key);
    updateOptional(digest, value);
    update(digest, sourceTopic);
    update(digest, Integer.toString(sourcePartition));
    update(digest, Long.toString(sourceOffset));
    update(digest, sourceTimestamp.toString());
    update(digest, failureCode);
    update(digest, Boolean.toString(retryable));
    update(digest, failureType);
    updateOptional(digest, failureMessage);
    update(digest, Integer.toString(replayGeneration));
    for (ConnectorDeadLetterHeader header : replayHeaders) {
      update(digest, header.name());
      update(digest, header.valueBase64());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String requireText(String value, String description, int maxLength) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(description + " is invalid");
    }
    return value;
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static void updateOptional(MessageDigest digest, Optional<String> value) {
    update(digest, Boolean.toString(value.isPresent()));
    value.ifPresent(present -> update(digest, present));
  }
}
