package io.github.khaledshawki.eoc.operations.application.model.importing;

import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record PaymentImportReceipt(String payloadFingerprint, PaymentImportResult result) {

  public static final int SHA_256_HEX_LENGTH = 64;
  private static final Pattern SHA_256_HEX_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

  public PaymentImportReceipt {
    Objects.requireNonNull(payloadFingerprint, "Import payload fingerprint cannot be null");
    payloadFingerprint = payloadFingerprint.toLowerCase(Locale.ROOT);
    if (!SHA_256_HEX_FORMAT.matcher(payloadFingerprint).matches()) {
      throw new IllegalArgumentException(
          "Import payload fingerprint must be a SHA-256 hexadecimal value");
    }
    Objects.requireNonNull(result, "Payment import result cannot be null");
  }
}
