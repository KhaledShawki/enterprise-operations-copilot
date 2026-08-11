package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.khaledshawki.eoc.operations.application.model.event.BusinessPartnerSynchronizedPayload;
import io.github.khaledshawki.eoc.operations.application.model.event.SourceRecordEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OperationsIntegrationEventPayloadSerializerTest {

  @Test
  void shouldEscapePortablePayloadValuesWithoutIntroducingExcludedPersonalData() throws Exception {
    JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    OperationsIntegrationEventPayloadSerializer serializer =
        new OperationsIntegrationEventPayloadSerializer(jsonMapper);
    BusinessPartnerSynchronizedPayload payload =
        new BusinessPartnerSynchronizedPayload(
            UUID.fromString("00000000-0000-0000-0000-000000000801"),
            "BP-801",
            "Acme \"North\"",
            List.of("CUSTOMER"),
            new SourceRecordEvidence(
                UUID.fromString("00000000-0000-0000-0000-000000000802"),
                "SOURCE_RECORD_ID",
                "partner/801",
                "v1",
                Optional.empty()));

    JsonNode serialized = jsonMapper.readTree(serializer.serialize(payload));

    assertEquals("Acme \"North\"", serialized.get("displayName").textValue());
    assertEquals("CUSTOMER", serialized.get("roles").get(0).textValue());
    assertEquals("partner/801", serialized.get("source").get("sourceIdentity").textValue());
    assertFalse(serialized.has("emailAddress"));
  }
}
