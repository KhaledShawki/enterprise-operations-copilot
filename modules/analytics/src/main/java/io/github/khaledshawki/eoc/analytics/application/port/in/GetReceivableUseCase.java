package io.github.khaledshawki.eoc.analytics.application.port.in;

public interface GetReceivableUseCase {

  ReceivableResult get(GetReceivableQuery query);
}
