package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;

interface MockErpRpcClient {

  void checkConnection(ConnectorEndpoint endpoint);

  SourceSchemaVerificationResult verifySchema(ConnectorEndpoint endpoint);

  SourcePage<SourceCustomerRecord> retrieveCustomers(
      ConnectorEndpoint endpoint, SourceFetchRequest request);

  SourcePage<SourceInvoiceRecord> retrieveInvoices(
      ConnectorEndpoint endpoint, SourceFetchRequest request);

  SourcePage<SourcePaymentRecord> retrievePayments(
      ConnectorEndpoint endpoint, SourceFetchRequest request);
}
