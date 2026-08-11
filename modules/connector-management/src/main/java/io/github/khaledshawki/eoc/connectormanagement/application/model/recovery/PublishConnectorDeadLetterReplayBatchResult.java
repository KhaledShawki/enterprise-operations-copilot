package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

public record PublishConnectorDeadLetterReplayBatchResult(
    int claimed, int replayed, int retriesScheduled, int failed) {

  public PublishConnectorDeadLetterReplayBatchResult {
    if (claimed < 0
        || replayed < 0
        || retriesScheduled < 0
        || failed < 0
        || replayed + retriesScheduled + failed != claimed) {
      throw new IllegalArgumentException("Replay batch result counts are invalid");
    }
  }

  public static PublishConnectorDeadLetterReplayBatchResult empty() {
    return new PublishConnectorDeadLetterReplayBatchResult(0, 0, 0, 0);
  }
}
