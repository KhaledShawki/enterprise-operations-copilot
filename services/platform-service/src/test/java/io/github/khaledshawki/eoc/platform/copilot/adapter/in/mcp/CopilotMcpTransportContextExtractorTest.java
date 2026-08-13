package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

class CopilotMcpTransportContextExtractorTest {

  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final CopilotMcpTransportContextExtractor extractor =
      new CopilotMcpTransportContextExtractor();

  @Test
  void extractsAuthenticatedJwtAndSelectedTenant() {
    ServerRequest request = mock(ServerRequest.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    JwtAuthenticationToken authentication = authentication();

    when(request.principal()).thenReturn(Optional.of(authentication));
    when(request.servletRequest()).thenReturn(servletRequest);
    when(servletRequest.getHeader(CopilotMcpTransportContextExtractor.TENANT_HEADER))
        .thenReturn(TENANT_ID.toString());

    var context = extractor.extract(request);

    assertEquals(
        authentication,
        context.get(CopilotMcpTransportContextExtractor.AUTHENTICATION_CONTEXT_KEY));
    assertEquals(
        TENANT_ID.toString(), context.get(CopilotMcpTransportContextExtractor.TENANT_CONTEXT_KEY));
  }

  @Test
  void omitsBlankTenantAndNonJwtPrincipal() {
    ServerRequest request = mock(ServerRequest.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    Principal principal = () -> "not-a-jwt";

    when(request.principal()).thenReturn(Optional.of(principal));
    when(request.servletRequest()).thenReturn(servletRequest);
    when(servletRequest.getHeader(CopilotMcpTransportContextExtractor.TENANT_HEADER))
        .thenReturn("   ");

    var context = extractor.extract(request);

    assertNull(context.get(CopilotMcpTransportContextExtractor.AUTHENTICATION_CONTEXT_KEY));
    assertNull(context.get(CopilotMcpTransportContextExtractor.TENANT_CONTEXT_KEY));
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("mcp-context-test-token")
            .header("alg", "none")
            .issuer(ISSUER.toString())
            .subject("mcp-user")
            .build();
    return new JwtAuthenticationToken(jwt, List.of());
  }
}
