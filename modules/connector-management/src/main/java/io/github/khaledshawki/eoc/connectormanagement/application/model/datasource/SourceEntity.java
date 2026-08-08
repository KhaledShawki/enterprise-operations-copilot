package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Locale;
import java.util.regex.Pattern;

/** Extensible normalized key identifying a source entity type. */
public record SourceEntity(String value) {

  public static final int MAX_LENGTH = 63;

  private static final Pattern VALID_FORMAT = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public static final SourceEntity CUSTOMER = new SourceEntity("customer");
  public static final SourceEntity INVOICE = new SourceEntity("invoice");
  public static final SourceEntity PAYMENT = new SourceEntity("payment");

  public SourceEntity {
    value =
        SourceContractValidation.requiredText(value, "Source entity", MAX_LENGTH)
            .toLowerCase(Locale.ROOT);
    if (!VALID_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Source entity has an invalid format");
    }
  }

  public static SourceEntity of(String value) {
    return new SourceEntity(value);
  }
}
