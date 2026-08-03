package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportFailurePayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunCompletedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunFailedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunRetryScheduledPayload;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorIntegrationEventPayloadSerializerTest {

  private static final UUID CONNECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000083");
  private static final ImportFailurePayload FAILURE =
      new ImportFailurePayload("TIMEOUT", "source-timeout");
  private final ConnectorIntegrationEventPayloadSerializer serializer =
      new ConnectorIntegrationEventPayloadSerializer();

  @Test
  void shouldSerializeTheCompletedContractDeterministically() {
    assertEquals(
        """
        {"connectorId":"00000000-0000-0000-0000-000000000083","importType":"CUSTOMERS","importMode":"INCREMENTAL","status":"COMPLETED","fetchedCount":3,"acceptedCount":2,"rejectedCount":0,"duplicateCount":1,"attemptCount":1}
        """
            .strip(),
        serializer.serialize(
            new ImportRunCompletedPayload(
                CONNECTOR_ID, "CUSTOMERS", "INCREMENTAL", "COMPLETED", 3, 2, 0, 1, 1)));
  }

  @Test
  void shouldSerializeTheFailedContractDeterministically() {
    assertEquals(
        """
        {"connectorId":"00000000-0000-0000-0000-000000000083","importType":"CUSTOMERS","importMode":"FULL","failure":{"category":"TIMEOUT","code":"source-timeout"},"attemptCount":2}
        """
            .strip(),
        serializer.serialize(
            new ImportRunFailedPayload(CONNECTOR_ID, "CUSTOMERS", "FULL", FAILURE, 2)));
  }

  @Test
  void shouldSerializeTheRetryContractDeterministically() {
    assertEquals(
        """
        {"connectorId":"00000000-0000-0000-0000-000000000083","importType":"CUSTOMERS","importMode":"INCREMENTAL","failure":{"category":"TIMEOUT","code":"source-timeout"},"attemptCount":2,"nextRetryAt":"2026-08-03T18:01:00Z"}
        """
            .strip(),
        serializer.serialize(
            new ImportRunRetryScheduledPayload(
                CONNECTOR_ID,
                "CUSTOMERS",
                "INCREMENTAL",
                FAILURE,
                2,
                Instant.parse("2026-08-03T18:01:00Z"))));
  }
}
