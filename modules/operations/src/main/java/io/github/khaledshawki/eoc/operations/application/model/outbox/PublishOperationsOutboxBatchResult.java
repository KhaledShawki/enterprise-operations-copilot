package io.github.khaledshawki.eoc.operations.application.model.outbox;

public record PublishOperationsOutboxBatchResult(
    int claimed, int published, int retriesScheduled, int failed) {

  public PublishOperationsOutboxBatchResult {
    if (claimed < 0 || published < 0 || retriesScheduled < 0 || failed < 0) {
      throw new IllegalArgumentException("Operations outbox publication counts cannot be negative");
    }
    if (claimed != published + retriesScheduled + failed) {
      throw new IllegalArgumentException("Every claimed event must have one publication outcome");
    }
  }

  public static PublishOperationsOutboxBatchResult empty() {
    return new PublishOperationsOutboxBatchResult(0, 0, 0, 0);
  }
}
