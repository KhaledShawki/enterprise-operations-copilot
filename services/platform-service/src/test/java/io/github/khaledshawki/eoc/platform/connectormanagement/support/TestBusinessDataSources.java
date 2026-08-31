package io.github.khaledshawki.eoc.platform.connectormanagement.support;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/** Small transport-independent source double for connector orchestration tests. */
public final class TestBusinessDataSources {

  private static final ConnectorType TYPE = ConnectorType.of("mock-erp");

  private TestBusinessDataSources() {}

  public static BusinessDataSource healthy() {
    return new HealthySource();
  }

  private static final class HealthySource implements BusinessDataSource {

    private static final List<Sequenced<SourceCustomerRecord>> CUSTOMERS =
        List.of(
            new Sequenced<>(1, customer("customer-1000", "C1000", "Acme Manufacturing")),
            new Sequenced<>(2, customer("customer-2000", "C2000", "Northwind Traders")),
            new Sequenced<>(3, customer("customer-3000", "C3000", "Globex Corporation")));

    private static final List<Sequenced<SourcePaymentRecord>> PAYMENTS =
        List.of(
            new Sequenced<>(1, payment("payment-1000", "customer-1000", "400.00", false)),
            new Sequenced<>(2, payment("payment-2000", "customer-2000", "600.00", false)),
            new Sequenced<>(3, payment("payment-2001", "customer-2000", "125.00", true)));

    @Override
    public ConnectorType supportedConnectorType() {
      return TYPE;
    }

    @Override
    public ConnectionTestResult testConnection(BusinessDataSourceConfiguration configuration) {
      return ConnectionTestResult.connected();
    }

    @Override
    public SourceSchemaVerificationResult verifySourceSchema(
        BusinessDataSourceConfiguration configuration) {
      return SourceSchemaVerificationResult.verified();
    }

    @Override
    public SourcePage<SourceCustomerRecord> retrieveCustomers(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest request) {
      return page(SourceEntity.CUSTOMER, CUSTOMERS, request);
    }

    @Override
    public SourcePage<SourceInvoiceRecord> retrieveInvoices(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest request) {
      return page(SourceEntity.INVOICE, List.of(), request);
    }

    @Override
    public SourcePage<SourcePaymentRecord> retrievePayments(
        BusinessDataSourceConfiguration configuration, SourceFetchRequest request) {
      return page(SourceEntity.PAYMENT, PAYMENTS, request);
    }
  }

  private static <T> SourcePage<T> page(
      SourceEntity entity, List<Sequenced<T>> source, SourceFetchRequest request) {
    Position position =
        request
            .pageToken()
            .map(token -> pagePosition(entity, token))
            .orElseGet(
                () ->
                    new Position(
                        request
                            .incrementalCursor()
                            .map(cursor -> cursorSequence(entity, cursor))
                            .orElse(0L),
                        0));
    long after = position.afterSequence();
    int offset = position.offset();
    List<Sequenced<T>> available =
        source.stream().filter(record -> record.sequence() > after).toList();
    if (offset > available.size()) {
      throw new IllegalArgumentException("Unexpected test page token offset");
    }
    int end = Math.min(offset + request.pageSize(), available.size());
    List<Sequenced<T>> selected = available.subList(offset, end);
    Optional<SourcePageToken> next =
        end < available.size()
            ? Optional.of(
                new SourcePageToken("test-source|" + entity.value() + "|" + after + "|" + end))
            : Optional.empty();
    Optional<IncrementalCursor> candidate =
        selected.isEmpty()
            ? request.incrementalCursor()
            : Optional.of(
                new IncrementalCursor(
                    "test-source|" + entity.value() + "|" + selected.getLast().sequence()));
    return new SourcePage<>(selected.stream().map(Sequenced::value).toList(), next, candidate);
  }

  private static Position pagePosition(SourceEntity entity, SourcePageToken token) {
    String[] parts = token.value().split("\\|", -1);
    if (parts.length != 4 || !"test-source".equals(parts[0]) || !entity.value().equals(parts[1])) {
      throw new IllegalArgumentException("Unexpected test page token");
    }
    return new Position(Long.parseLong(parts[2]), Integer.parseInt(parts[3]));
  }

  private static long cursorSequence(SourceEntity entity, IncrementalCursor cursor) {
    String[] parts = cursor.value().split("\\|", -1);
    if (parts.length != 3 || !"test-source".equals(parts[0]) || !entity.value().equals(parts[1])) {
      throw new IllegalArgumentException("Unexpected test cursor");
    }
    return Long.parseLong(parts[2]);
  }

  private static SourceCustomerRecord customer(String sourceId, String number, String name) {
    return new SourceCustomerRecord(
        metadata(SourceEntity.CUSTOMER, sourceId), number, name, Optional.empty());
  }

  private static SourcePaymentRecord payment(
      String sourceId, String customerSourceId, String amount, boolean reversed) {
    return new SourcePaymentRecord(
        metadata(SourceEntity.PAYMENT, sourceId),
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, customerSourceId),
        LocalDate.of(2026, 2, 10),
        Currency.getInstance("USD"),
        new BigDecimal(amount),
        reversed);
  }

  private static SourceRecordMetadata metadata(SourceEntity entity, String sourceId) {
    return new SourceRecordMetadata(
        SourceIdentity.sourceRecordId(entity, sourceId),
        new SourceModificationVersion("v1"),
        Optional.of(Instant.parse("2026-01-01T00:00:00Z")));
  }

  private record Position(long afterSequence, int offset) {}

  private record Sequenced<T>(long sequence, T value) {}
}
