package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

public record ConnectorDeadLetterReference(int partition, long offset) {

  public ConnectorDeadLetterReference {
    if (partition < 0) {
      throw new IllegalArgumentException("Dead-letter partition cannot be negative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Dead-letter offset cannot be negative");
    }
  }
}
