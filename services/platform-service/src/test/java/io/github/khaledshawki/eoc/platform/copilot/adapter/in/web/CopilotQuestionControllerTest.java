package io.github.khaledshawki.eoc.platform.copilot.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.TenantAccessPolicy;
import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CopilotQuestionController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "eoc.copilot.llm.enabled=true")
class CopilotQuestionControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000671");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000672");
  private static final URI ISSUER = URI.create("http://localhost:8180/realms/eoc");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 13);
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-12T15:00:00Z");
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/copilot/questions";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AskCopilotUseCase askCopilotUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper authenticatedUserMapper;

  @MockitoBean(name = "tenantAccessPolicy")
  private TenantAccessPolicy tenantAccessPolicy;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void shouldRejectUnauthenticatedAndUnauthorizedRequestsBeforeCallingCopilot() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is overdue?\"}"))
        .andExpect(status().isUnauthorized());

    when(tenantAccessPolicy.hasAnyRole(any(), eq(TENANT_ID), any(String[].class)))
        .thenReturn(false);

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is overdue?\"}")
                .with(jwt()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(askCopilotUseCase);
  }

  @Test
  void shouldMapTrustedJwtTenantAndBusinessDateAndReturnGroundedAnswer() throws Exception {
    allowTenantAccess();
    when(authenticatedUserMapper.map(any()))
        .thenReturn(new AuthenticatedUser(ISSUER, "user-671", Set.of("auditor")));
    when(askCopilotUseCase.ask(any(), any())).thenReturn(groundedAnswer());

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"  What is overdue?  \",\"businessDate\":\"2026-08-13\"}")
                .with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("1 invoice is overdue."))
        .andExpect(jsonPath("$.grounding[0].toolCallId").value("call-1"))
        .andExpect(jsonPath("$.grounding[0].toolName").value("get_receivable"))
        .andExpect(jsonPath("$.grounding[0].sourceEvidence[0].eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.grounding[0].sourceEvidence[0].aggregateVersion").value(4))
        .andExpect(
            jsonPath("$.grounding[0].sourceEvidence[0].occurredAt").value("2026-08-12T15:00:00Z"));

    ArgumentCaptor<CopilotExecutionContext> context =
        ArgumentCaptor.forClass(CopilotExecutionContext.class);
    ArgumentCaptor<CopilotQuestion> question = ArgumentCaptor.forClass(CopilotQuestion.class);
    verify(askCopilotUseCase).ask(context.capture(), question.capture());

    assertThat(context.getValue().issuer()).isEqualTo(ISSUER);
    assertThat(context.getValue().subject()).isEqualTo("user-671");
    assertThat(context.getValue().tenantId()).isEqualTo(TENANT_ID);
    assertThat(question.getValue().text()).isEqualTo("What is overdue?");
    assertThat(question.getValue().businessDate()).contains(BUSINESS_DATE);
  }

  @Test
  void shouldLeaveBusinessDateUnspecifiedWhenCallerDoesNotProvideOne() throws Exception {
    allowTenantAccess();
    when(authenticatedUserMapper.map(any()))
        .thenReturn(new AuthenticatedUser(ISSUER, "user-671", Set.of("auditor")));
    when(askCopilotUseCase.ask(any(), any())).thenReturn(groundedAnswer());

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is overdue?\"}")
                .with(jwt()))
        .andExpect(status().isOk());

    ArgumentCaptor<CopilotQuestion> question = ArgumentCaptor.forClass(CopilotQuestion.class);
    verify(askCopilotUseCase).ask(any(), question.capture());
    assertThat(question.getValue().businessDate()).isEqualTo(Optional.empty());
  }

  @Test
  void shouldRejectBlankAndOversizedQuestionsBeforeCallingCopilot() throws Exception {
    allowTenantAccess();

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"   \"}")
                .with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

    String oversized = "x".repeat(CopilotQuestion.MAX_LENGTH + 1);
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"" + oversized + "\"}")
                .with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

    verifyNoInteractions(askCopilotUseCase);
  }

  @Test
  void shouldMapUnavailableModelWithoutLeakingProviderFailure() throws Exception {
    allowTenantAccess();
    when(authenticatedUserMapper.map(any()))
        .thenReturn(new AuthenticatedUser(ISSUER, "user-671", Set.of("auditor")));
    when(askCopilotUseCase.ask(any(), any()))
        .thenThrow(
            new CopilotModelUnavailableException(
                new IllegalStateException("secret-provider-credential-detail")));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is overdue?\"}")
                .with(jwt()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COPILOT_UNAVAILABLE"))
        .andExpect(jsonPath("$.detail").value("Copilot is temporarily unavailable."));
  }

  private void allowTenantAccess() {
    when(tenantAccessPolicy.hasAnyRole(any(), eq(TENANT_ID), any(String[].class))).thenReturn(true);
  }

  private static CopilotAnswer groundedAnswer() {
    return new CopilotAnswer(
        "1 invoice is overdue.",
        List.of(
            new CopilotAnswerGrounding(
                "call-1",
                CopilotToolName.GET_RECEIVABLE,
                List.of(new CopilotEvidence(EVENT_ID, 4, OCCURRED_AT)))));
  }
}
