package io.github.khaledshawki.eoc.operations.application.model.outbox;

public enum OperationsOutboxStatus {
  PENDING,
  CLAIMED,
  RETRY_SCHEDULED,
  PUBLISHED,
  FAILED
}
