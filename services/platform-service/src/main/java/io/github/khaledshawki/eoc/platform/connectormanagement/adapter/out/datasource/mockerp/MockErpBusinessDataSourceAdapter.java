package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure.Category.INVALID_POSITION;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure.Category.SOURCE_CONTRACT_VIOLATION;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.CUSTOMER;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.INVOICE;
import static io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity.PAYMENT;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaIssue;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp.MockErpFixture.FixtureRecord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
final class MockErpBusinessDataSourceAdapter implements BusinessDataSource {

  static final ConnectorType CONNECTOR_TYPE = ConnectorType.of("mock-erp");

  private static final String POSITION_PREFIX = "mock-erp";
  private static final String POSITION_SEPARATOR = "|";

  private static final BusinessDataSourceFailure AUTHENTICATION_FAILURE =
      new BusinessDataSourceFailure(AUTHENTICATION_FAILED, "mock-authentication-failed");
  private static final BusinessDataSourceFailure UNAVAILABLE_FAILURE =
      new BusinessDataSourceFailure(SOURCE_UNAVAILABLE, "mock-source-unavailable");
  private static final BusinessDataSourceFailure RETRIEVAL_FAILURE =
      new BusinessDataSourceFailure(SOURCE_UNAVAILABLE, "mock-retrieval-failed");
  private static final BusinessDataSourceFailure SCHEMA_FAILURE =
      new BusinessDataSourceFailure(SOURCE_CONTRACT_VIOLATION, "mock-schema-incompatible");

  private final MockErpFixture fixture;
  private final MockErpScenario scenario;

  MockErpBusinessDataSourceAdapter() {
    this(MockErpFixture.standard(), MockErpScenario.HEALTHY);
  }

  MockErpBusinessDataSourceAdapter(MockErpFixture fixture, MockErpScenario scenario) {
    this.fixture = Objects.requireNonNull(fixture, "Mock ERP fixture cannot be null");
    this.scenario = Objects.requireNonNull(scenario, "Mock ERP scenario cannot be null");
  }

  @Override
  public ConnectorType supportedConnectorType() {
    return CONNECTOR_TYPE;
  }

  @Override
  public ConnectionTestResult testConnection(BusinessDataSourceConfiguration configuration) {
    requireSupportedConfiguration(configuration);
    return switch (scenario) {
      case AUTHENTICATION_FAILED -> ConnectionTestResult.failed(AUTHENTICATION_FAILURE);
      case SOURCE_UNAVAILABLE -> ConnectionTestResult.failed(UNAVAILABLE_FAILURE);
      default -> ConnectionTestResult.connected();
    };
  }

  @Override
  public SourceSchemaVerificationResult verifySourceSchema(
      BusinessDataSourceConfiguration configuration) {
    requireSupportedConfiguration(configuration);
    requireSourceAvailable();
    if (scenario == MockErpScenario.INCOMPATIBLE_SCHEMA) {
      return SourceSchemaVerificationResult.withIssues(
          List.of(SourceSchemaIssue.missingField(INVOICE, "dueDate")));
    }
    return SourceSchemaVerificationResult.verified();
  }

  @Override
  public SourcePage<SourceCustomerRecord> retrieveCustomers(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    requireRetrievalAvailable();
    return retrievePage(CUSTOMER, fixture.customers(), fetchRequest);
  }

  @Override
  public SourcePage<SourceInvoiceRecord> retrieveInvoices(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    requireRetrievalAvailable();
    return retrievePage(INVOICE, fixture.invoices(), fetchRequest);
  }

  @Override
  public SourcePage<SourcePaymentRecord> retrievePayments(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    requireRetrievalAvailable();
    return retrievePage(PAYMENT, fixture.payments(), fetchRequest);
  }

  private void requireSourceAvailable() {
    switch (scenario) {
      case AUTHENTICATION_FAILED -> throw new BusinessDataSourceException(AUTHENTICATION_FAILURE);
      case SOURCE_UNAVAILABLE -> throw new BusinessDataSourceException(UNAVAILABLE_FAILURE);
      default -> {
        // Other deterministic scenarios can reach the source.
      }
    }
  }

  private void requireRetrievalAvailable() {
    requireSourceAvailable();
    switch (scenario) {
      case INCOMPATIBLE_SCHEMA -> throw new BusinessDataSourceException(SCHEMA_FAILURE);
      case RETRIEVAL_FAILURE -> throw new BusinessDataSourceException(RETRIEVAL_FAILURE);
      default -> {
        // The healthy scenario can retrieve fixture records.
      }
    }
  }

