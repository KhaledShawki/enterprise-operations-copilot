package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolAccessDeniedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataCorruptedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataNotFoundException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.InvalidCopilotToolArgumentsException;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableSortField;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableStatus;
import io.github.khaledshawki.eoc.copilot.application.model.SortDirection;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpResponses.ReceivablePageResponse;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpResponses.ReceivableResponse;
import io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp.CopilotMcpResponses.ReceivablesSummaryResponse;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

public final class CopilotMcpToolAdapter {

  private final ExecuteCopilotToolUseCase executeCopilotToolUseCase;
  private final CopilotMcpExecutionContextFactory executionContextFactory;

  public CopilotMcpToolAdapter(
      ExecuteCopilotToolUseCase executeCopilotToolUseCase,
      CopilotMcpExecutionContextFactory executionContextFactory) {
    this.executeCopilotToolUseCase =
        Objects.requireNonNull(
            executeCopilotToolUseCase, "Execute Copilot tool use case cannot be null");
    this.executionContextFactory =
        Objects.requireNonNull(
            executionContextFactory, "Copilot MCP execution context factory cannot be null");
  }

  @McpTool(
      name = "get_receivable",
      title = "Get receivable",
      description =
          "Return one tenant-scoped receivable with balances, overdue state, customer data, and source evidence.",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  public ReceivableResponse getReceivable(
      McpTransportContext transportContext,
      @McpToolParam(description = "Invoice UUID.") String invoiceId,
      @McpToolParam(
              required = false,
              description =
                  "Optional ISO-8601 business date (yyyy-MM-dd). Uses the server business date when omitted.")
          String businessDate) {
    return invoke(
        () ->
            CopilotMcpResponses.from(
                executeCopilotToolUseCase.execute(
                    executionContextFactory.create(transportContext),
                    new GetReceivableToolRequest(
                        requiredUuid(invoiceId, "invoiceId"),
                        optionalDate(businessDate, "businessDate")))));
  }

  @McpTool(
      name = "list_receivables",
      title = "List receivables",
      description =
          "List tenant-scoped receivables with bounded filters, paging, sorting, overdue state, and source evidence.",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  public ReceivablePageResponse listReceivables(
      McpTransportContext transportContext,
      @McpToolParam(required = false, description = "Optional customer UUID.") String customerId,
      @McpToolParam(
              required = false,
              description = "Optional statuses: OPEN, PARTIALLY_PAID, PAID, CANCELLED.")
          List<String> statuses,
      @McpToolParam(required = false, description = "Optional overdue filter.") Boolean overdue,
      @McpToolParam(
              required = false,
              description =
                  "Optional ISO-8601 business date (yyyy-MM-dd). Uses the server business date when omitted.")
          String businessDate,
      @McpToolParam(required = false, description = "Zero-based page number. Defaults to 0.")
          Integer pageNumber,
      @McpToolParam(required = false, description = "Page size from 1 to 25. Defaults to 25.")
          Integer pageSize,
      @McpToolParam(
              required = false,
              description =
                  "Sort field: DUE_DATE, ISSUE_DATE, OUTSTANDING_AMOUNT, INVOICE_NUMBER. Defaults to DUE_DATE.")
          String sortField,
      @McpToolParam(required = false, description = "Sort direction: ASC or DESC. Defaults to ASC.")
          String sortDirection) {
    return invoke(
        () ->
            CopilotMcpResponses.from(
                executeCopilotToolUseCase.execute(
                    executionContextFactory.create(transportContext),
                    new ListReceivablesToolRequest(
                        optionalUuid(customerId, "customerId"),
                        statuses(statuses),
                        Optional.ofNullable(overdue),
                        optionalDate(businessDate, "businessDate"),
                        pageNumber == null ? 0 : pageNumber,
                        pageSize == null ? ListReceivablesToolRequest.MAX_PAGE_SIZE : pageSize,
                        enumValue(
                            sortField,
                            ReceivableSortField.class,
                            ReceivableSortField.DUE_DATE,
                            "sortField"),
                        enumValue(
                            sortDirection,
                            SortDirection.class,
                            SortDirection.ASC,
                            "sortDirection")))));
  }

  @McpTool(
      name = "get_receivables_summary",
      title = "Get receivables summary",
      description =
          "Return tenant-scoped receivables counts, currency-separated balances, and aging buckets for a business date.",
      generateOutputSchema = true,
      annotations =
          @McpTool.McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false))
  public ReceivablesSummaryResponse getReceivablesSummary(
      McpTransportContext transportContext,
      @McpToolParam(
              required = false,
              description =
                  "Optional ISO-8601 business date (yyyy-MM-dd). Uses the server business date when omitted.")
          String businessDate) {
    return invoke(
        () ->
            CopilotMcpResponses.from(
                executeCopilotToolUseCase.execute(
                    executionContextFactory.create(transportContext),
                    new GetReceivablesSummaryToolRequest(
                        optionalDate(businessDate, "businessDate")))));
  }

  private static UUID requiredUuid(String rawValue, String fieldName) {
    String value = requiredText(rawValue, fieldName);
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidCopilotToolArgumentsException(fieldName + " must be a UUID");
    }
  }

