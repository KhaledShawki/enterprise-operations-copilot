package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CopilotMcpExecutionContextFactory {

  private final JwtAuthenticatedUserMapper authenticatedUserMapper;

  public CopilotMcpExecutionContextFactory(JwtAuthenticatedUserMapper authenticatedUserMapper) {
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");
  }

  public CopilotExecutionContext create(McpTransportContext transportContext) {
    Objects.requireNonNull(transportContext, "MCP transport context cannot be null");

    Object authenticationValue =
        transportContext.get(CopilotMcpTransportContextExtractor.AUTHENTICATION_CONTEXT_KEY);
    if (!(authenticationValue instanceof JwtAuthenticationToken authentication)
        || !authentication.isAuthenticated()) {
      throw CopilotMcpToolException.invalidContext(
          "Authenticated MCP execution context is required");
    }

    AuthenticatedUser authenticatedUser;
    try {
      authenticatedUser = authenticatedUserMapper.map(authentication);
    } catch (RuntimeException exception) {
      throw CopilotMcpToolException.invalidContext(
          "Authenticated MCP execution context is invalid", exception);
    }

    Object tenantValue =
        transportContext.get(CopilotMcpTransportContextExtractor.TENANT_CONTEXT_KEY);
    if (!(tenantValue instanceof String tenantText) || tenantText.isBlank()) {
      throw CopilotMcpToolException.invalidContext(
          "MCP tenant context is required in the "
              + CopilotMcpTransportContextExtractor.TENANT_HEADER
              + " header");
    }

    UUID tenantId;
    try {
      tenantId = UUID.fromString(tenantText.strip());
    } catch (IllegalArgumentException exception) {
      throw CopilotMcpToolException.invalidContext("MCP tenant context must be a UUID", exception);
    }

    return new CopilotExecutionContext(
        authenticatedUser.issuer(), authenticatedUser.subject(), tenantId);
  }
}
