package io.github.khaledshawki.eoc.analytics.application.port.in;

public interface ListReceivablesUseCase {

  ReceivablePageResult list(ListReceivablesQuery query);
}
