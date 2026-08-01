package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantAccessPolicyTest {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final RecordingUseCase useCase = new RecordingUseCase();
  private final TenantAccessPolicy policy =
      new TenantAccessPolicy(new JwtAuthenticatedUserMapper(), useCase);

  @Test
  void shouldMapAuthenticatedJwtAndReturnUseCaseDecision() {
    useCase.result = ResolveTenantAccessResult.allow();

    assertTrue(policy.hasRole(authentication(), TENANT_ID, "auditor"));
    assertEquals(
        new ResolveTenantAccessQuery(ISSUER, SUBJECT, TENANT_ID, "auditor"), useCase.query);

    useCase.result = ResolveTenantAccessResult.deny();

    assertFalse(policy.hasRole(authentication(), TENANT_ID, "auditor"));
  }

  @Test
  void shouldFailClosedForUnsupportedOrUnauthenticatedAuthentication() {
    assertFalse(
        policy.hasRole(
            new TestingAuthenticationToken("user", "credentials"), TENANT_ID, "auditor"));
    assertFalse(
        policy.hasAnyRole(
            new TestingAuthenticationToken("user", "credentials"),
            TENANT_ID,
            "auditor",
            "tenant-admin"));

    JwtAuthenticationToken unauthenticated = authentication();
    unauthenticated.setAuthenticated(false);

    assertFalse(policy.hasRole(unauthenticated, TENANT_ID, "auditor"));
    assertEquals(0, useCase.calls);
  }

  @Test
  void shouldAllowAnyMatchingRoleAndStopAfterFirstGrant() {
    useCase.allowedRole = "auditor";

    assertTrue(
        policy.hasAnyRole(
            authentication(), TENANT_ID, "operations-manager", "auditor", "tenant-admin"));

    assertEquals(2, useCase.calls);
    assertEquals(
        List.of(
            new ResolveTenantAccessQuery(ISSUER, SUBJECT, TENANT_ID, "operations-manager"),
            new ResolveTenantAccessQuery(ISSUER, SUBJECT, TENANT_ID, "auditor")),
        useCase.queries);
  }

  @Test
  void shouldDenyWhenNoneOfTheRequiredRolesMatch() {
    assertFalse(policy.hasAnyRole(authentication(), TENANT_ID, "auditor", "tenant-admin"));
    assertEquals(2, useCase.calls);
  }

  @Test
  void shouldRejectNullDependenciesAndArguments() {
    assertThrows(NullPointerException.class, () -> new TenantAccessPolicy(null, useCase));
    assertThrows(
        NullPointerException.class,
        () -> new TenantAccessPolicy(new JwtAuthenticatedUserMapper(), null));
    assertThrows(
        NullPointerException.class, () -> policy.hasRole(authentication(), null, "auditor"));
    assertThrows(
        NullPointerException.class, () -> policy.hasRole(authentication(), TENANT_ID, null));
    assertThrows(
        NullPointerException.class, () -> policy.hasAnyRole(authentication(), null, "auditor"));
    assertThrows(
        NullPointerException.class,
        () -> policy.hasAnyRole(authentication(), TENANT_ID, (String[]) null));
    assertThrows(
        IllegalArgumentException.class, () -> policy.hasAnyRole(authentication(), TENANT_ID));
    assertThrows(
        IllegalArgumentException.class,
        () -> policy.hasAnyRole(authentication(), TENANT_ID, "auditor", null));
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("access-token")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .subject(SUBJECT)
            .build();
    return new JwtAuthenticationToken(jwt, List.of());
  }

  private static final class RecordingUseCase implements ResolveTenantAccessUseCase {
    private ResolveTenantAccessResult result = ResolveTenantAccessResult.deny();
    private ResolveTenantAccessQuery query;
    private final List<ResolveTenantAccessQuery> queries = new ArrayList<>();
    private String allowedRole;
    private int calls;

    @Override
    public ResolveTenantAccessResult resolve(ResolveTenantAccessQuery query) {
      calls++;
      this.query = query;
      queries.add(query);
      if (query.requiredRole().equals(allowedRole)) {
        return ResolveTenantAccessResult.allow();
      }
      return result;
    }
  }
}
