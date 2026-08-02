package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.Optional;

public record BusinessPartnerProfile(
    String partnerNumber, String displayName, Optional<String> emailAddress) {

  public static final int MAX_PARTNER_NUMBER_LENGTH = 100;
  public static final int MAX_DISPLAY_NAME_LENGTH = 255;
  public static final int MAX_EMAIL_ADDRESS_LENGTH = 320;

  public BusinessPartnerProfile {
    partnerNumber =
        requiredText(partnerNumber, "Business partner number", MAX_PARTNER_NUMBER_LENGTH);
    displayName = requiredText(displayName, "Display name", MAX_DISPLAY_NAME_LENGTH);
    emailAddress = optionalText(emailAddress, "Email address", MAX_EMAIL_ADDRESS_LENGTH);
  }

  private static String requiredText(String value, String field, int maxLength) {
    Objects.requireNonNull(value, field + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be blank");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
    }
    return normalized;
  }

  private static Optional<String> optionalText(
      Optional<String> value, String field, int maxLength) {
    Objects.requireNonNull(value, field + " optional cannot be null");
    if (value.isEmpty()) {
      return Optional.empty();
    }
    String normalized = value.orElseThrow().strip();
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
    }
    return Optional.of(normalized);
  }
}
