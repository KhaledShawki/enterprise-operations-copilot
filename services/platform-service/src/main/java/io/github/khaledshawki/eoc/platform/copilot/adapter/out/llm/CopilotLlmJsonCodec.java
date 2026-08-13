package io.github.khaledshawki.eoc.platform.copilot.adapter.out.llm;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelToolCall;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivableCurrencySummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolObservation;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableSortField;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableStatus;
import io.github.khaledshawki.eoc.copilot.application.model.SortDirection;
import io.github.khaledshawki.eoc.platform.copilot.configuration.CopilotLlmProperties;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.messages.AssistantMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CopilotLlmJsonCodec {
  private static final Set<String> GET_FIELDS = Set.of("invoiceId");
  private static final Set<String> LIST_FIELDS =
      Set.of(
          "customerId",
          "statuses",
          "overdue",
          "pageNumber",
          "pageSize",
          "sortField",
          "sortDirection");
  private static final Set<String> SUMMARY_FIELDS = Set.of();
  private static final Set<String> ANSWER_FIELDS = Set.of("groundingToolCallIds");

  private final JsonMapper jsonMapper;
  private final CopilotLlmProperties properties;

  CopilotLlmJsonCodec(JsonMapper jsonMapper, CopilotLlmProperties properties) {
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "Copilot LLM JSON mapper cannot be null");
    this.properties = Objects.requireNonNull(properties, "Copilot LLM properties cannot be null");
  }

  CopilotModelToolCall decodeToolCall(AssistantMessage.ToolCall toolCall) {
    if (toolCall == null
        || toolCall.id() == null
        || toolCall.type() == null
        || toolCall.name() == null
        || toolCall.arguments() == null) {
      throw new CopilotModelProtocolException();
    }
    if (!"function".equals(toolCall.type())
        || toolCall.arguments().length() > properties.getMaxToolArgumentsChars()) {
      throw new CopilotModelProtocolException();
    }

    try {
      return switch (toolCall.name()) {
        case "get_receivable" ->
            new CopilotModelToolCall.GetReceivable(
                toolCall.id(), decodeGetReceivable(toolCall.arguments()));
        case "list_receivables" ->
            new CopilotModelToolCall.ListReceivables(
                toolCall.id(), decodeListReceivables(toolCall.arguments()));
        case "get_receivables_summary" ->
            new CopilotModelToolCall.GetReceivablesSummary(
                toolCall.id(), decodeSummary(toolCall.arguments()));
        default -> new CopilotModelToolCall.Unsupported(toolCall.id(), toolCall.name());
      };
    } catch (CopilotModelProtocolException exception) {
      throw exception;
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new CopilotModelProtocolException(exception);
    }
  }

  String encodeToolArguments(CopilotModelToolCall toolCall) {
    Objects.requireNonNull(toolCall, "Copilot model tool call cannot be null");
    Object arguments =
        switch (toolCall) {
          case CopilotModelToolCall.GetReceivable call -> getArguments(call.request());
          case CopilotModelToolCall.ListReceivables call -> listArguments(call.request());
          case CopilotModelToolCall.GetReceivablesSummary ignored -> summaryArguments();
          case CopilotModelToolCall.Unsupported ignored ->
              throw new CopilotModelProtocolException();
        };
    return writeBounded(arguments, properties.getMaxToolArgumentsChars());
  }

  String encodeToolResult(CopilotToolObservation observation) {
    Objects.requireNonNull(observation, "Copilot tool observation cannot be null");
    Object result =
        switch (observation) {
          case CopilotToolObservation.Receivable item -> receivable(item.result());
          case CopilotToolObservation.ReceivablePage page -> receivablePage(page.result());
          case CopilotToolObservation.ReceivablesSummary summary -> summary(summary.result());
        };
    return writeBounded(result, properties.getMaxToolResultChars());
  }

  CopilotModelResponse.Answer decodeAnswer(String rawAnswer) {
    if (rawAnswer == null
        || rawAnswer.isBlank()
        || rawAnswer.length() > properties.getMaxModelResponseChars()) {
      throw new CopilotModelProtocolException();
    }

    try {
      JsonNode root = jsonMapper.readTree(rawAnswer);
      requireExactObject(root, ANSWER_FIELDS);
      JsonNode idsNode = required(root, "groundingToolCallIds");
      if (!idsNode.isArray() || idsNode.size() == 0) {
        throw new IllegalArgumentException("Grounding ids must be a non-empty array");
      }
      LinkedHashSet<String> groundingIds = new LinkedHashSet<>();
      for (JsonNode idNode : idsNode) {
        String id = boundedText(idNode, CopilotModelToolCall.MAX_CALL_ID_LENGTH);
        if (!groundingIds.add(id)) {
          throw new IllegalArgumentException("Grounding ids cannot contain duplicates");
        }
      }
      return new CopilotModelResponse.Answer(List.copyOf(groundingIds));
    } catch (CopilotModelProtocolException exception) {
      throw exception;
    } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
      throw new CopilotModelProtocolException(exception);
    }
  }

  private GetReceivableToolRequest decodeGetReceivable(String arguments) {
    JsonNode root = readObject(arguments, GET_FIELDS);
    requireFields(root, Set.of("invoiceId"));
    return GetReceivableToolRequest.current(UUID.fromString(text(required(root, "invoiceId"))));
  }

  private ListReceivablesToolRequest decodeListReceivables(String arguments) {
    JsonNode root = readObject(arguments, LIST_FIELDS);
    return new ListReceivablesToolRequest(
        optionalUuid(root, "customerId"),
        statuses(root.get("statuses")),
        optionalBoolean(root, "overdue"),
        Optional.empty(),
        optionalInt(root, "pageNumber", 0),
        optionalInt(root, "pageSize", ListReceivablesToolRequest.MAX_PAGE_SIZE),
        optionalEnum(root, "sortField", ReceivableSortField.class, ReceivableSortField.DUE_DATE),
        optionalEnum(root, "sortDirection", SortDirection.class, SortDirection.ASC));
  }

  private GetReceivablesSummaryToolRequest decodeSummary(String arguments) {
    readObject(arguments, SUMMARY_FIELDS);
    return GetReceivablesSummaryToolRequest.current();
  }

  private JsonNode readObject(String json, Set<String> allowedFields) {
    try {
      JsonNode root = jsonMapper.readTree(json);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Tool arguments must be a JSON object");
      }
      Set<String> actualFields = Set.copyOf(root.propertyNames());
      if (!allowedFields.containsAll(actualFields)) {
        throw new IllegalArgumentException("Tool arguments contain unsupported fields");
      }
      return root;
    } catch (JacksonException exception) {
      throw new CopilotModelProtocolException(exception);
    }
  }

  private static void requireFields(JsonNode root, Set<String> requiredFields) {
    if (!Set.copyOf(root.propertyNames()).containsAll(requiredFields)) {
      throw new IllegalArgumentException("Tool arguments are missing required fields");
    }
  }

  private static JsonNode required(JsonNode root, String name) {
    JsonNode node = root.get(name);
    if (node == null || node.isNull()) {
      throw new IllegalArgumentException("Required model field is missing: " + name);
    }
    return node;
  }

  private static String text(JsonNode node) {
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Model field must be text");
    }
    String value = node.textValue();
    if (value.isBlank() || !value.equals(value.strip())) {
      throw new IllegalArgumentException("Model text field must be canonical and nonblank");
    }
    return value;
  }

  private static String boundedText(JsonNode node, int maxLength) {
    String value = text(node);
    if (value.length() > maxLength) {
      throw new IllegalArgumentException("Model text field exceeds its bound");
    }
    return value;
  }

  private static Optional<UUID> optionalUuid(JsonNode root, String name) {
    JsonNode node = root.get(name);
    if (node == null) {
      return Optional.empty();
    }
    if (node.isNull()) {
      throw new IllegalArgumentException("Optional UUID must be omitted rather than null");
    }
    return Optional.of(UUID.fromString(text(node)));
  }

  private static Optional<Boolean> optionalBoolean(JsonNode root, String name) {
    JsonNode node = root.get(name);
    if (node == null) {
      return Optional.empty();
    }
    if (!node.isBoolean()) {
      throw new IllegalArgumentException("Optional boolean field must be boolean");
    }
    return Optional.of(node.booleanValue());
  }

  private static int optionalInt(JsonNode root, String name, int defaultValue) {
    JsonNode node = root.get(name);
    if (node == null) {
      return defaultValue;
    }
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw new IllegalArgumentException("Integer model field is invalid");
    }
    return node.intValue();
  }

  private static Set<ReceivableStatus> statuses(JsonNode node) {
    if (node == null) {
      return Set.of();
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException("Statuses must be an array");
    }
    LinkedHashSet<ReceivableStatus> statuses = new LinkedHashSet<>();
    for (JsonNode statusNode : node) {
      ReceivableStatus status = ReceivableStatus.valueOf(text(statusNode));
      if (!statuses.add(status)) {
        throw new IllegalArgumentException("Statuses cannot contain duplicates");
      }
    }
    return Set.copyOf(statuses);
  }

  private static <T extends Enum<T>> T optionalEnum(
      JsonNode root, String name, Class<T> enumType, T defaultValue) {
    JsonNode node = root.get(name);
    if (node == null) {
      return defaultValue;
    }
    return Enum.valueOf(enumType, text(node));
  }

  private static Map<String, Object> getArguments(GetReceivableToolRequest request) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("invoiceId", request.invoiceId().toString());
    return result;
  }

  private static Map<String, Object> listArguments(ListReceivablesToolRequest request) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    request.customerId().ifPresent(id -> result.put("customerId", id.toString()));
    if (!request.statuses().isEmpty()) {
      result.put("statuses", request.statuses().stream().map(Enum::name).sorted().toList());
    }
    request.overdue().ifPresent(value -> result.put("overdue", value));
    result.put("pageNumber", request.pageNumber());
    result.put("pageSize", request.pageSize());
    result.put("sortField", request.sortField().name());
    result.put("sortDirection", request.sortDirection().name());
    return result;
  }

  private static Map<String, Object> summaryArguments() {
    return Map.of();
  }

  private static Map<String, Object> receivable(CopilotReceivable receivable) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("invoiceId", receivable.invoiceId().toString());
    result.put("customer", customer(receivable.customer()));
    result.put("invoiceNumber", receivable.invoiceNumber());
    result.put("originalAmount", money(receivable.originalAmount()));
    result.put("paidAmount", money(receivable.paidAmount()));
    result.put("outstandingAmount", money(receivable.outstandingAmount()));
    result.put("issueDate", receivable.issueDate().toString());
    result.put("dueDate", receivable.dueDate().toString());
    result.put("businessDate", receivable.businessDate().toString());
    result.put("status", receivable.status().name());
    result.put("cancelled", receivable.cancelled());
    result.put("overdue", receivable.overdue());
    result.put("evidence", evidence(receivable.evidence()));
    return result;
  }

  private static Map<String, Object> receivablePage(CopilotReceivablePage page) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put(
        "receivables", page.receivables().stream().map(CopilotLlmJsonCodec::receivable).toList());
    result.put("pageNumber", page.pageNumber());
    result.put("pageSize", page.pageSize());
    result.put("totalElements", page.totalElements());
    result.put("totalPages", page.totalPages());
    result.put("businessDate", page.businessDate().toString());
    result.put("hasNext", page.hasNext());
    result.put("hasPrevious", page.hasPrevious());
    return result;
  }

  private static Map<String, Object> summary(CopilotReceivablesSummary summary) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("businessDate", summary.businessDate().toString());
    result.put("invoiceCount", summary.invoiceCount());
    result.put("openCount", summary.openCount());
    result.put("overdueCount", summary.overdueCount());
    result.put(
        "currencies",
        summary.currencies().stream().map(CopilotLlmJsonCodec::currencySummary).toList());
    return result;
  }

  private static Map<String, Object> currencySummary(CopilotReceivableCurrencySummary summary) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("currency", summary.currency());
    result.put("invoiceCount", summary.invoiceCount());
    result.put("openCount", summary.openCount());
    result.put("overdueCount", summary.overdueCount());
    result.put("outstandingAmount", money(summary.outstandingAmount()));
    result.put("overdueAmount", money(summary.overdueAmount()));
    result.put("currentAmount", money(summary.currentAmount()));
    result.put("days1To30OverdueAmount", money(summary.days1To30OverdueAmount()));
    result.put("days31To60OverdueAmount", money(summary.days31To60OverdueAmount()));
    result.put("days61To90OverdueAmount", money(summary.days61To90OverdueAmount()));
    result.put("days91PlusOverdueAmount", money(summary.days91PlusOverdueAmount()));
    return result;
  }

  private static Map<String, Object> customer(CopilotCustomer customer) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("customerId", customer.customerId().toString());
    result.put("projected", customer.projected());
    customer.partnerNumber().ifPresent(value -> result.put("partnerNumber", value));
    customer.displayName().ifPresent(value -> result.put("displayName", value));
    return result;
  }

  private static Map<String, Object> money(CopilotMoney money) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("amount", money.amount());
    result.put("currency", money.currency());
    return result;
  }

  private static Map<String, Object> evidence(CopilotEvidence evidence) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("eventId", evidence.eventId().toString());
    result.put("aggregateVersion", evidence.aggregateVersion());
    result.put("occurredAt", evidence.occurredAt().toString());
    return result;
  }

  private String writeBounded(Object value, int maxChars) {
    try {
      String json = jsonMapper.writeValueAsString(value);
      if (json.length() > maxChars) {
        throw new CopilotOrchestrationLimitExceededException();
      }
      return json;
    } catch (CopilotOrchestrationLimitExceededException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new CopilotModelProtocolException(exception);
    }
  }

  private static void requireExactObject(JsonNode node, Set<String> fields) {
    if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(fields)) {
      throw new IllegalArgumentException("Model response object does not match the contract");
    }
  }
}
