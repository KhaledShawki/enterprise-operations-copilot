package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryPage;

public interface InvoiceQueryRepository {

  InvoiceQueryPage findPage(InvoiceQueryCriteria criteria);
}
