package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockErpBusinessDataSourceAdapterTest {

  private static final BusinessDataSourceConfiguration CONFIGURATION =
      new BusinessDataSourceConfiguration(
          ConnectorId.of(UUID.fromString("1409f22e-a183-4d87-87c8-3edcfda0b640")),
          ConnectorTenantId.of(UUID.fromString("c92cc19e-35dc-45f9-802b-4362ecce5970")),
          ConnectorType.of("mock-erp"),
          ConnectorEndpoint.of("https://mock-erp.example/api"),
          CredentialReference.of(UUID.fromString("5afda653-cea6-43a9-b84e-60bd6f2611a6")));

  @Test
  void shouldExposeAConnectedAndSchemaCompatibleHealthySource() {
    MockErpBusinessDataSourceAdapter adapter = healthyAdapter();

    assertEquals(ConnectorType.of("mock-erp"), adapter.supportedConnectorType());
    assertEquals(ConnectionTestResult.connected(), adapter.testConnection(CONFIGURATION));
    assertTrue(adapter.verifySourceSchema(CONFIGURATION).isCompatible());
  }

  @Test
  void shouldRetrieveCustomersDeterministicallyAcrossPageTokens() {
    MockErpBusinessDataSourceAdapter adapter = healthyAdapter();
    SourcePage<SourceCustomerRecord> firstPage =
        adapter.retrieveCustomers(CONFIGURATION, SourceFetchRequest.initial(2));

    assertEquals(List.of("C1000", "C2000"), customerNumbers(firstPage));
    assertEquals("mock-erp|customer|0|2", firstPage.nextPageToken().orElseThrow().value());
    assertEquals("mock-erp|customer|2", firstPage.candidateCursor().orElseThrow().value());

    SourcePage<SourceCustomerRecord> secondPage =
        adapter.retrieveCustomers(
            CONFIGURATION,
            SourceFetchRequest.continueWith(2, firstPage.nextPageToken().orElseThrow()));

    assertEquals(List.of("C3000"), customerNumbers(secondPage));
    assertTrue(secondPage.nextPageToken().isEmpty());
    assertEquals("mock-erp|customer|3", secondPage.candidateCursor().orElseThrow().value());
  }

  @Test
  void shouldResumeCustomerRetrievalAfterAnIncrementalCursorWithoutDuplicates() {
    SourcePage<SourceCustomerRecord> page =
        healthyAdapter()
            .retrieveCustomers(
                CONFIGURATION,
                SourceFetchRequest.after(10, new IncrementalCursor("mock-erp|customer|1")));

    assertEquals(List.of("C2000", "C3000"), customerNumbers(page));
    assertEquals("mock-erp|customer|3", page.candidateCursor().orElseThrow().value());
  }

  @Test
  void shouldPreserveTheCursorWhenNoNewCustomerRecordsExist() {
    IncrementalCursor currentCursor = new IncrementalCursor("mock-erp|customer|3");

    SourcePage<SourceCustomerRecord> page =
        healthyAdapter()
            .retrieveCustomers(CONFIGURATION, SourceFetchRequest.after(10, currentCursor));

    assertTrue(page.records().isEmpty());
    assertEquals(currentCursor, page.candidateCursor().orElseThrow());
  }

  @Test
  void shouldRetrieveNormalizedInvoicesWithStableSourceEvidence() {
    var page = healthyAdapter().retrieveInvoices(CONFIGURATION, SourceFetchRequest.initial(10));

    assertEquals(3, page.records().size());
    assertEquals("invoice-1000", page.records().getFirst().metadata().identity().value());
    assertEquals("customer-1000", page.records().getFirst().customerIdentity().value());
    assertEquals(
        Instant.parse("2026-01-05T11:00:00Z"),
        page.records().getFirst().metadata().sourceModifiedAt().orElseThrow());
    assertEquals(new BigDecimal("1250.00"), page.records().getFirst().totalAmount());
    assertEquals(new BigDecimal("1250.00"), page.records().getFirst().openAmount());
    assertFalse(page.nextPageToken().isPresent());
    assertEquals("mock-erp|invoice|3", page.candidateCursor().orElseThrow().value());
  }

  @Test
  void shouldReportAnIncompatibleSchemaAndRejectRetrieval() {
    MockErpBusinessDataSourceAdapter adapter = adapterFor(MockErpScenario.INCOMPATIBLE_SCHEMA);

    assertFalse(adapter.verifySourceSchema(CONFIGURATION).isCompatible());
    BusinessDataSourceException exception =
        assertThrows(
            BusinessDataSourceException.class,
            () -> adapter.retrieveInvoices(CONFIGURATION, SourceFetchRequest.initial(10)));

    assertEquals(
        BusinessDataSourceFailure.Category.SOURCE_CONTRACT_VIOLATION,
        exception.failure().category());
    assertFalse(exception.failure().retryable());
  }

  @Test
  void shouldClassifyAuthenticationFailuresAsPermanent() {
    MockErpBusinessDataSourceAdapter adapter = adapterFor(MockErpScenario.AUTHENTICATION_FAILED);

    ConnectionTestResult result = adapter.testConnection(CONFIGURATION);
    BusinessDataSourceException exception =
        assertThrows(
            BusinessDataSourceException.class, () -> adapter.verifySourceSchema(CONFIGURATION));

    assertEquals(ConnectionTestResult.Status.FAILED, result.status());
    assertEquals(
        BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED,
        result.failure().orElseThrow().category());
    assertFalse(result.failure().orElseThrow().retryable());
    assertEquals(result.failure().orElseThrow(), exception.failure());
  }

  @Test
  void shouldClassifyUnavailableSourcesAsRetryable() {
    MockErpBusinessDataSourceAdapter adapter = adapterFor(MockErpScenario.SOURCE_UNAVAILABLE);

    ConnectionTestResult result = adapter.testConnection(CONFIGURATION);
    BusinessDataSourceException exception =
        assertThrows(
            BusinessDataSourceException.class,
            () -> adapter.retrieveCustomers(CONFIGURATION, SourceFetchRequest.initial(10)));

    assertEquals(ConnectionTestResult.Status.FAILED, result.status());
    assertEquals(
        BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE, exception.failure().category());
    assertTrue(result.failure().orElseThrow().retryable());
    assertTrue(exception.failure().retryable());
  }

  @Test
  void shouldFailRetrievalDeterministicallyAfterSuccessfulVerification() {
    MockErpBusinessDataSourceAdapter adapter = adapterFor(MockErpScenario.RETRIEVAL_FAILURE);

    assertEquals(ConnectionTestResult.connected(), adapter.testConnection(CONFIGURATION));
    assertTrue(adapter.verifySourceSchema(CONFIGURATION).isCompatible());
    BusinessDataSourceException exception =
        assertThrows(
            BusinessDataSourceException.class,
            () -> adapter.retrieveCustomers(CONFIGURATION, SourceFetchRequest.initial(10)));

    assertEquals("mock-retrieval-failed", exception.failure().diagnosticCode());
    assertTrue(exception.failure().retryable());
  }

  @Test
  void shouldRejectPositionsCreatedForAnotherEntityWithoutExposingTheirValues() {
    BusinessDataSourceException cursorException =
        assertThrows(
            BusinessDataSourceException.class,
            () ->
                healthyAdapter()
                    .retrieveCustomers(
                        CONFIGURATION,
                        SourceFetchRequest.after(10, new IncrementalCursor("mock-erp|invoice|1"))));
    BusinessDataSourceException tokenException =
        assertThrows(
            BusinessDataSourceException.class,
            () ->
                healthyAdapter()
                    .retrieveInvoices(
                        CONFIGURATION,
                        SourceFetchRequest.continueWith(
                            10, new SourcePageToken("mock-erp|customer|0|1"))));

    assertEquals(
        BusinessDataSourceFailure.Category.INVALID_POSITION, cursorException.failure().category());
    assertEquals("mock-invalid-incremental-cursor", cursorException.getMessage());
    assertEquals(
        BusinessDataSourceFailure.Category.INVALID_POSITION, tokenException.failure().category());
    assertEquals("mock-invalid-page-token", tokenException.getMessage());
  }

  @Test
  void shouldRejectUnsupportedConnectorTypesBeforeAccessingTheSource() {
    BusinessDataSourceConfiguration unsupportedConfiguration =
        new BusinessDataSourceConfiguration(
            CONFIGURATION.connectorId(),
            CONFIGURATION.tenantId(),
            ConnectorType.of("sap-b1"),
            CONFIGURATION.endpoint(),
            CONFIGURATION.credentialReference());

    assertThrows(
        IllegalArgumentException.class,
        () -> healthyAdapter().testConnection(unsupportedConfiguration));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            healthyAdapter()
                .retrieveCustomers(unsupportedConfiguration, SourceFetchRequest.initial(10)));
  }

  private static MockErpBusinessDataSourceAdapter healthyAdapter() {
    return adapterFor(MockErpScenario.HEALTHY);
  }

  private static MockErpBusinessDataSourceAdapter adapterFor(MockErpScenario scenario) {
    return new MockErpBusinessDataSourceAdapter(MockErpFixture.standard(), scenario);
  }

  private static List<String> customerNumbers(SourcePage<SourceCustomerRecord> page) {
    return page.records().stream().map(SourceCustomerRecord::customerNumber).toList();
  }
}
