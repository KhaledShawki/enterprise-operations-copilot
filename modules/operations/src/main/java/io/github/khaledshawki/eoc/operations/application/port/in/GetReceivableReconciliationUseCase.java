package io.github.khaledshawki.eoc.operations.application.port.in;

public interface GetReceivableReconciliationUseCase {

  ReceivableReconciliationResult get(GetReceivableReconciliationQuery query);
}
