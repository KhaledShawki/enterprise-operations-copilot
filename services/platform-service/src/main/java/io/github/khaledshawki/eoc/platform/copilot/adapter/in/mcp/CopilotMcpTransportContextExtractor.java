package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

public final class CopilotMcpTransportContextExtractor
    implements McpTransportContextExtractor<ServerRequest> {

  public static final String TENANT_HEADER = "X-EOC-Tenant-Id";

  static final String AUTHENTICATION_CONTEXT_KEY = "eoc.copilot.authentication";
  static final String TENANT_CONTEXT_KEY = "eoc.copilot.tenant";

  @Override
  public McpTransportContext extract(ServerRequest request) {
    Objects.requireNonNull(request, "MCP server request cannot be null");

    Map<String, Object> values = new HashMap<>();

    request
        .principal()
        .filter(JwtAuthenticationToken.class::isInstance)
        .map(JwtAuthenticationToken.class::cast)
        .filter(authentication -> authentication.isAuthenticated())
        .ifPresent(authentication -> values.put(AUTHENTICATION_CONTEXT_KEY, authentication));

    String tenantHeader = request.servletRequest().getHeader(TENANT_HEADER);
    if (tenantHeader != null && !tenantHeader.isBlank()) {
      values.put(TENANT_CONTEXT_KEY, tenantHeader.strip());
    }

    return values.isEmpty()
        ? McpTransportContext.EMPTY
        : McpTransportContext.create(Map.copyOf(values));
  }
}
