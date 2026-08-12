package io.github.khaledshawki.eoc.analytics.application.port.in;

public interface GetReceivablesSummaryUseCase {

  ReceivablesSummaryResult get(GetReceivablesSummaryQuery query);
}
