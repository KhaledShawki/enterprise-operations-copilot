package io.github.khaledshawki.eoc.platform.copilot.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.copilot.llm")
public class CopilotLlmProperties {
  private static final int MAX_CHAR_LIMIT = 1_048_576;
  private static final Duration MAX_MODEL_CALL_TIMEOUT = Duration.ofMinutes(2);

  private int maxToolArgumentsChars = 4_096;
  private int maxToolResultChars = 32_768;
  private int maxModelInputChars = 98_304;
  private int maxModelResponseChars = 4_096;
  private Duration modelCallTimeout = Duration.ofSeconds(30);
  private int maxConcurrentModelCalls = 16;

  public int getMaxToolArgumentsChars() {
    return maxToolArgumentsChars;
  }

  public void setMaxToolArgumentsChars(int maxToolArgumentsChars) {
    this.maxToolArgumentsChars = requireCharLimit(maxToolArgumentsChars, "tool argument");
  }

  public int getMaxToolResultChars() {
    return maxToolResultChars;
  }

  public void setMaxToolResultChars(int maxToolResultChars) {
    this.maxToolResultChars = requireCharLimit(maxToolResultChars, "tool result");
  }

  public int getMaxModelInputChars() {
    return maxModelInputChars;
  }

  public void setMaxModelInputChars(int maxModelInputChars) {
    this.maxModelInputChars = requireCharLimit(maxModelInputChars, "model input");
  }

  public int getMaxModelResponseChars() {
    return maxModelResponseChars;
  }

  public void setMaxModelResponseChars(int maxModelResponseChars) {
    this.maxModelResponseChars = requireCharLimit(maxModelResponseChars, "model response");
  }

  public Duration getModelCallTimeout() {
    return modelCallTimeout;
  }

  public void setModelCallTimeout(Duration modelCallTimeout) {
    Objects.requireNonNull(modelCallTimeout, "Copilot LLM model call timeout cannot be null");
    if (modelCallTimeout.isZero()
        || modelCallTimeout.isNegative()
        || modelCallTimeout.compareTo(MAX_MODEL_CALL_TIMEOUT) > 0) {
      throw new IllegalArgumentException(
          "Copilot LLM model call timeout must be positive and at most two minutes");
    }
    this.modelCallTimeout = modelCallTimeout;
  }

  public int getMaxConcurrentModelCalls() {
    return maxConcurrentModelCalls;
  }

  public void setMaxConcurrentModelCalls(int maxConcurrentModelCalls) {
    if (maxConcurrentModelCalls < 1 || maxConcurrentModelCalls > 128) {
      throw new IllegalArgumentException(
          "Copilot LLM maximum concurrent model calls must be between 1 and 128");
    }
    this.maxConcurrentModelCalls = maxConcurrentModelCalls;
  }

  private static int requireCharLimit(int value, String description) {
    if (value < 1 || value > MAX_CHAR_LIMIT) {
      throw new IllegalArgumentException(
          "Copilot LLM "
              + description
              + " character limit must be between 1 and "
              + MAX_CHAR_LIMIT);
    }
    return value;
  }
}
