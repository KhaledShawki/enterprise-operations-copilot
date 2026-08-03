package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

public record PublishConnectorOutboxBatchResult(
    int claimed, int published, int retriesScheduled, int failed) {

  public PublishConnectorOutboxBatchResult {
    if (claimed < 0 || published < 0 || retriesScheduled < 0 || failed < 0) {
      throw new IllegalArgumentException("Outbox publication counts cannot be negative");
    }
    if (claimed != published + retriesScheduled + failed) {
      throw new IllegalArgumentException("Every claimed event must have one publication outcome");
    }
  }

  public static PublishConnectorOutboxBatchResult empty() {
    return new PublishConnectorOutboxBatchResult(0, 0, 0, 0);
  }
}
