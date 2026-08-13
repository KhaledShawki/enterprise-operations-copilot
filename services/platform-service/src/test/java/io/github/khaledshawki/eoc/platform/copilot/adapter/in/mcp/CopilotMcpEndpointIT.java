package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "eoc.copilot.mcp.enabled=true",
      "spring.ai.mcp.server.enabled=true",
      "spring.ai.mcp.server.protocol=STATELESS"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CopilotMcpEndpointIT {

  private static final String MCP_ENDPOINT = "/mcp";
  private static final String INITIALIZE_REQUEST =
      """
      {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2025-11-25",
          "capabilities": {},
          "clientInfo": {
            "name": "eoc-mcp-it",
            "version": "1.0.0"
          }
        }
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Test
  void requiresAuthenticationForMcpTransport() throws Exception {
    mockMvc.perform(mcpRequest()).andExpect(status().isUnauthorized());
  }

  @Test
  void initializesAuthenticatedStatelessMcpServer() throws Exception {
    mockMvc
        .perform(mcpRequest().with(jwt()).header("Host", "localhost"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.serverInfo.name").value("enterprise-operations-copilot"))
        .andExpect(jsonPath("$.result.capabilities.tools").exists());
  }

  @Test
  void rejectsDisallowedOriginBeforeProtocolHandling() throws Exception {
    mockMvc
        .perform(
            mcpRequest()
                .with(jwt())
                .header("Host", "localhost")
                .header("Origin", "https://malicious.example.com"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsDisallowedHostBeforeProtocolHandling() throws Exception {
    mockMvc
        .perform(mcpRequest().with(jwt()).header("Host", "malicious.example.com"))
        .andExpect(status().is(421));
  }

  private static MockHttpServletRequestBuilder mcpRequest() {
    return post(MCP_ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .content(INITIALIZE_REQUEST);
  }
}
