package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface ExecuteImportRunUseCase {

  ImportRunResult execute(ExecuteImportRunCommand command);
}
