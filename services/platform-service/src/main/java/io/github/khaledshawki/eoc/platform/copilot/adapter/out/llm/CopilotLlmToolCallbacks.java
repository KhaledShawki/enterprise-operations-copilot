package io.github.khaledshawki.eoc.platform.copilot.adapter.out.llm;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

final class CopilotLlmToolCallbacks {
  private static final List<ToolCallback> APPROVED_TOOLS =
      List.of(
          descriptor(
              CopilotToolName.GET_RECEIVABLE,
              "Get one receivable by invoice UUID using the application-selected business date.",
              """
              {
                "type": "object",
                "properties": {
                  "invoiceId": {"type": "string", "format": "uuid"}
                },
                "required": ["invoiceId"],
                "additionalProperties": false
              }
              """),
          descriptor(
              CopilotToolName.LIST_RECEIVABLES,
              "List receivables using bounded filters, paging, and the application-selected business date.",
              """
              {
                "type": "object",
                "properties": {
                  "customerId": {"type": "string", "format": "uuid"},
                  "statuses": {
                    "type": "array",
                    "items": {"type": "string", "enum": ["OPEN", "PARTIALLY_PAID", "PAID", "CANCELLED"]},
                    "uniqueItems": true
                  },
                  "overdue": {"type": "boolean"},
                  "pageNumber": {"type": "integer", "minimum": 0, "maximum": 1000},
                  "pageSize": {"type": "integer", "minimum": 1, "maximum": 25},
                  "sortField": {
                    "type": "string",
                    "enum": ["DUE_DATE", "ISSUE_DATE", "OUTSTANDING_AMOUNT", "INVOICE_NUMBER"]
                  },
                  "sortDirection": {"type": "string", "enum": ["ASC", "DESC"]}
                },
                "additionalProperties": false
              }
              """),
          descriptor(
              CopilotToolName.GET_RECEIVABLES_SUMMARY,
              "Get receivables counts, currency-separated balances, and aging buckets for the application-selected business date.",
              """
              {
                "type": "object",
                "properties": {},
                "additionalProperties": false
              }
              """));

  private CopilotLlmToolCallbacks() {}

  static List<ToolCallback> approvedTools() {
    return APPROVED_TOOLS;
  }

  static int approvedToolDefinitionChars() {
    return APPROVED_TOOLS.stream()
        .map(ToolCallback::getToolDefinition)
        .mapToInt(
            definition ->
                definition.name().length()
                    + definition.description().length()
                    + definition.inputSchema().length())
        .sum();
  }

  private static ToolCallback descriptor(
      CopilotToolName toolName, String description, String inputSchema) {
    ToolDefinition definition =
        ToolDefinition.builder()
            .name(toolName.contractName())
            .description(description)
            .inputSchema(inputSchema)
            .build();
    return new DescriptorOnlyToolCallback(definition);
  }

  private record DescriptorOnlyToolCallback(ToolDefinition definition) implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public String call(String toolInput) {
      throw new IllegalStateException(
          "Copilot LLM tool descriptors must never execute through Spring AI");
    }
  }
}
