package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataImportCheckpointRepository
    extends JpaRepository<ImportCheckpointJpaEntity, ImportCheckpointJpaId> {}
