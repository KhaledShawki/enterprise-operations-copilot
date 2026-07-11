package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNameAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateTenantServiceTest {

  private InMemoryTenantRepository repository;
  private CreateTenantService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryTenantRepository();
    service = new CreateTenantService(repository);
  }

  @Test
  void shouldCreateAndSaveActiveTenant() {
    CreateTenantResult result = service.create(new CreateTenantCommand("Tenant Name"));

    assertNotNull(result.tenantId());
    assertEquals(TenantName.of("Tenant Name"), result.tenantName());
    assertEquals(TenantStatus.ACTIVE, result.tenantStatus());

    Tenant savedTenant = repository.findById(result.tenantId()).orElseThrow();
    assertEquals(result.tenantId(), savedTenant.id());
    assertEquals(result.tenantName(), savedTenant.name());
    assertEquals(result.tenantStatus(), savedTenant.status());
  }

  @Test
  void shouldTrimTenantNameBeforeCheckingUniqueness() {
    service.create(new CreateTenantCommand("Tenant Name"));

    assertThrows(
        TenantNameAlreadyExistsException.class,
        () -> service.create(new CreateTenantCommand("  Tenant Name  ")));
    assertEquals(1, repository.size());
  }

  @Test
  void shouldRejectNullTenantName() {
    assertThrows(
        IllegalArgumentException.class, () -> service.create(new CreateTenantCommand(null)));
  }

  @Test
  void shouldRejectInvalidTenantName() {
    assertThrows(IllegalArgumentException.class, () -> service.create(new CreateTenantCommand("")));
    assertThrows(
        IllegalArgumentException.class, () -> service.create(new CreateTenantCommand("    ")));

    assertEquals(0, repository.size());
  }

  @Test
  void shouldRejectNullRepository() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new CreateTenantService(null));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  @Test
  void shouldRejectNullCommand() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> service.create(null));

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
    public boolean existsByName(TenantName tenantName) {
      return tenants.values().stream().anyMatch(tenant -> tenant.name().equals(tenantName));
    }

    int size() {
      return tenants.size();
    }
  }
}
