package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlatformUserProvisioningController.class)
@Import({SecurityConfiguration.class, JwtAuthenticatedUserMapper.class})
class PlatformUserProvisioningControllerTest {

  private static final String ENDPOINT = "/api/v1/platform-users/me";
  private static final String ACCESS_TOKEN = "platform-user-access-token";
  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";

  private static final UUID USER_ID = UUID.fromString("f6df18c0-306e-4be0-b2c2-b985e3aadcb7");

  private static final ExternalIdentity EXTERNAL_IDENTITY = ExternalIdentity.of(ISSUER, SUBJECT);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private ProvisionPlatformUserUseCase provisionPlatformUserUseCase;

  @Test
  void shouldRejectUnauthenticatedProvisioning() throws Exception {
    mockMvc
        .perform(put(ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(provisionPlatformUserUseCase);
  }

  @Test
  void shouldCreatePlatformUserForAuthenticatedIdentity() throws Exception {
    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt());

    ProvisionPlatformUserResult result =
        new ProvisionPlatformUserResult(
            PlatformUserId.of(USER_ID), EXTERNAL_IDENTITY, PlatformUserStatus.ACTIVE, true);

    when(provisionPlatformUserUseCase.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT)))
        .thenReturn(result);

    mockMvc
        .perform(
            put(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost" + ENDPOINT))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(provisionPlatformUserUseCase)
        .provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT));
  }

  @Test
  void shouldReturnExistingPlatformUser() throws Exception {
    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt());

    ProvisionPlatformUserResult result =
        new ProvisionPlatformUserResult(
            PlatformUserId.of(USER_ID), EXTERNAL_IDENTITY, PlatformUserStatus.SUSPENDED, false);

    when(provisionPlatformUserUseCase.provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT)))
        .thenReturn(result);

    mockMvc
        .perform(
            put(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(USER_ID.toString()))
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(provisionPlatformUserUseCase)
        .provision(new ProvisionPlatformUserCommand(ISSUER, SUBJECT));
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue(ACCESS_TOKEN)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(SUBJECT)
        .claim("scope", "profile email")
        .claim("realm_access", Map.of("roles", List.of("user")))
        .build();
  }
}
