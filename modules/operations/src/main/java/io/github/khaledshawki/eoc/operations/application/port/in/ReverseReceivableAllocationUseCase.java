package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;

public interface ReverseReceivableAllocationUseCase {

  ReceivableAllocationResult reverse(ReverseReceivableAllocationCommand command);
}
