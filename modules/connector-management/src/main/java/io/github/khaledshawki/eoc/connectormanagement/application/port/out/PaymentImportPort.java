package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.PaymentImportPage;

/** Downstream boundary for durably accepting a normalized payment page. */
public interface PaymentImportPort {

  PaymentImportOutcome importPage(PaymentImportPage page);
}
