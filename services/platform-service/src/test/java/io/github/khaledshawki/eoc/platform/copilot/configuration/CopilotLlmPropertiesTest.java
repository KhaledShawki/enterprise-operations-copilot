package io.github.khaledshawki.eoc.platform.copilot.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CopilotLlmPropertiesTest {

  @Test
  void usesBoundedSafeDefaults() {
    var properties = new CopilotLlmProperties();

    assertEquals(4_096, properties.getMaxToolArgumentsChars());
    assertEquals(32_768, properties.getMaxToolResultChars());
    assertEquals(98_304, properties.getMaxModelInputChars());
    assertEquals(4_096, properties.getMaxModelResponseChars());
    assertEquals(Duration.ofSeconds(30), properties.getModelCallTimeout());
    assertEquals(16, properties.getMaxConcurrentModelCalls());
  }

  @Test
  void rejectsUnboundedOrInvalidLimits() {
    var properties = new CopilotLlmProperties();

    assertThrows(IllegalArgumentException.class, () -> properties.setMaxToolArgumentsChars(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxToolResultChars(1_048_577));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxModelInputChars(1_048_577));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxModelResponseChars(-1));
    assertThrows(
        IllegalArgumentException.class, () -> properties.setModelCallTimeout(Duration.ZERO));
    assertThrows(
        IllegalArgumentException.class,
        () -> properties.setModelCallTimeout(Duration.ofMinutes(2).plusMillis(1)));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxConcurrentModelCalls(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxConcurrentModelCalls(129));
  }
}
