package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockErpBusinessDataSourceAdapterTest {

  private static final BusinessDataSourceConfiguration CONFIGURATION =
      new BusinessDataSourceConfiguration(
          ConnectorId.of(UUID.fromString("1409f22e-a183-4d87-87c8-3edcfda0b640")),
          ConnectorTenantId.of(UUID.fromString("c92cc19e-35dc-45f9-802b-4362ecce5970")),
          ConnectorType.of("mock-erp"),
          ConnectorEndpoint.of("grpc://mock-erp:9090"),
          CredentialReference.of(UUID.fromString("5afda653-cea6-43a9-b84e-60bd6f2611a6")));

  @Test
  void shouldDelegateHealthyConnectionSchemaAndRetrievalToGrpcClient() {
    MockErpRpcClient client = mock(MockErpRpcClient.class);
    doNothing().when(client).checkConnection(CONFIGURATION.endpoint());
    when(client.verifySchema(CONFIGURATION.endpoint()))
        .thenReturn(SourceSchemaVerificationResult.verified());
    when(client.retrieveCustomers(CONFIGURATION.endpoint(), SourceFetchRequest.initial(10)))
        .thenReturn(
            new SourcePage<>(List.of(), java.util.Optional.empty(), java.util.Optional.empty()));
    MockErpBusinessDataSourceAdapter adapter = new MockErpBusinessDataSourceAdapter(client);

    assertEquals(ConnectionTestResult.connected(), adapter.testConnection(CONFIGURATION));
    assertTrue(adapter.verifySourceSchema(CONFIGURATION).isCompatible());
    assertTrue(
        adapter
            .retrieveCustomers(CONFIGURATION, SourceFetchRequest.initial(10))
            .records()
            .isEmpty());
  }

  @Test
  void shouldMapRetryableGrpcAvailabilityFailureWithoutLeakingTransportDetail() {
    MockErpRpcClient client = mock(MockErpRpcClient.class);
    doThrow(
            new MockErpRpcException(
                MockErpRpcException.Category.SOURCE_UNAVAILABLE,
                "mock-grpc-source-unavailable",
                new RuntimeException("transport detail")))
        .when(client)
        .checkConnection(CONFIGURATION.endpoint());
    MockErpBusinessDataSourceAdapter adapter = new MockErpBusinessDataSourceAdapter(client);

    ConnectionTestResult result = adapter.testConnection(CONFIGURATION);

    assertEquals(ConnectionTestResult.Status.FAILED, result.status());
    assertEquals(
        BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE,
        result.failure().orElseThrow().category());
    assertEquals("mock-grpc-source-unavailable", result.failure().orElseThrow().diagnosticCode());
    assertTrue(result.failure().orElseThrow().retryable());
  }

  @Test
  void shouldMapInvalidSourcePositionToPermanentApplicationFailure() {
    MockErpRpcClient client = mock(MockErpRpcClient.class);
    when(client.retrieveInvoices(CONFIGURATION.endpoint(), SourceFetchRequest.initial(10)))
        .thenThrow(
            new MockErpRpcException(
                MockErpRpcException.Category.INVALID_POSITION, "mock-grpc-invalid-position", null));
    MockErpBusinessDataSourceAdapter adapter = new MockErpBusinessDataSourceAdapter(client);

    BusinessDataSourceException exception =
        assertThrows(
            BusinessDataSourceException.class,
            () -> adapter.retrieveInvoices(CONFIGURATION, SourceFetchRequest.initial(10)));

    assertEquals(
        BusinessDataSourceFailure.Category.INVALID_POSITION, exception.failure().category());
    assertEquals("mock-grpc-invalid-position", exception.failure().diagnosticCode());
  }
}
