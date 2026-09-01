package io.github.khaledshawki.eoc.platform.integration.copilot.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.platform.observability.metrics.CopilotMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeteredAskCopilotUseCaseTest {

  private static final CopilotExecutionContext CONTEXT =
      new CopilotExecutionContext(
          URI.create("http://issuer.example"),
          "subject",
          UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final CopilotQuestion QUESTION = CopilotQuestion.current("What is overdue?");

  @Test
  void recordsSuccessfulRequestWithoutChangingAnswer() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CopilotAnswer answer =
        new CopilotAnswer(
            "Grounded answer",
            List.of(
                new CopilotAnswerGrounding(
                    "call-1", CopilotToolName.GET_RECEIVABLES_SUMMARY, List.of())));
    AskCopilotUseCase delegate = (context, question) -> answer;
    MeteredAskCopilotUseCase useCase =
        new MeteredAskCopilotUseCase(delegate, new CopilotMetrics(registry));

    assertSame(answer, useCase.ask(CONTEXT, QUESTION));
    assertEquals(
        1.0, registry.find("eoc.copilot.request").tag("outcome", "success").counter().count());
  }

  @Test
  void recordsFailureAndPreservesOriginalException() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    IllegalStateException failure = new IllegalStateException("model failure");
    AskCopilotUseCase delegate =
        (context, question) -> {
          throw failure;
        };
    MeteredAskCopilotUseCase useCase =
        new MeteredAskCopilotUseCase(delegate, new CopilotMetrics(registry));

    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> useCase.ask(CONTEXT, QUESTION)));
    assertEquals(
        1.0, registry.find("eoc.copilot.request").tag("outcome", "failure").counter().count());
  }
}