  private static void requireSupportedConfiguration(BusinessDataSourceConfiguration configuration) {
    Objects.requireNonNull(configuration, "Business data source configuration cannot be null");
    if (!CONNECTOR_TYPE.equals(configuration.connectorType())) {
      throw new IllegalArgumentException(
          "Mock ERP data source does not support connector type "
              + configuration.connectorType().value());
    }
  }

  private static <T> SourcePage<T> retrievePage(
      SourceEntity entity, List<FixtureRecord<T>> fixtureRecords, SourceFetchRequest fetchRequest) {
    Objects.requireNonNull(fetchRequest, "Source fetch request cannot be null");
    Position position = resolvePosition(entity, fetchRequest);
    List<FixtureRecord<T>> availableRecords =
        fixtureRecords.stream()
            .filter(record -> record.sequence() > position.afterSequence())
            .toList();

    if (position.offset() > availableRecords.size()) {
      throw invalidPosition("mock-invalid-page-token");
    }

    int pageEnd = Math.min(position.offset() + fetchRequest.pageSize(), availableRecords.size());
    List<FixtureRecord<T>> pageRecords = availableRecords.subList(position.offset(), pageEnd);
    List<T> records = pageRecords.stream().map(FixtureRecord::record).toList();

    Optional<SourcePageToken> nextPageToken =
        pageEnd < availableRecords.size()
            ? Optional.of(pageToken(entity, position.afterSequence(), pageEnd))
            : Optional.empty();
    Optional<IncrementalCursor> candidateCursor =
        pageRecords.isEmpty()
            ? fetchRequest.incrementalCursor()
            : Optional.of(cursor(entity, pageRecords.getLast().sequence()));

    return new SourcePage<>(records, nextPageToken, candidateCursor);
  }

  private static Position resolvePosition(SourceEntity entity, SourceFetchRequest fetchRequest) {
    if (fetchRequest.pageToken().isPresent()) {
      return parsePageToken(entity, fetchRequest.pageToken().orElseThrow());
    }

    long afterSequence =
        fetchRequest.incrementalCursor().map(cursor -> parseCursor(entity, cursor)).orElse(0L);
    return new Position(afterSequence, 0);
  }

  private static Position parsePageToken(SourceEntity entity, SourcePageToken pageToken) {
    String[] parts = pageToken.value().split("\\|", -1);
    if (parts.length != 4
        || !POSITION_PREFIX.equals(parts[0])
        || !entity.value().equals(parts[1])) {
      throw invalidPosition("mock-invalid-page-token");
    }
    return new Position(
        parseNonNegativeLong(parts[2], "mock-invalid-page-token"),
        parseNonNegativeInt(parts[3], "mock-invalid-page-token"));
  }

  private static long parseCursor(SourceEntity entity, IncrementalCursor cursor) {
    String[] parts = cursor.value().split("\\|", -1);
    if (parts.length != 3
        || !POSITION_PREFIX.equals(parts[0])
        || !entity.value().equals(parts[1])) {
      throw invalidPosition("mock-invalid-incremental-cursor");
    }
    return parseNonNegativeLong(parts[2], "mock-invalid-incremental-cursor");
  }

  private static long parseNonNegativeLong(String value, String diagnosticCode) {
    try {
      long result = Long.parseLong(value);
      if (result < 0) {
        throw invalidPosition(diagnosticCode);
      }
      return result;
    } catch (NumberFormatException exception) {
      throw invalidPosition(diagnosticCode, exception);
    }
  }

  private static int parseNonNegativeInt(String value, String diagnosticCode) {
    try {
      int result = Integer.parseInt(value);
      if (result < 0) {
        throw invalidPosition(diagnosticCode);
      }
      return result;
    } catch (NumberFormatException exception) {
      throw invalidPosition(diagnosticCode, exception);
    }
  }

  private static SourcePageToken pageToken(SourceEntity entity, long afterSequence, int offset) {
    return new SourcePageToken(
        String.join(
            POSITION_SEPARATOR,
            POSITION_PREFIX,
            entity.value(),
            Long.toString(afterSequence),
            Integer.toString(offset)));
  }

  private static IncrementalCursor cursor(SourceEntity entity, long sequence) {
    return new IncrementalCursor(
        String.join(POSITION_SEPARATOR, POSITION_PREFIX, entity.value(), Long.toString(sequence)));
  }

  private static BusinessDataSourceException invalidPosition(String diagnosticCode) {
    return new BusinessDataSourceException(
        new BusinessDataSourceFailure(INVALID_POSITION, diagnosticCode));
  }

  private static BusinessDataSourceException invalidPosition(
      String diagnosticCode, RuntimeException cause) {
    return new BusinessDataSourceException(
        new BusinessDataSourceFailure(INVALID_POSITION, diagnosticCode), cause);
  }

  private record Position(long afterSequence, int offset) {}
}
