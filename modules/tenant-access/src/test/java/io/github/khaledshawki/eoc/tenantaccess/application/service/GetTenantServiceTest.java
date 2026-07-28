package io.github.khaledshawki.eoc.tenantaccess.application.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTenantServiceTest {

  private InMemoryTenantRepository tenantRepository;

  private GetTenantService service;

  @BeforeEach
  void setUp() {
    tenantRepository = new InMemoryTenantRepository();

    service = new GetTenantService(tenantRepository);
  }

  @Test
  void shouldReturnActiveTenantWithoutPersisting() {
    Tenant tenant =
        storeTenant(Tenant.create(TenantKey.of("active-tenant"), TenantName.of("Active Tenant")));

    GetTenantResult result = service.get(new GetTenantQuery(tenant.id().value()));

    assertAll(
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(tenant.key(), result.key()),
        () -> assertEquals(tenant.name(), result.name()),
        () -> assertEquals(TenantStatus.ACTIVE, result.status()));

    assertEquals(1, tenantRepository.findByIdCalls());

    assertEquals(0, tenantRepository.saveCalls());
  }

  @Test
  void shouldReturnSuspendedTenantWithoutPersisting() {
    Tenant tenant =
        storeTenant(
            Tenant.reconstitute(
                TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                TenantKey.of("suspended-tenant"),
                TenantName.of("Suspended Tenant"),
                TenantStatus.SUSPENDED));

    GetTenantResult result = service.get(new GetTenantQuery(tenant.id().value()));

    assertAll(
        () -> assertEquals(tenant.id(), result.tenantId()),
        () -> assertEquals(tenant.key(), result.key()),
        () -> assertEquals(tenant.name(), result.name()),
        () -> assertEquals(TenantStatus.SUSPENDED, result.status()));

    assertEquals(1, tenantRepository.findByIdCalls());

    assertEquals(0, tenantRepository.saveCalls());
  }

  @Test
  void shouldRejectMissingTenantWithoutPersisting() {
    TenantId tenantId = TenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

    TenantNotFoundException exception =
        assertThrows(
            TenantNotFoundException.class, () -> service.get(new GetTenantQuery(tenantId.value())));

    assertEquals("Tenant " + tenantId.value() + " was not found", exception.getMessage());

    assertEquals(1, tenantRepository.findByIdCalls());

    assertEquals(0, tenantRepository.saveCalls());
  }

  @Test
  void shouldRejectNullQueryWithoutAccessingRepository() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.get(null));

    assertEquals("Query cannot be null", exception.getMessage());

    assertEquals(0, tenantRepository.findByIdCalls());

    assertEquals(0, tenantRepository.saveCalls());
  }

  @Test
  void shouldRejectNullTenantRepository() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new GetTenantService(null));

    assertEquals("Tenant repository cannot be null", exception.getMessage());
  }

  private Tenant storeTenant(Tenant tenant) {
    tenantRepository.store(tenant);
    return tenant;
  }

  private static final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> tenants = new HashMap<>();

    private int findByIdCalls;
    private int saveCalls;

    void store(Tenant tenant) {
      tenants.put(tenant.id(), tenant);
    }

    @Override
    public Tenant save(Tenant tenant) {
      saveCalls++;
      store(tenant);
      return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
      findByIdCalls++;

      return Optional.ofNullable(tenants.get(tenantId));
    }

    @Override
    public boolean existsByKey(TenantKey key) {
      return tenants.values().stream().map(Tenant::key).anyMatch(key::equals);
    }

    int findByIdCalls() {
      return findByIdCalls;
    }

    int saveCalls() {
      return saveCalls;
    }
  }
}
