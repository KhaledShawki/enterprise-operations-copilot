package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxEventNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxRecoveryConflictException;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.InspectOperationsOutboxUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OperationsOutboxAdministrationController.class)
@Import(SecurityConfiguration.class)
class OperationsOutboxAdministrationControllerSecurityTest {

  private static final String EVENTS = "/api/v1/admin/operations-outbox/events";
  private static final Instant NOW = Instant.parse("2026-08-11T16:00:00Z");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000951");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000952");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000953");
  private static final UUID RECOVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000000954");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InspectOperationsOutboxUseCase inspectUseCase;
  @MockitoBean private RecoverOperationsOutboxEventUseCase recoverUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper authenticatedUserMapper;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void shouldRejectUnauthenticatedAndTenantScopedRoles() throws Exception {
    mockMvc.perform(get(EVENTS)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get(EVENTS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_tenant-admin"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc
        .perform(
            post(EVENTS + "/" + EVENT_ID + "/recoveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"retry\"}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_operations-manager"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(inspectUseCase, recoverUseCase);
  }

  @Test
  void shouldAllowPlatformAdminInspectionWithoutExposingPayload() throws Exception {
    when(inspectUseCase.list(any(OperationsOutboxInspectionFilter.class)))
        .thenReturn(new OperationsOutboxPage(List.of(pendingEvent()), Optional.empty()));

    mockMvc
        .perform(get(EVENTS).with(platformAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[0].eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.events[0].status").value("PENDING"))
        .andExpect(jsonPath("$.events[0].payload").doesNotExist());
  }

  @Test
  void shouldAllowPlatformAdminToRequestAuditedRecovery() throws Exception {
    when(authenticatedUserMapper.map(any()))
        .thenReturn(
            new AuthenticatedUser(
                URI.create("http://localhost:8180/realms/eoc"),
                "platform-admin-1",
                Set.of("platform-admin")));
    when(recoverUseCase.recover(any(RecoverOperationsOutboxEventCommand.class)))
        .thenReturn(recovery());

    mockMvc
        .perform(
            post(EVENTS + "/" + EVENT_ID + "/recoveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"broker configuration corrected\"}")
                .with(platformAdmin()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recoveryId").value(RECOVERY_ID.toString()))
        .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.recoveryGeneration").value(1))
        .andExpect(jsonPath("$.previousStatus").value("FAILED"));
  }

  @Test
  void shouldMapMissingEventsAndRecoveryConflictsWithoutLeakingInternals() throws Exception {
    when(inspectUseCase.get(EVENT_ID))
        .thenThrow(new OperationsOutboxEventNotFoundException(EVENT_ID));

    mockMvc
        .perform(get(EVENTS + "/" + EVENT_ID).with(platformAdmin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("OPERATIONS_OUTBOX_EVENT_NOT_FOUND"));

    when(authenticatedUserMapper.map(any()))
        .thenReturn(
            new AuthenticatedUser(
                URI.create("http://localhost:8180/realms/eoc"),
                "platform-admin-1",
                Set.of("platform-admin")));
    when(recoverUseCase.recover(any(RecoverOperationsOutboxEventCommand.class)))
        .thenThrow(
            new OperationsOutboxRecoveryConflictException(
                EVENT_ID, "Operations outbox event is not recoverable from status PUBLISHED"));

    mockMvc
        .perform(
            post(EVENTS + "/" + EVENT_ID + "/recoveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"retry\"}")
                .with(platformAdmin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OPERATIONS_OUTBOX_RECOVERY_CONFLICT"));
  }

  @Test
  void shouldRejectBlankRecoveryReasonBeforeCallingTheUseCase() throws Exception {
    mockMvc
        .perform(
            post(EVENTS + "/" + EVENT_ID + "/recoveries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"   \"}")
                .with(platformAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OPERATIONS_OUTBOX_REQUEST"));

    verifyNoInteractions(recoverUseCase);
  }

  @Test
  void shouldRejectIncompleteCursorAndOversizedLimit() throws Exception {
    mockMvc
        .perform(get(EVENTS).param("cursorEventId", EVENT_ID.toString()).with(platformAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OPERATIONS_OUTBOX_REQUEST"));

    mockMvc
        .perform(get(EVENTS).param("limit", "101").with(platformAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_OPERATIONS_OUTBOX_REQUEST"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin() {
    return jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin"));
  }

  private static OperationsOutboxEventView pendingEvent() {
    return new OperationsOutboxEventView(
        EVENT_ID,
        "operations.invoice.synchronized.v1",
        1,
        TENANT_ID,
        "INVOICE",
        AGGREGATE_ID,
        1,
        NOW.minusSeconds(1),
        OperationsOutboxStatus.PENDING,
        0,
        0,
        0,
        NOW.minusSeconds(1),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        NOW,
        NOW);
  }

  private static OperationsOutboxRecovery recovery() {
    return new OperationsOutboxRecovery(
        RECOVERY_ID,
        EVENT_ID,
        1,
        "http://localhost:8180/realms/eoc",
        "platform-admin-1",
        "broker configuration corrected",
        OperationsOutboxStatus.FAILED,
        3,
        3,
        "broker-unavailable",
        NOW,
        NOW);
  }
}
