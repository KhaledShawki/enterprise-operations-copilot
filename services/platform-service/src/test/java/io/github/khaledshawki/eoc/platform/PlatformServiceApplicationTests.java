// Copyright (c) 2026 Khaled Shawki.
// Licensed under the MIT License.

package io.github.khaledshawki.eoc.platform;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.service.ReplaceTenantMembershipRolesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlatformServiceApplicationTests {

  @Autowired private ReplaceTenantMembershipRolesUseCase replaceTenantMembershipRolesUseCase;

  @Test
  void contextLoads() {}

  @Test
  void shouldWireReplaceTenantMembershipRolesUseCase() {
    assertInstanceOf(
        ReplaceTenantMembershipRolesService.class, replaceTenantMembershipRolesUseCase);
  }
}
