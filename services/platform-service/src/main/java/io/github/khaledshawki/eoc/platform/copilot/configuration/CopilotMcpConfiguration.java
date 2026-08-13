package io.github.khaledshawki.eoc.platform.copilot.configuration;

import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpExecutionContextFactory;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpToolAdapter;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpTransportContextExtractor;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import java.util.List;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CopilotMcpProperties.class)
@ConditionalOnBooleanProperty(prefix = "eoc.copilot.mcp", name = "enabled")
public class CopilotMcpConfiguration {

  private static final String MCP_ENDPOINT = "/mcp";

  @Bean
  CopilotMcpTransportContextExtractor copilotMcpTransportContextExtractor() {
    return new CopilotMcpTransportContextExtractor();
  }

  @Bean
  CopilotMcpExecutionContextFactory copilotMcpExecutionContextFactory(
      JwtAuthenticatedUserMapper authenticatedUserMapper) {
    return new CopilotMcpExecutionContextFactory(authenticatedUserMapper);
  }

  @Bean
  CopilotMcpToolAdapter copilotMcpToolAdapter(
      ExecuteCopilotToolUseCase executeCopilotToolUseCase,
      CopilotMcpExecutionContextFactory executionContextFactory) {
    return new CopilotMcpToolAdapter(executeCopilotToolUseCase, executionContextFactory);
  }

  @Bean
  List<SyncToolSpecification> copilotMcpToolSpecifications(CopilotMcpToolAdapter toolAdapter) {
    return SyncMcpAnnotationProviders.statelessToolSpecifications(List.of(toolAdapter));
  }

  @Bean
  WebMvcStatelessServerTransport copilotMcpServerTransport(
      @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
      CopilotMcpTransportContextExtractor contextExtractor,
      CopilotMcpProperties properties) {
    DefaultServerTransportSecurityValidator securityValidator =
        DefaultServerTransportSecurityValidator.builder()
            .allowedHosts(properties.getAllowedHosts())
            .allowedOrigins(properties.getAllowedOrigins())
            .build();

    return WebMvcStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .messageEndpoint(MCP_ENDPOINT)
        .contextExtractor(contextExtractor)
        .securityValidator(securityValidator)
        .build();
  }
}
