package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
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

class MockErpPaymentDataSourceAdapterTest {

  private static final BusinessDataSourceConfiguration CONFIGURATION =
      new BusinessDataSourceConfiguration(
          ConnectorId.of(UUID.fromString("1409f22e-a183-4d87-87c8-3edcfda0b640")),
          ConnectorTenantId.of(UUID.fromString("c92cc19e-35dc-45f9-802b-4362ecce5970")),
          ConnectorType.of("mock-erp"),
          ConnectorEndpoint.of("https://mock-erp.example/api"),
          CredentialReference.of(UUID.fromString("5afda653-cea6-43a9-b84e-60bd6f2611a6")));

  @Test
  void shouldRetrieveNormalizedPaymentsWithStableSourceEvidence() {
    var page = healthyAdapter().retrievePayments(CONFIGURATION, SourceFetchRequest.initial(10));

    assertEquals(3, page.records().size());
    SourcePaymentRecord first = page.records().getFirst();
    assertEquals("payment-1000", first.metadata().identity().value());
    assertEquals("customer-1000", first.customerIdentity().value());
    assertEquals(
        Instant.parse("2026-01-18T09:00:00Z"), first.metadata().sourceModifiedAt().orElseThrow());
    assertEquals(new BigDecimal("400.00"), first.amount());
    assertFalse(first.reversed());
    assertTrue(page.records().getLast().reversed());
    assertTrue(page.nextPageToken().isEmpty());
    assertEquals("mock-erp|payment|3", page.candidateCursor().orElseThrow().value());
  }

  @Test
  void shouldResumePaymentRetrievalAfterIncrementalCursorWithoutDuplicates() {
    var page =
        healthyAdapter()
            .retrievePayments(
                CONFIGURATION,
                SourceFetchRequest.after(10, new IncrementalCursor("mock-erp|payment|1")));

    assertEquals(List.of("payment-2000", "payment-2001"), sourceIds(page.records()));
    assertEquals("mock-erp|payment|3", page.candidateCursor().orElseThrow().value());
  }

  private static MockErpBusinessDataSourceAdapter healthyAdapter() {
    return new MockErpBusinessDataSourceAdapter(MockErpFixture.standard(), MockErpScenario.HEALTHY);
  }

  private static List<String> sourceIds(List<SourcePaymentRecord> records) {
    return records.stream().map(record -> record.metadata().identity().value()).toList();
  }
}
