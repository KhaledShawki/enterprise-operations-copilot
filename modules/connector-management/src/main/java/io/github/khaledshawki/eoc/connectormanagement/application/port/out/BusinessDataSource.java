package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.BusinessDataSourceConfiguration;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.ConnectionTestResult;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;

/**
 * Application-owned boundary for communicating with one type of external business data source.
 * Implementations translate source-specific representations into normalized connector contracts.
 */
public interface BusinessDataSource {

  /** Returns the connector type handled by this adapter. */
  ConnectorType supportedConnectorType();

  /**
   * Tests whether the configured source can be reached and authenticated. Expected connection
   * failures are returned as sanitized results rather than exposed as client-specific exceptions.
   */
  ConnectionTestResult testConnection(BusinessDataSourceConfiguration configuration);

  /**
   * Verifies that required source entities and fields are compatible with this adapter. Transport
   * failures are reported through a {@code BusinessDataSourceException}.
   */
  SourceSchemaVerificationResult verifySourceSchema(BusinessDataSourceConfiguration configuration);

  /** Retrieves one normalized customer page from an initial, incremental, or continuation scan. */
  SourcePage<SourceCustomerRecord> retrieveCustomers(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest);

  /** Retrieves one normalized invoice page from an initial, incremental, or continuation scan. */
  SourcePage<SourceInvoiceRecord> retrieveInvoices(
      BusinessDataSourceConfiguration configuration, SourceFetchRequest fetchRequest);
}
