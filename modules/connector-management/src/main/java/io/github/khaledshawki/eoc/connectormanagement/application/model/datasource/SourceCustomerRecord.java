package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.Optional;

/** Normalized customer data returned by a source adapter, not an owned business aggregate. */
public record SourceCustomerRecord(
    SourceRecordMetadata metadata,
    String customerNumber,
    String displayName,
    Optional<String> emailAddress) {

  public static final int MAX_CUSTOMER_NUMBER_LENGTH = 100;
  public static final int MAX_DISPLAY_NAME_LENGTH = 255;
  public static final int MAX_EMAIL_ADDRESS_LENGTH = 320;

  public SourceCustomerRecord {
    Objects.requireNonNull(metadata, "Customer source metadata cannot be null");
    if (!SourceEntity.CUSTOMER.equals(metadata.identity().entity())) {
      throw new IllegalArgumentException("Customer source identity must use the customer entity");
    }
    customerNumber =
        SourceContractValidation.requiredText(
            customerNumber, "Customer number", MAX_CUSTOMER_NUMBER_LENGTH);
    displayName =
        SourceContractValidation.requiredText(
            displayName, "Customer display name", MAX_DISPLAY_NAME_LENGTH);
    emailAddress =
        SourceContractValidation.optionalText(
            emailAddress, "Customer email address", MAX_EMAIL_ADDRESS_LENGTH);
  }
}
