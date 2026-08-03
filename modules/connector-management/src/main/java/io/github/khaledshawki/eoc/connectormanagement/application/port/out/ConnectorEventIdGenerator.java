package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import java.util.UUID;

@FunctionalInterface
public interface ConnectorEventIdGenerator {
  UUID generate();
}
