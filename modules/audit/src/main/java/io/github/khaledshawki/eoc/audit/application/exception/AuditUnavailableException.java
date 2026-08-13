package io.github.khaledshawki.eoc.audit.application.exception;

public final class AuditUnavailableException extends RuntimeException {
  public AuditUnavailableException(Throwable cause) {
    super("Copilot execution audit is unavailable", cause);
  }
}
