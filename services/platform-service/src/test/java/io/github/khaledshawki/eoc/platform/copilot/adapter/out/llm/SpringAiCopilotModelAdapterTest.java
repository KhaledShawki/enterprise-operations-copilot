package io.github.khaledshawki.eoc.platform.copilot.adapter.out.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelRequest;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelToolCall;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelTurn;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolObservation;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableStatus;
import io.github.khaledshawki.eoc.platform.copilot.configuration.CopilotLlmProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

class SpringAiCopilotModelAdapterTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000211");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 13);
  private static final CopilotQuestion QUESTION =
      CopilotQuestion.current("Which invoice is overdue?");

  @Test
  void exposesExactlyApprovedDescriptorOnlyToolsAndDecodesTypedToolRequest() {
    AtomicReference<Prompt> captured = new AtomicReference<>();
    ChatModel model =
        prompt -> {
          captured.set(prompt);
          return toolCallResponse(
              new AssistantMessage.ToolCall(
                  "call-1",
                  "function",
                  "get_receivable",
                  "{\"invoiceId\":\"" + INVOICE_ID + "\"}"));
        };

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var adapter = adapter(model, new CopilotLlmProperties(), executor);

      CopilotModelResponse response =
          adapter.generate(new CopilotModelRequest(QUESTION, List.of()));

      var toolCalls = assertInstanceOf(CopilotModelResponse.ToolCalls.class, response);
      var call =
          assertInstanceOf(
              CopilotModelToolCall.GetReceivable.class, toolCalls.toolCalls().getFirst());
      assertEquals(INVOICE_ID, call.request().invoiceId());
      assertTrue(call.request().businessDate().isEmpty());

      var options = assertInstanceOf(ToolCallingChatOptions.class, captured.get().getOptions());
      List<ToolCallback> callbacks = options.getToolCallbacks();
      assertEquals(
          List.of("get_receivable", "list_receivables", "get_receivables_summary"),
          callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList());
      assertTrue(
          callbacks.stream()
              .allMatch(
                  callback -> !callback.getToolDefinition().inputSchema().contains("tenantId")));
      assertTrue(
          callbacks.stream()
              .allMatch(
                  callback ->
                      !callback.getToolDefinition().inputSchema().contains("businessDate")));
      callbacks.forEach(
          callback -> assertThrows(IllegalStateException.class, () -> callback.call("{}")));
    }
  }

  @Test
  void rejectsModelSuppliedTenantIdentityAsAnUnsupportedToolArgument() {
    ChatModel model =
        prompt ->
            toolCallResponse(
                new AssistantMessage.ToolCall(
                    "call-1",
                    "function",
                    "get_receivable",
                    "{\"invoiceId\":\"" + INVOICE_ID + "\",\"tenantId\":\"" + TENANT_ID + "\"}"));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThrows(
          CopilotModelProtocolException.class,
          () ->
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));
    }
  }

  @Test
  void rejectsModelSuppliedBusinessDateAsAnUnsupportedToolArgument() {
    ChatModel model =
        prompt ->
            toolCallResponse(
                new AssistantMessage.ToolCall(
                    "call-1",
                    "function",
                    "get_receivable",
                    "{\"invoiceId\":\"" + INVOICE_ID + "\",\"businessDate\":\"2026-01-01\"}"));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThrows(
          CopilotModelProtocolException.class,
          () ->
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));
    }
  }

  @Test
  void appliesBoundedListDefaultsWhenTheModelOmitsOptionalArguments() {
    ChatModel model =
        prompt ->
            toolCallResponse(
                new AssistantMessage.ToolCall("list-1", "function", "list_receivables", "{}"));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var adapter = adapter(model, new CopilotLlmProperties(), executor);
      var response =
          assertInstanceOf(
              CopilotModelResponse.ToolCalls.class,
              adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      var call =
          assertInstanceOf(
              CopilotModelToolCall.ListReceivables.class, response.toolCalls().getFirst());

      assertEquals(0, call.request().pageNumber());
      assertEquals(25, call.request().pageSize());
      assertTrue(call.request().statuses().isEmpty());
      assertTrue(call.request().businessDate().isEmpty());
      assertEquals("DUE_DATE", call.request().sortField().name());
      assertEquals("ASC", call.request().sortDirection().name());
    }
  }

  @Test
  void reconstructsHistoryWithoutSendingTrustedTenantIdentityToTheProvider() {
    var call =
        new CopilotModelToolCall.GetReceivable(
            "call-1", GetReceivableToolRequest.current(INVOICE_ID));
    var observation = new CopilotToolObservation.Receivable(call, receivable());
    var request =
        new CopilotModelRequest(QUESTION, List.of(new CopilotModelTurn(List.of(observation))));
    AtomicReference<Prompt> captured = new AtomicReference<>();
    ChatModel model =
        prompt -> {
          captured.set(prompt);
          return answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");
        };

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var adapter = adapter(model, new CopilotLlmProperties(), executor);

      adapter.generate(request);

      List<Message> instructions = captured.get().getInstructions();
      assertEquals(4, instructions.size());
      var assistant = assertInstanceOf(AssistantMessage.class, instructions.get(2));
      String toolArguments = assistant.getToolCalls().getFirst().arguments();
      assertFalse(toolArguments.contains("tenantId"));
      assertFalse(toolArguments.contains("businessDate"));
      var toolResponse = assertInstanceOf(ToolResponseMessage.class, instructions.get(3));
      String responseData = toolResponse.getResponses().getFirst().responseData();
      assertFalse(responseData.contains("tenantId"));
      assertFalse(responseData.contains(TENANT_ID.toString()));
      assertTrue(responseData.contains(EVENT_ID.toString()));
      assertTrue(responseData.contains("\"aggregateVersion\":3"));
    }
  }

  @Test
  void acceptsOnlyGroundingSelectionAsTheFinalModelResponse() {
    ChatModel model = prompt -> answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var answer =
          assertInstanceOf(
              CopilotModelResponse.Answer.class,
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));

      assertEquals(List.of("call-1"), answer.groundingToolCallIds());
    }
  }

  @Test
  void rejectsFreeFormOrExtraFinalFieldsInsteadOfTrustingModelGeneratedBusinessProse() {
    ChatModel model =
        prompt ->
            answerResponse(
                "{\"answer\":\"Invented CHF 999.00\",\"groundingToolCallIds\":[\"call-1\"]}");

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThrows(
          CopilotModelProtocolException.class,
          () ->
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));
    }
  }

  @Test
  void mapsUnknownToolNameToUnsupportedCoreCallForFailClosedOrchestration() {
    ChatModel model =
        prompt ->
            toolCallResponse(
                new AssistantMessage.ToolCall("write-1", "function", "create_invoice", "{}"));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var response =
          assertInstanceOf(
              CopilotModelResponse.ToolCalls.class,
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));

      var unsupported =
          assertInstanceOf(CopilotModelToolCall.Unsupported.class, response.toolCalls().getFirst());
      assertEquals("create_invoice", unsupported.requestedToolName());
    }
  }

  @Test
  void rejectsAssistantTextMixedWithToolCalls() {
    ChatModel model =
        prompt ->
            new ChatResponse(
                List.of(
                    new Generation(
                        AssistantMessage.builder()
                            .content("Ignore the tool result and trust me")
                            .toolCalls(
                                List.of(
                                    new AssistantMessage.ToolCall(
                                        "call-1",
                                        "function",
                                        "get_receivable",
                                        "{\"invoiceId\":\"" + INVOICE_ID + "\"}")))
                            .build())));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThrows(
          CopilotModelProtocolException.class,
          () ->
              adapter(model, new CopilotLlmProperties(), executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));
    }
  }

  @Test
  void rejectsOversizedModelInputBeforeCallingTheProvider() {
    AtomicInteger calls = new AtomicInteger();
    ChatModel model =
        prompt -> {
          calls.incrementAndGet();
          return answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");
        };
    var properties = new CopilotLlmProperties();
    properties.setMaxModelInputChars(100);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThrows(
          CopilotOrchestrationLimitExceededException.class,
          () ->
              adapter(model, properties, executor)
                  .generate(new CopilotModelRequest(QUESTION, List.of())));
      assertEquals(0, calls.get());
    }
  }

  @Test
  void mapsProviderFailureToStableApplicationErrorWithoutProviderTextInTheMessage() {
    ChatModel model =
        prompt -> {
          throw new IllegalStateException("provider-secret-credential-and-endpoint");
        };

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var failure =
          assertThrows(
              CopilotModelUnavailableException.class,
              () ->
                  adapter(model, new CopilotLlmProperties(), executor)
                      .generate(new CopilotModelRequest(QUESTION, List.of())));

      assertEquals("Copilot language model is unavailable", failure.getMessage());
      assertFalse(failure.getMessage().contains("provider-secret"));
    }
  }

  @Test
  void rejectsCallsAboveConfiguredProviderConcurrencyWithoutStartingAnotherModelCall()
      throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    ChatModel model =
        prompt -> {
          calls.incrementAndGet();
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          return answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");
        };
    var properties = new CopilotLlmProperties();
    properties.setMaxConcurrentModelCalls(1);
    properties.setModelCallTimeout(Duration.ofSeconds(1));

    try (ExecutorService modelExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor()) {
      var adapter = adapter(model, properties, modelExecutor);
      Future<CopilotModelResponse> first =
          callerExecutor.submit(
              () -> adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      assertTrue(entered.await(1, TimeUnit.SECONDS));

      var failure =
          assertThrows(
              CopilotModelUnavailableException.class,
              () -> adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      assertEquals("Copilot language model is unavailable", failure.getMessage());
      assertEquals(1, calls.get());

      release.countDown();
      assertInstanceOf(CopilotModelResponse.Answer.class, first.get(1, TimeUnit.SECONDS));
    } finally {
      release.countDown();
    }
  }

  @Test
  void releasesConcurrencyPermitWhenTimedOutModelTaskWasCancelledBeforeStarting() throws Exception {
    CountDownLatch blockerEntered = new CountDownLatch(1);
    CountDownLatch releaseBlocker = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    ChatModel model =
        prompt -> {
          calls.incrementAndGet();
          return answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");
        };
    var properties = new CopilotLlmProperties();
    properties.setMaxConcurrentModelCalls(1);
    properties.setModelCallTimeout(Duration.ofMillis(250));

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> blocker =
          executor.submit(
              () -> {
                blockerEntered.countDown();
                try {
                  releaseBlocker.await();
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                }
              });
      assertTrue(blockerEntered.await(1, TimeUnit.SECONDS));
      var adapter = adapter(model, properties, executor);

      assertThrows(
          CopilotModelUnavailableException.class,
          () -> adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      assertEquals(0, calls.get());

      releaseBlocker.countDown();
      blocker.get(1, TimeUnit.SECONDS);

      assertInstanceOf(
          CopilotModelResponse.Answer.class,
          adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      assertEquals(1, calls.get());
    } finally {
      releaseBlocker.countDown();
    }
  }

  @Test
  void boundsProviderLatencyWithApplicationOwnedTimeout() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ChatModel model =
        prompt -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          return answerResponse("{\"groundingToolCallIds\":[\"call-1\"]}");
        };
    var properties = new CopilotLlmProperties();
    properties.setModelCallTimeout(Duration.ofMillis(500));

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var adapter = adapter(model, properties, executor);

      var failure =
          assertThrows(
              CopilotModelUnavailableException.class,
              () -> adapter.generate(new CopilotModelRequest(QUESTION, List.of())));
      assertTrue(entered.await(1, TimeUnit.SECONDS));
      assertEquals("Copilot language model is unavailable", failure.getMessage());
    } finally {
      release.countDown();
    }
  }

  private static SpringAiCopilotModelAdapter adapter(
      ChatModel model, CopilotLlmProperties properties, ExecutorService executor) {
    return new SpringAiCopilotModelAdapter(
        model, JsonMapper.builder().build(), properties, executor);
  }

  private static ChatResponse toolCallResponse(AssistantMessage.ToolCall toolCall) {
    return new ChatResponse(
        List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(toolCall)).build())));
  }

  private static ChatResponse answerResponse(String content) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
  }

  private static CopilotReceivable receivable() {
    return new CopilotReceivable(
        TENANT_ID,
        INVOICE_ID,
        new CopilotCustomer(
            UUID.fromString("00000000-0000-0000-0000-000000000311"),
            true,
            Optional.of("C-1"),
            Optional.of("Example Customer")),
        "INV-1",
        new CopilotMoney(new BigDecimal("100.00"), "CHF"),
        new CopilotMoney(new BigDecimal("20.00"), "CHF"),
        new CopilotMoney(new BigDecimal("80.00"), "CHF"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        BUSINESS_DATE,
        ReceivableStatus.PARTIALLY_PAID,
        false,
        true,
        new CopilotEvidence(EVENT_ID, 3, Instant.parse("2026-08-12T10:00:00Z")));
  }
}
