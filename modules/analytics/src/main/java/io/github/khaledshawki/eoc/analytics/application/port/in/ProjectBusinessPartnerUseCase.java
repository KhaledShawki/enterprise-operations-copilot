package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;

public interface ProjectBusinessPartnerUseCase {

  ProjectionApplyResult project(ProjectBusinessPartnerCommand command);
}
