package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka;

public final class ConnectorKafkaHeaders {

  public static final String FAILURE_CODE = "eoc-connector-failure-code";
  public static final String RETRYABLE = "eoc-connector-retryable";
  public static final String REPLAY_REQUEST_ID = "eoc-connector-replay-request-id";
  public static final String REPLAY_GENERATION = "eoc-connector-replay-generation";
  public static final String REPLAY_DLT_PARTITION = "eoc-connector-replay-dlt-partition";
  public static final String REPLAY_DLT_OFFSET = "eoc-connector-replay-dlt-offset";

  private ConnectorKafkaHeaders() {}
}
