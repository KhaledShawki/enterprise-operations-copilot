package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = ConnectorDeadLetterRecoveryController.class,
    properties = {
      "eoc.connector-events.transport=kafka",
      "eoc.connector-events.kafka.dead-letter-recovery.enabled=true"
    })
@Import(SecurityConfiguration.class)
class ConnectorDeadLetterRecoveryControllerSecurityTest {

  private static final String ENDPOINT = "/api/v1/admin/connector-event-dead-letters/partitions";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InspectConnectorDeadLettersUseCase inspectUseCase;
  @MockitoBean private RequestConnectorDeadLetterReplayUseCase replayUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper authenticatedUserMapper;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void rejectsUnauthenticatedAndNonPlatformAdminInspection() throws Exception {
    mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(ENDPOINT).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_auditor"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(inspectUseCase, replayUseCase);
  }

  @Test
  void allowsOnlyTheGlobalPlatformAdminRole() throws Exception {
    when(inspectUseCase.listPartitions()).thenReturn(List.of());

    mockMvc
        .perform(
            get(ENDPOINT)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.partitions").isEmpty());
  }

  @Test
  void listResponsesDoNotExposeRawRecordMaterial() throws Exception {
    ConnectorDeadLetterRecord record =
        new ConnectorDeadLetterRecord(
            new ConnectorDeadLetterReference(1, 9),
            "connector.events.dlt",
            Optional.of("sensitive-key"),
            Optional.of("sensitive-value"),
            "connector.events",
            1,
            7,
            Instant.parse("2026-08-10T12:00:00Z"),
            "invalid-envelope",
            false,
            "java.lang.IllegalArgumentException",
            Optional.of("invalid"),
            0,
            List.of());
    when(inspectUseCase.list(1, 0, 20))
        .thenReturn(new ConnectorDeadLetterPage(1, 0, 10, 10, List.of(record)));

    mockMvc
        .perform(
            get("/api/v1/admin/connector-event-dead-letters/partitions/1/records")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_platform-admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.records[0].keyPresent").value(true))
        .andExpect(jsonPath("$.records[0].valuePresent").value(true))
        .andExpect(jsonPath("$.records[0].key").doesNotExist())
        .andExpect(jsonPath("$.records[0].value").doesNotExist());
  }
}
