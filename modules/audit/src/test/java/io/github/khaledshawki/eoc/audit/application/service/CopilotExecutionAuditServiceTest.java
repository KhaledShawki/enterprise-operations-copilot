package io.github.khaledshawki.eoc.audit.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.audit.application.exception.AuditUnavailableException;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.port.out.AppendCopilotExecutionAuditEventPort;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CopilotExecutionAuditServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-13T13:00:00Z");
  private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

  @Test
  void createsApplicationOwnedEventIdentityAndTimestamp() {
    List<CopilotExecutionAuditEvent> events = new ArrayList<>();
    var service =
        new CopilotExecutionAuditService(
            events::add, Clock.fixed(NOW, ZoneOffset.UTC), () -> EVENT_ID);

    service.recordStarted(EXECUTION_ID, context());

    var event = assertInstanceOf(CopilotExecutionAuditEvent.Started.class, events.getFirst());
    assertEquals(EVENT_ID, event.auditEventId());
    assertEquals(EXECUTION_ID, event.executionId());
    assertEquals(NOW, event.occurredAt());
    assertEquals(LocalDate.parse("2026-08-13"), event.context().businessDate().orElseThrow());
  }

  @Test
  void translatesPersistenceFailureToStableAuditFailure() {
    AppendCopilotExecutionAuditEventPort failingPort =
        event -> {
          throw new IllegalStateException("db");
        };
    var service =
        new CopilotExecutionAuditService(
            failingPort, Clock.fixed(NOW, ZoneOffset.UTC), () -> EVENT_ID);

    AuditUnavailableException exception =
        assertThrows(
            AuditUnavailableException.class, () -> service.recordStarted(EXECUTION_ID, context()));

    assertEquals("Copilot execution audit is unavailable", exception.getMessage());
    assertInstanceOf(IllegalStateException.class, exception.getCause());
  }

  private static Context context() {
    return new Context(
        URI.create("https://issuer.example"),
        "subject-1",
        UUID.fromString("00000000-0000-0000-0000-000000000103"),
        Optional.of(LocalDate.parse("2026-08-13")),
        "a".repeat(64),
        21);
  }
}
