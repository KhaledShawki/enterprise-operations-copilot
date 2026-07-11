// Copyright (c) 2026 Khaled Shawki.
// Licensed under the MIT License.

package io.github.khaledshawki.eoc.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlatformServiceApplicationTests {

  @Test
  void contextLoads() {}
}
