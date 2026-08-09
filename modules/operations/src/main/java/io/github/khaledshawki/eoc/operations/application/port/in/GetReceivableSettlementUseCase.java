package io.github.khaledshawki.eoc.operations.application.port.in;

@FunctionalInterface
public interface GetReceivableSettlementUseCase {

  ReceivableSettlementResult get(GetReceivableSettlementQuery query);
}
