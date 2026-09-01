package io.github.khaledshawki.eoc.platform.integration.connectormanagement.observability;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.platform.observability.metrics.ConnectorImportMetrics;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

public final class MeteredExecuteImportRunUseCase implements ExecuteImportRunUseCase {

  private final ExecuteImportRunUseCase delegate;
  private final ConnectorImportMetrics metrics;

  public MeteredExecuteImportRunUseCase(
      ExecuteImportRunUseCase delegate, ConnectorImportMetrics metrics) {
    this.delegate = Objects.requireNonNull(delegate, "Import-run delegate cannot be null");
    this.metrics = Objects.requireNonNull(metrics, "Connector import metrics cannot be null");
  }

  @Override
  public ImportRunResult execute(ExecuteImportRunCommand command) {
    Timer.Sample sample = metrics.startTimer();
    try {
      ImportRunResult result = delegate.execute(command);
      metrics.recordExecution(result.importType(), result.status(), sample);
      return result;
    } catch (RuntimeException exception) {
      metrics.recordUnexpectedFailure(sample);
      throw exception;
    }
  }
}
