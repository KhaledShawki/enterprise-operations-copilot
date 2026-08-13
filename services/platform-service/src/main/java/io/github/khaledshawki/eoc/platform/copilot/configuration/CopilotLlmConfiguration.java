package io.github.khaledshawki.eoc.platform.copilot.configuration;

import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotModelPort;
import io.github.khaledshawki.eoc.copilot.application.service.CopilotOrchestrationService;
import io.github.khaledshawki.eoc.platform.copilot.adapter.out.llm.SpringAiCopilotModelAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eoc.copilot.llm.enabled", havingValue = "true")
@EnableConfigurationProperties(CopilotLlmProperties.class)
public class CopilotLlmConfiguration {

  @Bean(destroyMethod = "close")
  ExecutorService copilotLlmModelExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  CopilotModelPort copilotModelPort(
      ChatModel chatModel,
      JsonMapper jsonMapper,
      CopilotLlmProperties properties,
      @Qualifier("copilotLlmModelExecutor") ExecutorService copilotLlmModelExecutor) {
    return new SpringAiCopilotModelAdapter(
        chatModel, jsonMapper, properties, copilotLlmModelExecutor);
  }

  @Bean
  AskCopilotUseCase askCopilotUseCase(
      CopilotModelPort modelPort, ExecuteCopilotToolUseCase executeCopilotToolUseCase) {
    return new CopilotOrchestrationService(modelPort, executeCopilotToolUseCase);
  }
}
