package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import java.util.Objects;
import java.util.regex.Pattern;

public record ImportFailurePayload(String category, String code) {

  private static final Pattern CATEGORY = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final Pattern CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  public ImportFailurePayload {
    Objects.requireNonNull(category, "Failure category cannot be null");
    Objects.requireNonNull(code, "Failure code cannot be null");
    if (!CATEGORY.matcher(category).matches()) {
      throw new IllegalArgumentException("Failure category must be an uppercase contract code");
    }
    if (code.length() > 63 || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException("Failure code must be a bounded kebab-case contract code");
    }
  }
}
