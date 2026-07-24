package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public record ExternalIdentity(String issuer, String subject) {

  public static final int MAX_ISSUER_LENGTH = 2048;
  public static final int MAX_SUBJECT_LENGTH = 255;

  public ExternalIdentity {
    Objects.requireNonNull(issuer, "External identity issuer cannot be null");
    Objects.requireNonNull(subject, "External identity subject cannot be null");

    if (issuer.isBlank()) {
      throw new IllegalArgumentException("External identity issuer cannot be blank");
    }

    if (issuer.length() > MAX_ISSUER_LENGTH) {
      throw new IllegalArgumentException(
          "External identity issuer cannot be longer than " + MAX_ISSUER_LENGTH + " characters");
    }

    if (subject.isBlank()) {
      throw new IllegalArgumentException("External identity subject cannot be blank");
    }

    if (subject.length() > MAX_SUBJECT_LENGTH) {
      throw new IllegalArgumentException(
          "External identity subject cannot be longer than " + MAX_SUBJECT_LENGTH + " characters");
    }
  }

  public static ExternalIdentity of(String issuer, String subject) {
    return new ExternalIdentity(issuer, subject);
  }
}
