package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

public record ConnectorDeadLetterPartition(int partition, long beginningOffset, long endOffset) {

  public ConnectorDeadLetterPartition {
    if (partition < 0) {
      throw new IllegalArgumentException("Dead-letter partition cannot be negative");
    }
    if (beginningOffset < 0 || endOffset < beginningOffset) {
      throw new IllegalArgumentException("Dead-letter partition offsets are invalid");
    }
  }

  public long recordCount() {
    return endOffset - beginningOffset;
  }
}
