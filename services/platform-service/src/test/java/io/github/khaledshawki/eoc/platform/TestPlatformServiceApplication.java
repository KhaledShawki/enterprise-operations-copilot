// Copyright (c) 2026 Khaled Shawki.
// Licensed under the MIT License.

package io.github.khaledshawki.eoc.platform;

import org.springframework.boot.SpringApplication;

public class TestPlatformServiceApplication {

  public static void main(String[] args) {
    SpringApplication.from(PlatformServiceApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
