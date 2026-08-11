package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayStatus;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.RequestConnectorDeadLetterReplayCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
    path = "/api/v1/admin/connector-event-dead-letters",
    produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('platform-admin')")
@ConditionalOnExpression(
    "'${eoc.connector-events.transport:local}' == 'kafka' and "
        + "${eoc.connector-events.kafka.dead-letter-recovery.enabled:false}")
public class ConnectorDeadLetterRecoveryController {

  private final InspectConnectorDeadLettersUseCase inspectUseCase;
  private final RequestConnectorDeadLetterReplayUseCase replayUseCase;
  private final JwtAuthenticatedUserMapper authenticatedUserMapper;

  public ConnectorDeadLetterRecoveryController(
      InspectConnectorDeadLettersUseCase inspectUseCase,
      RequestConnectorDeadLetterReplayUseCase replayUseCase,
      JwtAuthenticatedUserMapper authenticatedUserMapper) {
    this.inspectUseCase = Objects.requireNonNull(inspectUseCase, "Inspect use case cannot be null");
    this.replayUseCase = Objects.requireNonNull(replayUseCase, "Replay use case cannot be null");
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");
  }

  @GetMapping("/partitions")
  public DeadLetterPartitionsResponse listPartitions() {
    return new DeadLetterPartitionsResponse(
        inspectUseCase.listPartitions().stream().map(DeadLetterPartitionResponse::from).toList());
  }

  @GetMapping("/partitions/{partition}/records")
  public DeadLetterPageResponse listRecords(
      @PathVariable int partition,
      @RequestParam(defaultValue = "0") long fromOffset,
      @RequestParam(defaultValue = "20") int limit) {
    return DeadLetterPageResponse.from(inspectUseCase.list(partition, fromOffset, limit));
  }

  @GetMapping("/partitions/{partition}/records/{offset}")
  public DeadLetterRecordResponse getRecord(
      @PathVariable int partition, @PathVariable long offset) {
    return DeadLetterRecordResponse.from(
        inspectUseCase.get(new ConnectorDeadLetterReference(partition, offset)));
  }

  @PostMapping(
      path = "/partitions/{partition}/records/{offset}/replays",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<DeadLetterReplayResponse> requestReplay(
      @PathVariable int partition,
      @PathVariable long offset,
      @Valid @RequestBody RequestDeadLetterReplayRequest request,
      JwtAuthenticationToken authentication) {
    ConnectorDeadLetterReplayRequest replay =
        replayUseCase.request(
            new RequestConnectorDeadLetterReplayCommand(
                actor(authentication),
                new ConnectorDeadLetterReference(partition, offset),
                request.reason()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/admin/connector-event-dead-letters/replays/{requestId}")
            .buildAndExpand(replay.requestId())
            .toUri();
    return ResponseEntity.accepted().location(location).body(DeadLetterReplayResponse.from(replay));
  }

  @GetMapping("/replays/{requestId}")
  public DeadLetterReplayResponse getReplay(@PathVariable UUID requestId) {
    return DeadLetterReplayResponse.from(replayUseCase.get(requestId));
  }

  private ConnectorActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser user = authenticatedUserMapper.map(authentication);
    return new ConnectorActor(user.issuer().toString(), user.subject());
  }

  public record RequestDeadLetterReplayRequest(@NotBlank @Size(max = 500) String reason) {}

  public record DeadLetterPartitionsResponse(List<DeadLetterPartitionResponse> partitions) {}

  public record DeadLetterPartitionResponse(
      int partition, long beginningOffset, long endOffset, long recordCount) {

    static DeadLetterPartitionResponse from(ConnectorDeadLetterPartition partition) {
      return new DeadLetterPartitionResponse(
          partition.partition(),
          partition.beginningOffset(),
          partition.endOffset(),
          partition.recordCount());
    }
  }

  public record DeadLetterPageResponse(
      int partition,
      long fromOffset,
      long nextOffset,
      long endOffset,
      boolean hasMore,
      List<DeadLetterRecordSummaryResponse> records) {

    static DeadLetterPageResponse from(ConnectorDeadLetterPage page) {
      return new DeadLetterPageResponse(
          page.partition(),
          page.fromOffset(),
          page.nextOffset(),
          page.endOffset(),
          page.hasMore(),
          page.records().stream().map(DeadLetterRecordSummaryResponse::from).toList());
    }
  }

  public record DeadLetterRecordSummaryResponse(
      int partition,
      long offset,
      String sourceTopic,
      int sourcePartition,
      long sourceOffset,
      Instant sourceTimestamp,
      String failureCode,
      boolean retryable,
      String failureType,
      int replayGeneration,
      boolean keyPresent,
      boolean valuePresent,
      int valueBytes) {

    static DeadLetterRecordSummaryResponse from(ConnectorDeadLetterRecord record) {
      return new DeadLetterRecordSummaryResponse(
          record.reference().partition(),
          record.reference().offset(),
          record.sourceTopic(),
          record.sourcePartition(),
          record.sourceOffset(),
          record.sourceTimestamp(),
          record.failureCode(),
          record.retryable(),
          record.failureType(),
          record.replayGeneration(),
          record.key().isPresent(),
          record.value().isPresent(),
          record.value().map(value -> value.getBytes(StandardCharsets.UTF_8).length).orElse(0));
    }
  }

  public record DeadLetterRecordResponse(
      DeadLetterRecordSummaryResponse summary,
      Optional<String> key,
      Optional<String> value,
      Optional<String> failureMessage,
      List<ConnectorDeadLetterHeader> replayHeaders,
      String fingerprint) {

    static DeadLetterRecordResponse from(ConnectorDeadLetterRecord record) {
      return new DeadLetterRecordResponse(
          DeadLetterRecordSummaryResponse.from(record),
          record.key(),
          record.value(),
          record.failureMessage(),
          record.replayHeaders(),
          record.fingerprint());
    }
  }

  public record DeadLetterReplayResponse(
      UUID requestId,
      int deadLetterPartition,
      long deadLetterOffset,
      ConnectorDeadLetterReplayStatus status,
      int replayGeneration,
      String requestedByIssuer,
      String requestedBySubject,
      String reason,
      Instant requestedAt,
      int publicationAttemptCount,
      Optional<String> lastFailureCode,
      Optional<Instant> replayedAt) {

    static DeadLetterReplayResponse from(ConnectorDeadLetterReplayRequest replay) {
      return new DeadLetterReplayResponse(
          replay.requestId(),
          replay.deadLetter().partition(),
          replay.deadLetter().offset(),
          replay.status(),
          replay.replayGeneration(),
          replay.requestedByIssuer(),
          replay.requestedBySubject(),
          replay.reason(),
          replay.requestedAt(),
          replay.publicationAttemptCount(),
          replay.lastFailureCode(),
          replay.replayedAt());
    }
  }
}
