package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

public sealed interface ConnectorIntegrationEventPayload
    permits ImportRunCompletedPayload, ImportRunFailedPayload, ImportRunRetryScheduledPayload {}