  private static Optional<UUID> optionalUuid(String rawValue, String fieldName) {
    if (rawValue == null) {
      return Optional.empty();
    }
    return Optional.of(requiredUuid(rawValue, fieldName));
  }

  private static Optional<LocalDate> optionalDate(String rawValue, String fieldName) {
    if (rawValue == null) {
      return Optional.empty();
    }
    String value = requiredText(rawValue, fieldName);
    try {
      return Optional.of(LocalDate.parse(value));
    } catch (DateTimeParseException exception) {
      throw new InvalidCopilotToolArgumentsException(
          fieldName + " must use ISO-8601 yyyy-MM-dd format");
    }
  }

  private static Set<ReceivableStatus> statuses(List<String> rawStatuses) {
    if (rawStatuses == null) {
      return Set.of();
    }
    return rawStatuses.stream()
        .map(status -> enumValue(status, ReceivableStatus.class, null, "statuses"))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static <T extends Enum<T>> T enumValue(
      String rawValue, Class<T> enumType, T defaultValue, String fieldName) {
    if (rawValue == null) {
      return defaultValue;
    }

    String value = requiredText(rawValue, fieldName).toUpperCase(Locale.ROOT);
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidCopilotToolArgumentsException(fieldName + " contains an unsupported value");
    }
  }

  private static String requiredText(String rawValue, String fieldName) {
    if (rawValue == null) {
      throw new InvalidCopilotToolArgumentsException(fieldName + " is required");
    }
    String value = rawValue.strip();
    if (value.isEmpty()) {
      throw new InvalidCopilotToolArgumentsException(fieldName + " cannot be blank");
    }
    return value;
  }

  private static <T> T invoke(Supplier<T> invocation) {
    try {
      return invocation.get();
    } catch (CopilotMcpToolException exception) {
      throw exception;
    } catch (InvalidCopilotToolArgumentsException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.INVALID_ARGUMENTS,
          "Copilot tool arguments are invalid",
          exception);
    } catch (CopilotToolAccessDeniedException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.ACCESS_DENIED, "Copilot tool access is denied", exception);
    } catch (CopilotToolDataNotFoundException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.NOT_FOUND, "Requested Copilot data was not found", exception);
    } catch (CopilotToolDataUnavailableException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.DATA_UNAVAILABLE,
          "Copilot data is temporarily unavailable",
          exception);
    } catch (CopilotToolDataCorruptedException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.DATA_CORRUPTED,
          "Copilot data failed integrity validation",
          exception);
    } catch (RuntimeException exception) {
      throw CopilotMcpToolException.mapped(
          CopilotMcpToolException.INTERNAL_ERROR, "Copilot tool execution failed", exception);
    }
  }
}
