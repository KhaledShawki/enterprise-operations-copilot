package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessDataSourceContractTest {

  @Test
  void shouldCreateAnImmutableConfigurationSnapshotWithoutPlaintextCredentials() {
    Connector connector =
        Connector.create(
            ConnectorTenantId.of(UUID.fromString("d0d3437a-a764-468c-980c-515621b27319")),
            ConnectorName.of("Mock ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://mock-erp.example/api"),
            CredentialReference.of(UUID.fromString("f27f19a0-c23c-45a2-aa14-5cdffdc4b408")),
            SyncPolicy.scheduled(Duration.ofMinutes(15)));

    BusinessDataSourceConfiguration configuration = BusinessDataSourceConfiguration.from(connector);

    assertEquals(connector.id(), configuration.connectorId());
    assertEquals(connector.tenantId(), configuration.tenantId());
    assertEquals(connector.type(), configuration.connectorType());
    assertEquals(connector.endpoint(), configuration.endpoint());
    assertEquals(connector.credentialReference(), configuration.credentialReference());
  }

  @Test
  void shouldSupportStableRecordIdsAndCanonicalHashFallbackIdentities() {
    SourceIdentity sourceRecordId =
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, " customer-1000 ");
    SourceIdentity canonicalHash =
        SourceIdentity.canonicalRecordHash(SourceEntity.CUSTOMER, "AB".repeat(32));

    assertEquals(SourceIdentity.Kind.SOURCE_RECORD_ID, sourceRecordId.kind());
    assertEquals("customer-1000", sourceRecordId.value());
    assertEquals(SourceIdentity.Kind.CANONICAL_RECORD_HASH, canonicalHash.kind());
    assertEquals("ab".repeat(32), canonicalHash.value());
    assertThrows(
        IllegalArgumentException.class,
        () -> SourceIdentity.canonicalRecordHash(SourceEntity.CUSTOMER, "not-a-sha-256-hash"));
  }

  @Test
  void shouldKeepOpaqueVersionAndOptionalSourceModificationTimeSeparate() {
    Instant sourceModifiedAt = Instant.parse("2026-02-10T16:20:00Z");
    SourceRecordMetadata metadata =
        new SourceRecordMetadata(
            SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1000"),
            new SourceModificationVersion(" etag-7 "),
            Optional.of(sourceModifiedAt));

    assertEquals("etag-7", metadata.modificationVersion().value());
    assertEquals(Optional.of(sourceModifiedAt), metadata.sourceModifiedAt());
    assertTrue(
        SourceRecordMetadata.withoutModificationTimestamp(
                metadata.identity(), metadata.modificationVersion())
            .sourceModifiedAt()
            .isEmpty());
  }

  @Test
  void shouldModelInitialIncrementalAndContinuationRequestsAsDistinctPositions() {
    IncrementalCursor cursor = new IncrementalCursor("customer-cursor");
    SourcePageToken pageToken = new SourcePageToken("customer-page");

    assertEquals(Optional.empty(), SourceFetchRequest.initial(100).incrementalCursor());
    assertEquals(Optional.of(cursor), SourceFetchRequest.after(100, cursor).incrementalCursor());
    assertEquals(
        Optional.of(pageToken), SourceFetchRequest.continueWith(100, pageToken).pageToken());
  }

  @Test
  void shouldRejectInvalidOrAmbiguousFetchRequests() {
    assertThrows(IllegalArgumentException.class, () -> SourceFetchRequest.initial(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> SourceFetchRequest.initial(SourceFetchRequest.MAX_PAGE_SIZE + 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceFetchRequest(
                100,
                Optional.of(new SourcePageToken("next")),
                Optional.of(new IncrementalCursor("after"))));
    assertThrows(NullPointerException.class, () -> SourceFetchRequest.after(100, null));
    assertThrows(NullPointerException.class, () -> SourceFetchRequest.continueWith(100, null));
  }

  @Test
  void shouldDefensivelyCopyRetrievedPages() {
    List<String> mutableRecords = new ArrayList<>(List.of("first"));
    SourcePage<String> page = new SourcePage<>(mutableRecords, Optional.empty(), Optional.empty());

    mutableRecords.add("second");

    assertEquals(List.of("first"), page.records());
    assertThrows(UnsupportedOperationException.class, () -> page.records().add("third"));
    List<String> recordsContainingNull = new ArrayList<>();
    recordsContainingNull.add("first");
    recordsContainingNull.add(null);
    assertThrows(
        NullPointerException.class,
        () -> new SourcePage<>(recordsContainingNull, Optional.empty(), Optional.empty()));
  }

  @Test
  void shouldClassifyRetryableFailuresAndRejectUnsanitizedDiagnosticCodes() {
    BusinessDataSourceFailure unavailable =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE, "source-unavailable");
    BusinessDataSourceFailure authentication =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED, "authentication-failed");

    assertTrue(unavailable.retryable());
    assertFalse(authentication.retryable());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BusinessDataSourceFailure(
                BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE,
                "raw source response details"));
  }

  @Test
  void shouldExposeOnlyTheSanitizedFailureThroughTheDataSourceException() {
    RuntimeException sourceCause = new RuntimeException("remote response containing a secret");
    BusinessDataSourceFailure failure =
        new BusinessDataSourceFailure(BusinessDataSourceFailure.Category.TIMEOUT, "source-timeout");

    BusinessDataSourceException exception = new BusinessDataSourceException(failure, sourceCause);

    assertEquals("source-timeout", exception.getMessage());
    assertEquals(failure, exception.failure());
    assertSame(sourceCause, exception.getCause());
  }

  @Test
  void shouldRepresentConnectionOutcomesWithClassifiedFailures() {
    BusinessDataSourceFailure failure =
        new BusinessDataSourceFailure(
            BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED, "authentication-failed");
    ConnectionTestResult connected = ConnectionTestResult.connected();
    ConnectionTestResult failed = ConnectionTestResult.failed(failure);

    assertEquals(ConnectionTestResult.Status.CONNECTED, connected.status());
    assertEquals(Optional.empty(), connected.failure());
    assertEquals(ConnectionTestResult.Status.FAILED, failed.status());
    assertEquals(Optional.of(failure), failed.failure());
    assertThrows(NullPointerException.class, () -> ConnectionTestResult.failed(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConnectionTestResult(ConnectionTestResult.Status.FAILED, Optional.empty()));
  }

  @Test
  void shouldReportSchemaCompatibilityFromImmutableIssues() {
    SourceSchemaVerificationResult compatible = SourceSchemaVerificationResult.verified();
    List<SourceSchemaIssue> mutableIssues =
        new ArrayList<>(List.of(SourceSchemaIssue.missingField(SourceEntity.INVOICE, "dueDate")));
    SourceSchemaVerificationResult incompatible =
        SourceSchemaVerificationResult.withIssues(mutableIssues);

    mutableIssues.clear();

    assertTrue(compatible.isCompatible());
    assertFalse(incompatible.isCompatible());
    assertEquals(1, incompatible.issues().size());
    assertEquals(Optional.of("dueDate"), incompatible.issues().getFirst().field());
    assertEquals(
        SourceSchemaIssue.Type.MISSING_REQUIRED_ENTITY,
        SourceSchemaIssue.missingEntity(SourceEntity.CUSTOMER).type());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceSchemaIssue(
                SourceEntity.CUSTOMER,
                Optional.of("customerNumber"),
                SourceSchemaIssue.Type.MISSING_REQUIRED_ENTITY));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceSchemaIssue(
                SourceEntity.CUSTOMER,
                Optional.empty(),
                SourceSchemaIssue.Type.MISSING_REQUIRED_FIELD));
    assertThrows(
        UnsupportedOperationException.class,
        () -> incompatible.issues().add(incompatible.issues().getFirst()));
  }

  @Test
  void shouldKeepNormalizedCustomerAndInvoiceRecordsSeparateFromOwnedAggregates() {
    SourceIdentity customerIdentity =
        SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, "customer-1000");
    SourceCustomerRecord customer =
        new SourceCustomerRecord(
            SourceRecordMetadata.withoutModificationTimestamp(
                customerIdentity, new SourceModificationVersion("customer-v1")),
            " C1000 ",
            " Acme Manufacturing ",
            Optional.of(" accounts@acme.example "));
    SourceInvoiceRecord invoice =
        new SourceInvoiceRecord(
            SourceRecordMetadata.withoutModificationTimestamp(
                SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1000"),
                new SourceModificationVersion("invoice-v2")),
            customerIdentity,
            " INV-1000 ",
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 2, 4),
            Currency.getInstance("USD"),
            new BigDecimal("1250.00"),
            new BigDecimal("275.00"),
            " OPEN ");

    assertEquals("C1000", customer.customerNumber());
    assertEquals(Optional.of("accounts@acme.example"), customer.emailAddress());
    assertEquals("INV-1000", invoice.invoiceNumber());
    assertEquals("OPEN", invoice.sourceStatus());
    assertEquals(new BigDecimal("275.00"), invoice.openAmount());
  }

  @Test
  void shouldRejectRecordsWithMismatchedSourceEntities() {
    SourceModificationVersion version = new SourceModificationVersion("v1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceCustomerRecord(
                SourceRecordMetadata.withoutModificationTimestamp(
                    SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1000"), version),
                "C1000",
                "Acme",
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceInvoiceRecord(
                SourceRecordMetadata.withoutModificationTimestamp(
                    SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "invoice-1000"), version),
                SourceIdentity.sourceRecordId(SourceEntity.INVOICE, "not-a-customer"),
                "INV-1000",
                LocalDate.of(2026, 2, 4),
                LocalDate.of(2026, 1, 5),
                Currency.getInstance("USD"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                "OPEN"));
  }
}
