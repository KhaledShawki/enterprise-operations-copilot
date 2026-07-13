package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateTenantServiceTest {

  private static final String TENANT_KEY = "tenant-key";
  private static final String TENANT_NAME = "Tenant Name";

  private InMemoryTenantRepository repository;
  private CreateTenantService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryTenantRepository();
    service = new CreateTenantService(repository);
  }

  @Test
  void shouldCreateAndSaveActiveTenant() {
    CreateTenantResult result = service.create(new CreateTenantCommand(TENANT_KEY, TENANT_NAME));

    assertNotNull(result.tenantId());
    assertEquals(TenantKey.of(TENANT_KEY), result.tenantKey());
    assertEquals(TenantName.of(TENANT_NAME), result.tenantName());
    assertEquals(TenantStatus.ACTIVE, result.tenantStatus());

    Tenant savedTenant = repository.findById(result.tenantId()).orElseThrow();

    assertEquals(result.tenantId(), savedTenant.id());
    assertEquals(result.tenantKey(), savedTenant.key());
    assertEquals(result.tenantName(), savedTenant.name());
    assertEquals(result.tenantStatus(), savedTenant.status());
  }

  @Test
  void shouldNormalizeTenantKeyBeforeCheckingUniqueness() {
    service.create(new CreateTenantCommand("tenant-key", "First Tenant"));

    assertThrows(
        TenantKeyAlreadyExistsException.class,
        () -> service.create(new CreateTenantCommand("  TENANT-KEY  ", "Second Tenant")));

    assertEquals(1, repository.size());
  }

  @Test
  void shouldAllowDuplicateDisplayNamesForDifferentKeys() {
    service.create(new CreateTenantCommand("first-tenant", TENANT_NAME));

    service.create(new CreateTenantCommand("second-tenant", TENANT_NAME));

    assertEquals(2, repository.size());
  }

  @Test
  void shouldRejectNullTenantKey() {
    assertThrows(
        NullPointerException.class,
        () -> service.create(new CreateTenantCommand(null, TENANT_NAME)));

    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectInvalidTenantKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(new CreateTenantCommand("", TENANT_NAME)));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(new CreateTenantCommand("invalid key", TENANT_NAME)));

    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectNullTenantName() {
    assertThrows(
        NullPointerException.class,
        () -> service.create(new CreateTenantCommand(TENANT_KEY, null)));

    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectInvalidTenantName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(new CreateTenantCommand(TENANT_KEY, "")));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create(new CreateTenantCommand(TENANT_KEY, "    ")));

    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectNullRepository() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new CreateTenantService(null));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullCommand() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.create(null));

    assertEquals("Command cannot be null", exception.getMessage());
    assertEquals(0, repository.size());
  }

  private static final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> tenants = new HashMap<>();

    @Override
    public Tenant save(Tenant tenant) {
      tenants.put(tenant.id(), tenant);
      return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
      return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public boolean existsByKey(TenantKey tenantKey) {
      return tenants.values().stream().map(Tenant::key).anyMatch(tenantKey::equals);
    }

    int size() {
      return tenants.size();
    }
  }
}
