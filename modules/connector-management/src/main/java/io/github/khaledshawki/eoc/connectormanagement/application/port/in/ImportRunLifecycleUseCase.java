package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

public interface ImportRunLifecycleUseCase {

  ImportRunResult request(RequestImportRunCommand command);

  ImportRunResult get(ImportRunReference reference);

  ImportRunResult start(ImportRunReference reference);

  ImportRunResult recordAcceptedPage(RecordAcceptedImportPageCommand command);

  ImportRunResult scheduleRetry(ScheduleImportRetryCommand command);

  ImportRunResult complete(ImportRunReference reference);

  ImportRunResult fail(FailImportRunCommand command);

  ImportRunResult requestCancellation(ImportRunReference reference);

  ImportRunResult confirmCancellation(ImportRunReference reference);
}
