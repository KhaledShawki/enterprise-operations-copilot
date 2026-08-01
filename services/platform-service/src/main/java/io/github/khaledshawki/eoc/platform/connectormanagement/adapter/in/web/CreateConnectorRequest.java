package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateConnectorRequest(
    @NotBlank(message = "Connector name is required")
        @Size(
            max = ConnectorName.MAX_LENGTH,
            message = "Connector name cannot be longer than {max} characters")
        String name,
    @NotBlank(message = "Connector type is required")
        @Size(
            max = ConnectorType.MAX_LENGTH,
            message = "Connector type cannot be longer than {max} characters")
        @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message =
                "Connector type must contain lowercase letters, numbers, and single hyphens only")
        String type,
    @NotBlank(message = "Connector endpoint is required")
        @Size(
            max = ConnectorEndpoint.MAX_LENGTH,
            message = "Connector endpoint cannot be longer than {max} characters")
        String endpoint,
    @NotNull(message = "Credential reference is required") UUID credentialReference,
    @NotNull(message = "Sync policy is required") @Valid SyncPolicyRequest syncPolicy) {}
