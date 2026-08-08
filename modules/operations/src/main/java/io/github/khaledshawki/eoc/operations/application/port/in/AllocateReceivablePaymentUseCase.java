package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;

public interface AllocateReceivablePaymentUseCase {

  ReceivableAllocationResult allocate(AllocateReceivablePaymentCommand command);
}
