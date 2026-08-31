package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import jakarta.validation.constraints.NotNull;

public record RequestImportRunRequest(
    @NotNull(message = "Import type is required") ImportType importType,
    @NotNull(message = "Import mode is required") ImportMode mode) {}
