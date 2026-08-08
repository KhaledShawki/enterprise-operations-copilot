package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryPage;

public interface PaymentQueryRepository {

  PaymentQueryPage findPage(PaymentQueryCriteria criteria);
}
