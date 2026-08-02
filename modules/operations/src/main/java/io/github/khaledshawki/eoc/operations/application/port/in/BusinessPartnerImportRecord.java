package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record BusinessPartnerImportRecord(
    SourceRecordIdentity sourceIdentity,
    SourceRecordVersion sourceVersion,
    Optional<Instant> sourceModifiedAt,
    BusinessPartnerProfile profile) {

  public BusinessPartnerImportRecord {
    Objects.requireNonNull(sourceIdentity, "Source identity cannot be null");
    Objects.requireNonNull(sourceVersion, "Source version cannot be null");
    Objects.requireNonNull(sourceModifiedAt, "Source modification timestamp cannot be null");
    Objects.requireNonNull(profile, "Business partner profile cannot be null");
  }
}
