package io.github.khaledshawki.eoc.platform.copilot.adapter.in.web;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/copilot/questions",
    produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "eoc.copilot.llm.enabled", havingValue = "true")
public class CopilotQuestionController {

  private static final String ASK_COPILOT =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final AskCopilotUseCase askCopilotUseCase;
  private final JwtAuthenticatedUserMapper authenticatedUserMapper;

  public CopilotQuestionController(
      AskCopilotUseCase askCopilotUseCase, JwtAuthenticatedUserMapper authenticatedUserMapper) {
    this.askCopilotUseCase = askCopilotUseCase;
    this.authenticatedUserMapper = authenticatedUserMapper;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(ASK_COPILOT)
  public CopilotAnswerResponse ask(
      @PathVariable UUID tenantId,
      @Valid @RequestBody AskCopilotRequest request,
      JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = authenticatedUserMapper.map(authentication);
    CopilotExecutionContext context =
        new CopilotExecutionContext(
            authenticatedUser.issuer(), authenticatedUser.subject(), tenantId);
    CopilotQuestion question =
        new CopilotQuestion(request.question(), Optional.ofNullable(request.businessDate()));

    return CopilotAnswerResponse.from(askCopilotUseCase.ask(context, question));
  }

  public record AskCopilotRequest(
      @NotBlank @Size(max = CopilotQuestion.MAX_LENGTH) String question, LocalDate businessDate) {}

  public record CopilotAnswerResponse(String answer, List<GroundingResponse> grounding) {
    static CopilotAnswerResponse from(CopilotAnswer answer) {
      return new CopilotAnswerResponse(
          answer.text(), answer.grounding().stream().map(GroundingResponse::from).toList());
    }
  }

  public record GroundingResponse(
      String toolCallId, String toolName, List<SourceEvidenceResponse> sourceEvidence) {
    static GroundingResponse from(CopilotAnswerGrounding grounding) {
      return new GroundingResponse(
          grounding.toolCallId(),
          grounding.toolName().contractName(),
          grounding.sourceEvidence().stream().map(SourceEvidenceResponse::from).toList());
    }
  }

  public record SourceEvidenceResponse(UUID eventId, long aggregateVersion, Instant occurredAt) {
    static SourceEvidenceResponse from(CopilotEvidence evidence) {
      return new SourceEvidenceResponse(
          evidence.eventId(), evidence.aggregateVersion(), evidence.occurredAt());
    }
  }
}
