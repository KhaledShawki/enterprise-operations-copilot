package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayLimitExceededException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.RequestConnectorDeadLetterReplayCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RequestConnectorDeadLetterReplayService
    implements RequestConnectorDeadLetterReplayUseCase {

  private final ConnectorDeadLetterReader reader;
  private final ConnectorDeadLetterReplayRepository repository;
  private final Supplier<UUID> requestIdGenerator;
  private final Clock clock;
  private final int maxReplayGeneration;

  public RequestConnectorDeadLetterReplayService(
      ConnectorDeadLetterReader reader,
      ConnectorDeadLetterReplayRepository repository,
      Supplier<UUID> requestIdGenerator,
      Clock clock,
      int maxReplayGeneration) {
    this.reader = Objects.requireNonNull(reader, "Connector dead-letter reader cannot be null");
    this.repository =
        Objects.requireNonNull(repository, "Connector replay repository cannot be null");
    this.requestIdGenerator =
        Objects.requireNonNull(requestIdGenerator, "Replay request id generator cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    if (maxReplayGeneration < 1 || maxReplayGeneration > 100) {
      throw new IllegalArgumentException("Maximum replay generation must be between 1 and 100");
    }
    this.maxReplayGeneration = maxReplayGeneration;
  }

  @Override
  public ConnectorDeadLetterReplayRequest request(RequestConnectorDeadLetterReplayCommand command) {
    Objects.requireNonNull(command, "Replay request command cannot be null");
    ConnectorDeadLetterRecord deadLetter =
        reader
            .find(command.deadLetter())
            .orElseThrow(() -> new ConnectorDeadLetterNotFoundException(command.deadLetter()));
    if (deadLetter.replayGeneration() >= maxReplayGeneration) {
      throw new ConnectorDeadLetterReplayLimitExceededException(
          deadLetter.reference(), maxReplayGeneration);
    }
    UUID requestId =
        Objects.requireNonNull(
            requestIdGenerator.get(), "Generated replay request id cannot be null");
    return repository.request(
        new NewConnectorDeadLetterReplayRequest(
            requestId,
            deadLetter,
            deadLetter.fingerprint(),
            command.actor().issuer(),
            command.actor().subject(),
            command.reason(),
            clock.instant()));
  }

  @Override
  public ConnectorDeadLetterReplayRequest get(UUID requestId) {
    Objects.requireNonNull(requestId, "Replay request id cannot be null");
    return repository
        .findById(requestId)
        .orElseThrow(() -> new ConnectorDeadLetterReplayNotFoundException(requestId));
  }
}
