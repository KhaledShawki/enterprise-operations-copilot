package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;

public interface OperationsAuthorizationPort {

  boolean hasPermission(
      OperationsActor actor, OperationsTenantId tenantId, OperationsPermission permission);
}
