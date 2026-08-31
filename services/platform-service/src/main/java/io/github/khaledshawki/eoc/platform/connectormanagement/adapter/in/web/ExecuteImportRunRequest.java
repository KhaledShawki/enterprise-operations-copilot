package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ExecuteImportRunRequest(
    @Min(value = 1, message = "Page size must be at least 1")
        @Max(
            value = SourceFetchRequest.MAX_PAGE_SIZE,
            message = "Page size cannot exceed " + SourceFetchRequest.MAX_PAGE_SIZE)
        int pageSize) {}
