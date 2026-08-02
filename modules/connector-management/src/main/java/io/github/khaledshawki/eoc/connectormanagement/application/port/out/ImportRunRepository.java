package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCheckpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportCursor;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportPageAcceptanceId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRun;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import java.util.Optional;

public interface ImportRunRepository {

  ImportRun save(ImportRun importRun);

  Optional<ImportRun> findById(ConnectorTenantId tenantId, ImportRunId importRunId);

  boolean existsActive(ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType);

  Optional<ImportCursor> findCheckpoint(
      ConnectorTenantId tenantId, ConnectorId connectorId, ImportType importType);

  boolean hasAcceptedPage(ImportRunId importRunId, ImportPageAcceptanceId acceptanceId);

  ImportRun saveAcceptedProgress(
      ImportRun importRun,
      Optional<ImportCheckpoint> checkpoint,
      ImportPageAcceptanceId acceptanceId);
}
