package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxCursor;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecoveryPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.InspectOperationsOutboxUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(
    path = "/api/v1/admin/operations-outbox",
    produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('platform-admin')")
public class OperationsOutboxAdministrationController {

  private final InspectOperationsOutboxUseCase inspectUseCase;
  private final RecoverOperationsOutboxEventUseCase recoverUseCase;
  private final JwtAuthenticatedUserMapper authenticatedUserMapper;

  public OperationsOutboxAdministrationController(
      InspectOperationsOutboxUseCase inspectUseCase,
      RecoverOperationsOutboxEventUseCase recoverUseCase,
      JwtAuthenticatedUserMapper authenticatedUserMapper) {
    this.inspectUseCase =
        Objects.requireNonNull(inspectUseCase, "Operations outbox inspect use case cannot be null");
    this.recoverUseCase =
        Objects.requireNonNull(recoverUseCase, "Operations outbox recover use case cannot be null");
    this.authenticatedUserMapper =
        Objects.requireNonNull(
            authenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @GetMapping("/events")
  public OutboxPageResponse listEvents(
      @RequestParam(required = false) OperationsOutboxStatus status,
      @RequestParam(required = false) UUID tenantId,
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) UUID aggregateId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant cursorCreatedAt,
      @RequestParam(required = false) UUID cursorEventId,
      @RequestParam(defaultValue = "20") int limit) {
    if (aggregateId != null && aggregateType == null) {
      throw new IllegalArgumentException("aggregateType is required when aggregateId is provided");
    }
    OperationsOutboxPage page =
        inspectUseCase.list(
            new OperationsOutboxInspectionFilter(
                Optional.ofNullable(status),
                Optional.ofNullable(tenantId),
                Optional.ofNullable(aggregateType),
                Optional.ofNullable(aggregateId),
                cursor(cursorCreatedAt, cursorEventId),
                limit));
    return OutboxPageResponse.from(page);
  }

  @GetMapping("/events/{eventId}")
  public OutboxEventResponse getEvent(@PathVariable UUID eventId) {
    return OutboxEventResponse.from(inspectUseCase.get(eventId));
  }

  @GetMapping("/events/{eventId}/recoveries")
  public RecoveryPageResponse listRecoveries(
      @PathVariable UUID eventId,
      @RequestParam(required = false) Integer beforeGeneration,
      @RequestParam(defaultValue = "20") int limit) {
    return RecoveryPageResponse.from(
        inspectUseCase.listRecoveries(eventId, Optional.ofNullable(beforeGeneration), limit));
  }

  @PostMapping(path = "/events/{eventId}/recoveries", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RecoveryResponse> recover(
      @PathVariable UUID eventId,
      @Valid @RequestBody RequestRecoveryRequest request,
      JwtAuthenticationToken authentication) {
    OperationsOutboxRecovery recovery =
        recoverUseCase.recover(
            new RecoverOperationsOutboxEventCommand(
                actor(authentication), eventId, request.reason()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/admin/operations-outbox/events/{eventId}")
            .buildAndExpand(eventId)
            .toUri();
    return ResponseEntity.created(location).body(RecoveryResponse.from(recovery));
  }

  private OperationsActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser user = authenticatedUserMapper.map(authentication);
    return new OperationsActor(user.issuer().toString(), user.subject());
  }

  private static Optional<OperationsOutboxCursor> cursor(
      Instant cursorCreatedAt, UUID cursorEventId) {
    if (cursorCreatedAt == null && cursorEventId == null) {
      return Optional.empty();
    }
    if (cursorCreatedAt == null || cursorEventId == null) {
      throw new IllegalArgumentException(
          "cursorCreatedAt and cursorEventId must be provided together");
    }
    return Optional.of(new OperationsOutboxCursor(cursorCreatedAt, cursorEventId));
  }

  public record RequestRecoveryRequest(@NotBlank @Size(max = 500) String reason) {}

  public record OutboxPageResponse(
      List<OutboxEventResponse> events,
      Optional<Instant> nextCursorCreatedAt,
      Optional<UUID> nextCursorEventId) {

    static OutboxPageResponse from(OperationsOutboxPage page) {
      return new OutboxPageResponse(
          page.events().stream().map(OutboxEventResponse::from).toList(),
          page.nextCursor().map(OperationsOutboxCursor::createdAt),
          page.nextCursor().map(OperationsOutboxCursor::eventId));
    }
  }

  public record OutboxEventResponse(
      UUID eventId,
      String eventType,
      int schemaVersion,
      UUID tenantId,
      String aggregateType,
      UUID aggregateId,
      long aggregateVersion,
      Instant occurredAt,
      OperationsOutboxStatus status,
      int publicationAttemptCount,
      int recoveryGeneration,
      int generationAttemptCount,
      Instant nextPublishAt,
      Optional<Instant> claimedAt,
      Optional<String> claimedBy,
      Optional<Instant> publishedAt,
      Optional<String> lastFailureCode,
      Instant createdAt,
      Instant updatedAt) {

    static OutboxEventResponse from(OperationsOutboxEventView event) {
      return new OutboxEventResponse(
          event.eventId(),
          event.eventType(),
          event.schemaVersion(),
          event.tenantId(),
          event.aggregateType(),
          event.aggregateId(),
          event.aggregateVersion(),
          event.occurredAt(),
          event.status(),
          event.publicationAttemptCount(),
          event.recoveryGeneration(),
          event.generationAttemptCount(),
          event.nextPublishAt(),
          event.claimedAt(),
          event.claimedBy(),
          event.publishedAt(),
          event.lastFailureCode(),
          event.createdAt(),
          event.updatedAt());
    }
  }

  public record RecoveryPageResponse(
      List<RecoveryResponse> recoveries, Optional<Integer> nextBeforeGeneration) {

    static RecoveryPageResponse from(OperationsOutboxRecoveryPage page) {
      return new RecoveryPageResponse(
          page.recoveries().stream().map(RecoveryResponse::from).toList(),
          page.nextBeforeGeneration());
    }
  }

  public record RecoveryResponse(
      UUID recoveryId,
      UUID eventId,
      int recoveryGeneration,
      String requestedByIssuer,
      String requestedBySubject,
      String reason,
      OperationsOutboxStatus previousStatus,
      int previousPublicationAttemptCount,
      int previousGenerationAttemptCount,
      String previousFailureCode,
      Instant requestedAt,
      Instant completedAt) {

    static RecoveryResponse from(OperationsOutboxRecovery recovery) {
      return new RecoveryResponse(
          recovery.recoveryId(),
          recovery.eventId(),
          recovery.recoveryGeneration(),
          recovery.requestedByIssuer(),
          recovery.requestedBySubject(),
          recovery.reason(),
          recovery.previousStatus(),
          recovery.previousPublicationAttemptCount(),
          recovery.previousGenerationAttemptCount(),
          recovery.previousFailureCode(),
          recovery.requestedAt(),
          recovery.completedAt());
    }
  }
}
