package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class MockErpBusinessDataSourceAdapter implements BusinessDataSource {

  static final ConnectorType CONNECTOR_TYPE = ConnectorType.of("mock-erp");

  private final MockErpRpcClient rpcClient;

  MockErpBusinessDataSourceAdapter(MockErpRpcClient rpcClient) {
    this.rpcClient = Objects.requireNonNull(rpcClient, "Mock ERP RPC client cannot be null");
  }

  @Override
  public ConnectorType supportedConnectorType() {
    return CONNECTOR_TYPE;
  }

  @Override
  public ConnectionTestResult testConnection(BusinessDataSourceConfiguration configuration) {
    requireSupportedConfiguration(configuration);
    try {
      rpcClient.checkConnection(configuration.endpoint());
      return ConnectionTestResult.connected();
    } catch (MockErpRpcException exception) {
      return ConnectionTestResult.failed(toFailure(exception));
    }
  }

  @Override
  public SourceSchemaVerificationResult verifySourceSchema(
      BusinessDataSourceConfiguration configuration) {
    requireSupportedConfiguration(configuration);
    try {
      return rpcClient.verifySchema(configuration.endpoint());
    } catch (MockErpRpcException exception) {
      throw new BusinessDataSourceException(toFailure(exception), exception);
    }
  }

  @Override
  public SourcePage<SourceCustomerRecord> retrieveCustomers(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    Objects.requireNonNull(fetchRequest, "Source fetch request cannot be null");
    try {
      return rpcClient.retrieveCustomers(configuration.endpoint(), fetchRequest);
    } catch (MockErpRpcException exception) {
      throw new BusinessDataSourceException(toFailure(exception), exception);
    }
  }

  @Override
  public SourcePage<SourceInvoiceRecord> retrieveInvoices(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    Objects.requireNonNull(fetchRequest, "Source fetch request cannot be null");
    try {
      return rpcClient.retrieveInvoices(configuration.endpoint(), fetchRequest);
    } catch (MockErpRpcException exception) {
      throw new BusinessDataSourceException(toFailure(exception), exception);
    }
  }

  @Override
  public SourcePage<SourcePaymentRecord> retrievePayments(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest) {
    requireSupportedConfiguration(configuration);
    Objects.requireNonNull(fetchRequest, "Source fetch request cannot be null");
    try {
      return rpcClient.retrievePayments(configuration.endpoint(), fetchRequest);
    } catch (MockErpRpcException exception) {
      throw new BusinessDataSourceException(toFailure(exception), exception);
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

  private static BusinessDataSourceFailure toFailure(MockErpRpcException exception) {
    BusinessDataSourceFailure.Category category =
        switch (exception.category()) {
          case AUTHENTICATION_FAILED -> BusinessDataSourceFailure.Category.AUTHENTICATION_FAILED;
          case AUTHORIZATION_FAILED -> BusinessDataSourceFailure.Category.AUTHORIZATION_FAILED;
          case SOURCE_UNAVAILABLE -> BusinessDataSourceFailure.Category.SOURCE_UNAVAILABLE;
          case TIMEOUT -> BusinessDataSourceFailure.Category.TIMEOUT;
          case RATE_LIMITED -> BusinessDataSourceFailure.Category.RATE_LIMITED;
          case INVALID_POSITION -> BusinessDataSourceFailure.Category.INVALID_POSITION;
          case SOURCE_CONTRACT_VIOLATION ->
              BusinessDataSourceFailure.Category.SOURCE_CONTRACT_VIOLATION;
          case UNEXPECTED_SOURCE_FAILURE ->
              BusinessDataSourceFailure.Category.UNEXPECTED_SOURCE_FAILURE;
        };
    return new BusinessDataSourceFailure(category, exception.diagnosticCode());
  }
}
