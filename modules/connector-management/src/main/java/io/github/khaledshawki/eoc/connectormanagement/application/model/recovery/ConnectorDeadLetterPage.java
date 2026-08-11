package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.util.List;

public record ConnectorDeadLetterPage(
    int partition,
    long fromOffset,
    long nextOffset,
    long endOffset,
    List<ConnectorDeadLetterRecord> records) {

  public ConnectorDeadLetterPage {
    if (partition < 0 || fromOffset < 0 || nextOffset < fromOffset || endOffset < nextOffset) {
      throw new IllegalArgumentException("Dead-letter page offsets are invalid");
    }
    records = List.copyOf(records);
    for (ConnectorDeadLetterRecord record : records) {
      if (record.reference().partition() != partition
          || record.reference().offset() < fromOffset
          || record.reference().offset() >= nextOffset) {
        throw new IllegalArgumentException("Dead-letter page contains an out-of-range record");
      }
    }
  }

  public boolean hasMore() {
    return nextOffset < endOffset;
  }
}
