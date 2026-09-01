package io.github.khaledshawki.eoc.platform.observability.metrics;

public enum EventPipeline {
  CONNECTOR("connector"),
  OPERATIONS("operations"),
  ANALYTICS("analytics");

  private final String tagValue;

  EventPipeline(String tagValue) {
    this.tagValue = tagValue;
  }

  String tagValue() {
    return tagValue;
  }
}
