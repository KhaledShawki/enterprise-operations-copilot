package io.github.khaledshawki.eoc.platform.integration.copilot.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelToolCall;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotModelPort;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "eoc.connector-outbox.relay-enabled=false",
      "eoc.operations-outbox.relay-enabled=false",
      "eoc.analytics-events.transport=disabled",
      "eoc.copilot.llm.enabled=true",
      "spring.ai.model.chat=none"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CopilotQuestionRuntimeIT {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000681");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000682");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000683");
  private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000684");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000685");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000686");
  private static final UUID CUSTOMER_EVENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000687");
  private static final UUID INVOICE_EVENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000688");

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "copilot-runtime-user";
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 13);
  private static final Instant CUSTOMER_OCCURRED_AT = Instant.parse("2026-08-12T14:00:00Z");
  private static final Instant INVOICE_OCCURRED_AT = Instant.parse("2026-08-12T15:00:00Z");
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/copilot/questions";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private CopilotModelPort copilotModelPort;

  @BeforeEach
  void seedRuntimeState() {
    clearRuntimeState();
    seedTenantAccess();
    seedReceivableProjection();
  }

  @Test
  void executesAuthenticatedHttpThroughRealToolsAndPersistsGroundedSuccessAudit() throws Exception {
    when(copilotModelPort.generate(any()))
        .thenReturn(
            new CopilotModelResponse.ToolCalls(
                List.of(
                    new CopilotModelToolCall.GetReceivable(
                        "call-1", GetReceivableToolRequest.current(INVOICE_ID)))),
            new CopilotModelResponse.Answer(List.of("call-1")));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "Which receivable needs follow-up?",
                      "businessDate": "2026-08-13"
                    }
                    """)
                .with(jwt().jwt(token -> token.issuer(ISSUER).subject(SUBJECT))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.answer")
                .value(
                    "Invoice INV-100 for Acme AG (C-100): status PARTIALLY_PAID; "
                        + "outstanding CHF 80.00 of CHF 100.00; paid CHF 20.00; "
                        + "issued 2026-07-01; due 2026-08-01; overdue as of 2026-08-13."))
        .andExpect(jsonPath("$.grounding[0].toolCallId").value("call-1"))
        .andExpect(jsonPath("$.grounding[0].toolName").value("get_receivable"))
        .andExpect(
            jsonPath("$.grounding[0].sourceEvidence[0].eventId").value(INVOICE_EVENT_ID.toString()))
        .andExpect(jsonPath("$.grounding[0].sourceEvidence[0].aggregateVersion").value(4))
        .andExpect(
            jsonPath("$.grounding[0].sourceEvidence[0].occurredAt").value("2026-08-12T15:00:00Z"));

    verify(copilotModelPort, times(2)).generate(any());

    UUID executionId =
        jdbcTemplate.queryForObject(
            """
            SELECT execution_id
            FROM copilot_execution_audit_events
            WHERE tenant_id = ? AND subject = ? AND event_type = 'STARTED'
            """,
            UUID.class,
            TENANT_ID,
            SUBJECT);

    assertEquals(1, auditEventCount(executionId, "STARTED"));
    assertEquals(1, auditEventCount(executionId, "SUCCEEDED"));
    assertEquals(
        BUSINESS_DATE,
        jdbcTemplate.queryForObject(
            """
            SELECT business_date
            FROM copilot_execution_audit_events
            WHERE execution_id = ? AND event_type = 'SUCCEEDED'
            """,
            LocalDate.class,
            executionId));

    UUID succeededAuditEventId =
        jdbcTemplate.queryForObject(
            """
            SELECT audit_event_id
            FROM copilot_execution_audit_events
            WHERE execution_id = ? AND event_type = 'SUCCEEDED'
            """,
            UUID.class,
            executionId);

    assertEquals(
        "get_receivable",
        jdbcTemplate.queryForObject(
            """
            SELECT tool_name
            FROM copilot_execution_audit_groundings
            WHERE audit_event_id = ? AND grounding_index = 0
            """,
            String.class,
            succeededAuditEventId));
    assertEquals(
        INVOICE_EVENT_ID,
        jdbcTemplate.queryForObject(
            """
            SELECT source_event_id
            FROM copilot_execution_audit_evidence
            WHERE audit_event_id = ? AND grounding_index = 0 AND evidence_index = 0
            """,
            UUID.class,
            succeededAuditEventId));
    assertEquals(
        4L,
        jdbcTemplate.queryForObject(
            """
            SELECT aggregate_version
            FROM copilot_execution_audit_evidence
            WHERE audit_event_id = ? AND grounding_index = 0 AND evidence_index = 0
            """,
            Long.class,
            succeededAuditEventId));
  }

  @Test
  void deniesCrossTenantHttpBeforeModelExecutionOrAudit() throws Exception {
    String otherTenantEndpoint = "/api/v1/tenants/" + OTHER_TENANT_ID + "/copilot/questions";

    mockMvc
        .perform(
            post(otherTenantEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is overdue?\"}")
                .with(jwt().jwt(token -> token.issuer(ISSUER).subject(SUBJECT))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(copilotModelPort);
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM copilot_execution_audit_events", Integer.class));
  }

  @Test
  void mapsModelOutageAndPersistsStableFailureAuditWithoutProviderDetail() throws Exception {
    when(copilotModelPort.generate(any()))
        .thenThrow(
            new CopilotModelUnavailableException(
                new IllegalStateException("secret-provider-runtime-detail")));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "Which receivable needs follow-up?",
                      "businessDate": "2026-08-13"
                    }
                    """)
                .with(jwt().jwt(token -> token.issuer(ISSUER).subject(SUBJECT))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COPILOT_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").value("Copilot is temporarily unavailable."));

    verify(copilotModelPort).generate(any());

    UUID executionId =
        jdbcTemplate.queryForObject(
            """
            SELECT execution_id
            FROM copilot_execution_audit_events
            WHERE tenant_id = ? AND subject = ? AND event_type = 'STARTED'
            """,
            UUID.class,
            TENANT_ID,
            SUBJECT);

    assertEquals(1, auditEventCount(executionId, "STARTED"));
    assertEquals(1, auditEventCount(executionId, "FAILED"));
    assertEquals(
        "MODEL_UNAVAILABLE",
        jdbcTemplate.queryForObject(
            """
            SELECT failure_code
            FROM copilot_execution_audit_events
            WHERE execution_id = ? AND event_type = 'FAILED'
            """,
            String.class,
            executionId));
  }

  private int auditEventCount(UUID executionId, String eventType) {
    return jdbcTemplate.queryForObject(
        """
        SELECT count(*)
        FROM copilot_execution_audit_events
        WHERE execution_id = ? AND event_type = ?
        """,
        Integer.class,
        executionId,
        eventType);
  }

  private void clearRuntimeState() {
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_evidence");
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_groundings");
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_events");

    jdbcTemplate.update(
        "DELETE FROM analytics_invoice_receivable_projections WHERE tenant_id IN (?, ?)",
        TENANT_ID,
        OTHER_TENANT_ID);
    jdbcTemplate.update(
        "DELETE FROM analytics_business_partner_projections WHERE tenant_id IN (?, ?)",
        TENANT_ID,
        OTHER_TENANT_ID);
    jdbcTemplate.update(
        "DELETE FROM analytics_inbox_events WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);

    jdbcTemplate.update(
        "DELETE FROM tenant_membership_roles WHERE tenant_membership_id = ?", MEMBERSHIP_ID);
    jdbcTemplate.update("DELETE FROM tenant_memberships WHERE id = ?", MEMBERSHIP_ID);
    jdbcTemplate.update("DELETE FROM platform_users WHERE id = ?", USER_ID);
    jdbcTemplate.update("DELETE FROM tenants WHERE id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
  }

  private void seedTenantAccess() {
    jdbcTemplate.update(
        """
        INSERT INTO tenants
          (id, tenant_key, display_name, status, version, created_at, updated_at)
        VALUES (?, 'copilot-runtime', 'Copilot Runtime', 'ACTIVE', 0, now(), now())
        """,
        TENANT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO tenants
          (id, tenant_key, display_name, status, version, created_at, updated_at)
        VALUES (?, 'copilot-runtime-other', 'Copilot Runtime Other', 'ACTIVE', 0, now(), now())
        """,
        OTHER_TENANT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO platform_users
          (id, issuer, subject, status, version, created_at, updated_at)
        VALUES (?, ?, ?, 'ACTIVE', 0, now(), now())
        """,
        USER_ID,
        ISSUER,
        SUBJECT);
    jdbcTemplate.update(
        """
        INSERT INTO tenant_memberships
          (id, tenant_id, platform_user_id, status, version, created_at, updated_at)
        VALUES (?, ?, ?, 'ACTIVE', 0, now(), now())
        """,
        MEMBERSHIP_ID,
        TENANT_ID,
        USER_ID);
    jdbcTemplate.update(
        """
        INSERT INTO tenant_membership_roles (tenant_membership_id, role_key)
        VALUES (?, 'auditor')
        """,
        MEMBERSHIP_ID);
  }

  private void seedReceivableProjection() {
    jdbcTemplate.update(
        """
        INSERT INTO analytics_inbox_events
          (event_id, event_type, schema_version, tenant_id, aggregate_type, aggregate_id,
           aggregate_version, payload, content_fingerprint, projection_status, occurred_at,
           received_at, processed_at)
        VALUES (
          ?, 'operations.business-partner.synchronized.v1', 1, ?, 'BUSINESS_PARTNER', ?, 1,
          CAST('{}' AS jsonb), ?, 'APPLIED', ?, ?, ?
        )
        """,
        CUSTOMER_EVENT_ID,
        TENANT_ID,
        CUSTOMER_ID,
        "a".repeat(64),
        Timestamp.from(CUSTOMER_OCCURRED_AT),
        Timestamp.from(CUSTOMER_OCCURRED_AT),
        Timestamp.from(CUSTOMER_OCCURRED_AT));

    jdbcTemplate.update(
        """
        INSERT INTO analytics_business_partner_projections
          (tenant_id, business_partner_id, partner_number, display_name, roles, source_event_id,
           aggregate_version, occurred_at, projected_at)
        VALUES (?, ?, 'C-100', 'Acme AG', CAST('[\"CUSTOMER\"]' AS jsonb), ?, 1, ?, ?)
        """,
        TENANT_ID,
        CUSTOMER_ID,
        CUSTOMER_EVENT_ID,
        Timestamp.from(CUSTOMER_OCCURRED_AT),
        Timestamp.from(CUSTOMER_OCCURRED_AT));

    jdbcTemplate.update(
        """
        INSERT INTO analytics_inbox_events
          (event_id, event_type, schema_version, tenant_id, aggregate_type, aggregate_id,
           aggregate_version, payload, content_fingerprint, projection_status, occurred_at,
           received_at, processed_at)
        VALUES (
          ?, 'operations.invoice.synchronized.v1', 1, ?, 'INVOICE', ?, 4,
          CAST('{}' AS jsonb), ?, 'APPLIED', ?, ?, ?
        )
        """,
        INVOICE_EVENT_ID,
        TENANT_ID,
        INVOICE_ID,
        "b".repeat(64),
        Timestamp.from(INVOICE_OCCURRED_AT),
        Timestamp.from(INVOICE_OCCURRED_AT),
        Timestamp.from(INVOICE_OCCURRED_AT));

    jdbcTemplate.update(
        """
        INSERT INTO analytics_invoice_receivable_projections
          (tenant_id, invoice_id, customer_id, invoice_number, original_amount, paid_amount,
           currency, issue_date, due_date, cancelled, status, source_event_id, aggregate_version,
           occurred_at, projected_at)
        VALUES (?, ?, ?, 'INV-100', ?, ?, 'CHF', ?, ?, false, 'PARTIALLY_PAID', ?, 4, ?, ?)
        """,
        TENANT_ID,
        INVOICE_ID,
        CUSTOMER_ID,
        new BigDecimal("100.00"),
        new BigDecimal("20.00"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        INVOICE_EVENT_ID,
        Timestamp.from(INVOICE_OCCURRED_AT),
        Timestamp.from(INVOICE_OCCURRED_AT));
  }
}
