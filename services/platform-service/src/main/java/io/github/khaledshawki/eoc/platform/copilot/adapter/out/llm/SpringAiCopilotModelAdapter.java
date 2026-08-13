package io.github.khaledshawki.eoc.platform.copilot.adapter.out.llm;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelRequest;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelTurn;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolObservation;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotModelPort;
import io.github.khaledshawki.eoc.platform.copilot.configuration.CopilotLlmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import tools.jackson.databind.json.JsonMapper;

public final class SpringAiCopilotModelAdapter implements CopilotModelPort {
  private static final int TASK_QUEUED = 0;
  private static final int TASK_RUNNING = 1;
  private static final int TASK_DONE = 2;

  private static final String SYSTEM_PROMPT =
      """
      You are the planning model for Enterprise Operations Copilot.

      Security and grounding rules are mandatory:
      - Use only the tools supplied with this request. Never request another tool name.
      - Tool arguments never contain tenantId or businessDate. Tenant scope and business date are application-controlled, not model-controlled.
      - Never invent invoice amounts, balances, currencies, statuses, dates, customers, counts, aging values, identifiers, or other business facts.
      - Business facts may come only from tool results returned in this conversation.
      - Never attempt to choose or override the business date. The application injects an explicit selected date or resolves it deterministically through its Clock.
      - Do not request the exact same tool with the exact same arguments more than once.
      - Do not request write operations, raw SQL, URLs, repository access, or arbitrary external actions.

      When more business data is required, return tool calls.
      When enough evidence is available, do not write the business answer yourself. Return only one JSON object with exactly this field:
      {
        "groundingToolCallIds": ["id-of-executed-tool-call"]
      }
      Select only the executed tool results needed to answer the user's question. The application renders all business facts deterministically from those typed results.
      groundingToolCallIds must contain between one and three tool call IDs that were actually executed and returned in this conversation.
      Do not wrap the final JSON in Markdown or add other text.
      """;

  private final ChatModel chatModel;
  private final CopilotLlmJsonCodec codec;
  private final CopilotLlmProperties properties;
  private final ExecutorService modelExecutor;
  private final Semaphore modelCallPermits;

  public SpringAiCopilotModelAdapter(
      ChatModel chatModel,
      JsonMapper jsonMapper,
      CopilotLlmProperties properties,
      ExecutorService modelExecutor) {
    this.chatModel = Objects.requireNonNull(chatModel, "Spring AI chat model cannot be null");
    this.codec = new CopilotLlmJsonCodec(jsonMapper, properties);
    this.properties = Objects.requireNonNull(properties, "Copilot LLM properties cannot be null");
    this.modelExecutor =
        Objects.requireNonNull(modelExecutor, "Copilot LLM model executor cannot be null");
    this.modelCallPermits = new Semaphore(properties.getMaxConcurrentModelCalls());
  }

  @Override
  public CopilotModelResponse generate(CopilotModelRequest request) {
    Objects.requireNonNull(request, "Copilot model request cannot be null");

    ToolCallingChatOptions options =
        ToolCallingChatOptions.builder()
            .toolCallbacks(CopilotLlmToolCallbacks.approvedTools())
            .build();
    Prompt prompt = new Prompt(messages(request), options);
    ChatResponse response = callModel(prompt);

    if (response == null || response.getResults().size() != 1) {
      throw new CopilotModelProtocolException();
    }
    Generation generation = response.getResult();
    if (generation == null || generation.getOutput() == null) {
      throw new CopilotModelProtocolException();
    }

    AssistantMessage output = generation.getOutput();
    if (output.hasToolCalls()) {
      if ((output.getText() != null && !output.getText().isBlank())
          || output.getToolCalls().size() > CopilotModelResponse.ToolCalls.MAX_TOOL_CALLS) {
        throw new CopilotModelProtocolException();
      }
      return new CopilotModelResponse.ToolCalls(
          output.getToolCalls().stream().map(codec::decodeToolCall).toList());
    }

    return codec.decodeAnswer(output.getText());
  }

  private ChatResponse callModel(Prompt prompt) {
    if (!modelCallPermits.tryAcquire()) {
      throw new CopilotModelUnavailableException(
          new RejectedExecutionException("Copilot LLM concurrency limit reached"));
    }

    AtomicInteger taskState = new AtomicInteger(TASK_QUEUED);
    AtomicBoolean permitReleased = new AtomicBoolean();
    Runnable releasePermit =
        () -> {
          if (permitReleased.compareAndSet(false, true)) {
            modelCallPermits.release();
          }
        };

    Future<ChatResponse> future;
    try {
      future =
          modelExecutor.submit(
              () -> {
                if (!taskState.compareAndSet(TASK_QUEUED, TASK_RUNNING)) {
                  throw new IllegalStateException(
                      "Copilot LLM model task was cancelled before start");
                }
                try {
                  return chatModel.call(prompt);
                } finally {
                  taskState.set(TASK_DONE);
                  releasePermit.run();
                }
              });
    } catch (RejectedExecutionException exception) {
      taskState.set(TASK_DONE);
      releasePermit.run();
      throw new CopilotModelUnavailableException(exception);
    }

    try {
      return future.get(properties.getModelCallTimeout().toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      cancel(future, taskState, releasePermit);
      throw new CopilotModelUnavailableException(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      cancel(future, taskState, releasePermit);
      throw new CopilotModelUnavailableException(exception);
    } catch (ExecutionException exception) {
      throw new CopilotModelUnavailableException(exception.getCause());
    }
  }

  private static void cancel(Future<?> future, AtomicInteger taskState, Runnable releasePermit) {
    if (future.cancel(true) && taskState.compareAndSet(TASK_QUEUED, TASK_DONE)) {
      releasePermit.run();
    }
  }

  private List<Message> messages(CopilotModelRequest request) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(SYSTEM_PROMPT));
    messages.add(new UserMessage(request.question().text()));

    long modelInputChars =
        (long) SYSTEM_PROMPT.length()
            + request.question().text().length()
            + CopilotLlmToolCallbacks.approvedToolDefinitionChars();
    requireModelInputWithinBudget(modelInputChars);

    for (CopilotModelTurn turn : request.completedTurns()) {
      List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>(turn.observations().size());
      List<ToolResponseMessage.ToolResponse> responses =
          new ArrayList<>(turn.observations().size());
      for (CopilotToolObservation observation : turn.observations()) {
        String toolName = observation.toolName().contractName();
        String arguments = codec.encodeToolArguments(observation.toolCall());
        String result = codec.encodeToolResult(observation);
        modelInputChars +=
            (long) observation.callId().length()
                + toolName.length()
                + arguments.length()
                + result.length();
        requireModelInputWithinBudget(modelInputChars);
        toolCalls.add(
            new AssistantMessage.ToolCall(observation.callId(), "function", toolName, arguments));
        responses.add(new ToolResponseMessage.ToolResponse(observation.callId(), toolName, result));
      }
      messages.add(AssistantMessage.builder().toolCalls(List.copyOf(toolCalls)).build());
      messages.add(ToolResponseMessage.builder().responses(List.copyOf(responses)).build());
    }
    return List.copyOf(messages);
  }

  private void requireModelInputWithinBudget(long modelInputChars) {
    if (modelInputChars > properties.getMaxModelInputChars()) {
      throw new CopilotOrchestrationLimitExceededException();
    }
  }
}
