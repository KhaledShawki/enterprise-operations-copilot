package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.BusinessPartnerImportPage;

/** Downstream boundary for durably accepting a normalized customer page. */
public interface BusinessPartnerImportPort {

  BusinessPartnerImportOutcome importPage(BusinessPartnerImportPage page);
}
