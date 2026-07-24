package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PersistenceTestClockConfiguration.class})
class TenantPersistenceAdapterIT {

  @Autowired private SpringDataTenantMembershipRepository springDataTenantMembershipRepository;

  private static final Instant INITIAL_TIME = Instant.parse("2026-07-13T08:00:00Z");

  @Autowired private TenantRepository tenantRepository;

  @Autowired SpringDataTenantRepository springDataTenantRepository;

  @Autowired MutableClock clock;

  @BeforeEach
  void setUp() {
    springDataTenantMembershipRepository.deleteAllInBatch();
    springDataTenantRepository.deleteAllInBatch();
    clock.setInstant(INITIAL_TIME);
  }

  @Test
  void shouldSaveAndFindTenant() {
    Tenant tenant = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name"));

    Tenant savedTenant = tenantRepository.save(tenant);

    assertEquals(tenant.id(), savedTenant.id());
    assertEquals(tenant.key(), savedTenant.key());
    assertEquals(tenant.name(), savedTenant.name());
    assertEquals(TenantStatus.ACTIVE, savedTenant.status());

    Tenant loadedTenant = tenantRepository.findById(tenant.id()).orElseThrow();

    assertEquals(savedTenant.id(), loadedTenant.id());
    assertEquals(savedTenant.key(), loadedTenant.key());
    assertEquals(savedTenant.name(), loadedTenant.name());
    assertEquals(savedTenant.status(), loadedTenant.status());

    assertTrue(tenantRepository.existsByKey(TenantKey.of("tenant-key")));

    TenantJpaEntity storedEntity =
        springDataTenantRepository.findById(tenant.id().value()).orElseThrow();

    assertEquals(tenant.id().value(), storedEntity.getId());
    assertEquals("tenant-key", storedEntity.getTenantKey());
    assertEquals("Tenant Name", storedEntity.getDisplayName());
    assertEquals(TenantStatus.ACTIVE, storedEntity.getStatus());
    assertEquals(INITIAL_TIME, storedEntity.getCreatedAt());
    assertEquals(INITIAL_TIME, storedEntity.getUpdatedAt());
    assertEquals(0L, storedEntity.getVersion());
  }

  @Test
  void shouldUpdateTenant() {
    Tenant tenant = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name"));
    tenantRepository.save(tenant);

    TenantJpaEntity storedEntity =
        springDataTenantRepository.findById(tenant.id().value()).orElseThrow();

    assertEquals(INITIAL_TIME, storedEntity.getCreatedAt());
    assertEquals(INITIAL_TIME, storedEntity.getUpdatedAt());
    assertEquals(0L, storedEntity.getVersion());

    Instant updatedTime = Instant.parse("2026-07-13T09:00:00Z");
    clock.setInstant(updatedTime);

    Tenant tenantToUpdate = tenantRepository.findById(tenant.id()).orElseThrow();
    tenantToUpdate.rename(TenantName.of("Updated Name"));
    tenantToUpdate.suspend();
    Tenant updatedTenant = tenantRepository.save(tenantToUpdate);

    assertEquals(tenant.id(), updatedTenant.id());
    assertEquals(TenantStatus.SUSPENDED, updatedTenant.status());
    assertEquals(TenantName.of("Updated Name"), updatedTenant.name());

    TenantJpaEntity updatedEntity =
        springDataTenantRepository.findById(tenant.id().value()).orElseThrow();
    assertEquals(tenant.id().value(), updatedEntity.getId());
    assertEquals(tenant.key().value(), updatedEntity.getTenantKey());
    assertEquals("Updated Name", updatedEntity.getDisplayName());
    assertEquals(TenantStatus.SUSPENDED, updatedEntity.getStatus());
    assertEquals(INITIAL_TIME, updatedEntity.getCreatedAt());
    assertEquals(updatedTime, updatedEntity.getUpdatedAt());
    assertEquals(1L, updatedEntity.getVersion());
  }

  @Test
  void shouldRejectDuplicateTenantKey() {
    Tenant first = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("First Tenant"));

    Tenant second = Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Second Tenant"));

    tenantRepository.save(first);

    TenantKeyAlreadyExistsException exception =
        assertThrows(TenantKeyAlreadyExistsException.class, () -> tenantRepository.save(second));
    assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    assertEquals(1L, springDataTenantRepository.count());
  }
}
