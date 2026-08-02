package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataImportPageAcceptanceRepository
    extends JpaRepository<ImportPageAcceptanceJpaEntity, ImportPageAcceptanceJpaId> {

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO connector_import_page_acceptances (
            import_run_id, acceptance_id, accepted_at
          ) VALUES (
            :importRunId, :acceptanceId, :acceptedAt
          )
          ON CONFLICT ON CONSTRAINT pk_connector_import_page_acceptances DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("importRunId") UUID importRunId,
      @Param("acceptanceId") UUID acceptanceId,
      @Param("acceptedAt") Instant acceptedAt);
}
