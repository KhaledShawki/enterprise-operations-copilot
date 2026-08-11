package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;

public interface ProjectInvoiceReceivableUseCase {

  ProjectionApplyResult project(ProjectInvoiceReceivableCommand command);
}
