package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface ListConnectorsUseCase {

  ListConnectorsResult list(ListConnectorsQuery query);
}
