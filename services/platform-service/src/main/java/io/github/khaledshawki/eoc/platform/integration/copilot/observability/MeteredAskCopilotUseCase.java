package io.github.khaledshawki.eoc.platform.integration.copilot.observability;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.platform.observability.metrics.CopilotMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

public final class MeteredAskCopilotUseCase implements AskCopilotUseCase {

  private final AskCopilotUseCase delegate;
  private final CopilotMetrics metrics;

  public MeteredAskCopilotUseCase(AskCopilotUseCase delegate, CopilotMetrics metrics) {
    this.delegate = Objects.requireNonNull(delegate, "Copilot delegate cannot be null");
    this.metrics = Objects.requireNonNull(metrics, "Copilot metrics cannot be null");
  }

  @Override
  public CopilotAnswer ask(CopilotExecutionContext context, CopilotQuestion question) {
    Timer.Sample sample = metrics.startTimer();
    try {
      CopilotAnswer answer = delegate.ask(context, question);
      metrics.recordSuccess(sample);
      return answer;
    } catch (RuntimeException exception) {
      metrics.recordFailure(sample);
      throw exception;
    }
  }
}
