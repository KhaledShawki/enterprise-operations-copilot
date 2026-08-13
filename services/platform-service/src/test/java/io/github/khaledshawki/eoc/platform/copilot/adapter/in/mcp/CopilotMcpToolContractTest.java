package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;

class CopilotMcpToolContractTest {

  @Test
  void registersExactlyApprovedToolsAndKeepsTenantOutOfInputSchemas() {
    CopilotMcpToolAdapter adapter =
        new CopilotMcpToolAdapter(
            mock(ExecuteCopilotToolUseCase.class),
            new CopilotMcpExecutionContextFactory(new JwtAuthenticatedUserMapper()));

    var specifications = SyncMcpAnnotationProviders.statelessToolSpecifications(List.of(adapter));

    Set<String> expectedNames =
        Arrays.stream(CopilotToolName.values())
            .map(CopilotToolName::contractName)
            .collect(Collectors.toUnmodifiableSet());
    Set<String> actualNames =
        specifications.stream()
            .map(specification -> specification.tool().name())
            .collect(Collectors.toUnmodifiableSet());

    assertEquals(expectedNames, actualNames);
    assertEquals(3, specifications.size());

    for (var specification : specifications) {
      String inputSchema = specification.tool().inputSchema().toString();
      assertFalse(inputSchema.contains("tenantId"));
      assertFalse(inputSchema.contains("transportContext"));
    }
  }

  @Test
  void declaresEveryMcpToolReadOnlyIdempotentAndClosedWorld() {
    List<Method> toolMethods =
        Arrays.stream(CopilotMcpToolAdapter.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(McpTool.class))
            .toList();

    assertEquals(3, toolMethods.size());
    for (Method toolMethod : toolMethods) {
      McpTool tool = toolMethod.getAnnotation(McpTool.class);
      assertTrue(tool.generateOutputSchema());
      assertTrue(tool.annotations().readOnlyHint());
      assertFalse(tool.annotations().destructiveHint());
      assertTrue(tool.annotations().idempotentHint());
      assertFalse(tool.annotations().openWorldHint());
    }
  }
}
