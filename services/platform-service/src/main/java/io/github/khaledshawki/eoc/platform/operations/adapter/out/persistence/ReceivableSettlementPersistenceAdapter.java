package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ReceivableSettlementPersistenceAdapter implements ReceivableSettlementRepository {

  private static final String ACTIVE_INVOICE_TOTAL_SQL =
      """
      SELECT currency_code, SUM(amount) AS total_amount
      FROM operations_receivable_allocations
      WHERE tenant_id = ?
        AND invoice_id = ?
        AND state = 'ACTIVE'
      GROUP BY currency_code
      ORDER BY currency_code
      """;

  private final SpringDataReceivableSettlementRepository settlementRepository;
  private final SpringDataReceivableAllocationRepository allocationRepository;
  private final ReceivableSettlementPersistenceMapper persistenceMapper;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  ReceivableSettlementPersistenceAdapter(
      SpringDataReceivableSettlementRepository settlementRepository,
      SpringDataReceivableAllocationRepository allocationRepository,
      ReceivableSettlementPersistenceMapper persistenceMapper,
      JdbcTemplate jdbcTemplate,
      Clock clock) {
    this.settlementRepository =
        Objects.requireNonNull(
            settlementRepository, "Receivable settlement JPA repository cannot be null");
    this.allocationRepository =
        Objects.requireNonNull(
            allocationRepository, "Receivable allocation JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Receivable settlement mapper cannot be null");
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public ReceivableSettlement save(ReceivableSettlement settlement) {
    Objects.requireNonNull(settlement, "Receivable settlement cannot be null");
    Instant now = clock.instant();
    ensurePaymentOwnershipIsUnique(settlement);

    ReceivableSettlementJpaEntity settlementEntity =
        settlementRepository
            .findByIdAndTenantId(settlement.id().value(), settlement.tenantId().value())
            .map(existing -> persistenceMapper.updateEntity(settlement, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(settlement, now));
    settlementRepository.saveAndFlush(settlementEntity);

    persistAllocations(settlement, now);
    allocationRepository.flush();
    settlementRepository.flush();
    return loadBySettlementId(settlement.tenantId(), settlement.id().value())
        .orElseThrow(() -> corrupted("Persisted receivable settlement could not be reloaded"));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReceivableSettlement> findByPaymentId(
      OperationsTenantId tenantId, PaymentId paymentId) {
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    return settlementRepository
        .findByTenantIdAndPaymentId(tenantId.value(), paymentId.value())
        .map(this::loadAggregate);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReceivableSettlement> findByAllocationId(
      OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
    return allocationRepository
        .findByIdAndTenantId(allocationId.value(), tenantId.value())
        .map(
            allocation ->
                settlementRepository
                    .findByIdAndTenantId(allocation.getSettlementId(), tenantId.value())
                    .map(this::loadAggregate)
                    .orElseThrow(
                        () -> corrupted("Receivable allocation points to a missing settlement")));
  }

  @Override
  @Transactional(readOnly = true)
  public Money activeAllocatedAmountForInvoice(
      OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency) {
    Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Receivable allocation invoice id cannot be null");
    Objects.requireNonNull(currency, "Receivable allocation currency cannot be null");

    List<CurrencyTotal> totals =
        jdbcTemplate.query(
            ACTIVE_INVOICE_TOTAL_SQL,
            (resultSet, rowNumber) ->
                new CurrencyTotal(
                    resultSet.getString("currency_code"), resultSet.getBigDecimal("total_amount")),
            tenantId.value(),
            invoiceId.value());
    if (totals.isEmpty()) {
      return Money.zero(currency);
    }
    if (totals.size() != 1) {
      throw corrupted("Active Invoice allocations contain more than one currency");
    }
    CurrencyTotal total = totals.getFirst();
    CurrencyCode persistedCurrency;
    try {
      persistedCurrency = CurrencyCode.of(total.currencyCode());
    } catch (RuntimeException exception) {
      throw corrupted("Active Invoice allocations contain an invalid currency");
    }
    if (!persistedCurrency.equals(currency)) {
      throw corrupted("Active Invoice allocation currency does not match the requested currency");
    }
    if (total.amount() == null) {
      throw corrupted("Active Invoice allocation total cannot be null");
    }
    try {
      return new Money(total.amount(), persistedCurrency);
    } catch (RuntimeException exception) {
      throw corrupted("Active Invoice allocation total cannot be represented as Money");
    }
  }

  private void ensurePaymentOwnershipIsUnique(ReceivableSettlement settlement) {
    settlementRepository
        .findByTenantIdAndPaymentId(settlement.tenantId().value(), settlement.paymentId().value())
        .filter(existing -> !existing.getId().equals(settlement.id().value()))
        .ifPresent(
            existing -> {
              throw corrupted("A Payment is already owned by another receivable settlement");
            });
  }

  private void persistAllocations(ReceivableSettlement settlement, Instant now) {
    List<ReceivableAllocationJpaEntity> persisted =
        allocationRepository.findAllByTenantIdAndSettlementIdOrderByAllocationPositionAsc(
            settlement.tenantId().value(), settlement.id().value());
    Map<UUID, ReceivableAllocationJpaEntity> existingById = new HashMap<>();
    for (ReceivableAllocationJpaEntity entity : persisted) {
      if (existingById.put(entity.getId(), entity) != null) {
        throw corrupted("Persisted receivable settlement contains duplicate allocation identities");
      }
    }

    List<ReceivableAllocation> allocations = settlement.allocations();
    for (int position = 0; position < allocations.size(); position++) {
      ReceivableAllocation allocation = allocations.get(position);
      ReceivableAllocationJpaEntity existing = existingById.remove(allocation.id().value());
      ReceivableAllocationJpaEntity entity;
      if (existing == null) {
        ensureAllocationIdentityIsAvailable(settlement, allocation);
        entity = persistenceMapper.toEntity(settlement, allocation, position, now);
      } else {
        entity = persistenceMapper.updateEntity(settlement, allocation, position, existing, now);
      }
      allocationRepository.save(entity);
    }
    if (!existingById.isEmpty()) {
      throw corrupted("Receivable settlement persistence cannot delete allocation history");
    }
  }

  private void ensureAllocationIdentityIsAvailable(
      ReceivableSettlement settlement, ReceivableAllocation allocation) {
    allocationRepository
        .findByIdAndTenantId(allocation.id().value(), settlement.tenantId().value())
        .ifPresent(
            existing -> {
              throw corrupted(
                  existing.getSettlementId().equals(settlement.id().value())
                      ? "Settlement allocation list omitted an already persisted allocation identity"
                      : "Receivable allocation identity belongs to another settlement");
            });
  }

  private Optional<ReceivableSettlement> loadBySettlementId(
      OperationsTenantId tenantId, UUID settlementId) {
    return settlementRepository
        .findByIdAndTenantId(settlementId, tenantId.value())
        .map(this::loadAggregate);
  }

  private ReceivableSettlement loadAggregate(ReceivableSettlementJpaEntity entity) {
    List<ReceivableAllocationJpaEntity> allocations =
        allocationRepository.findAllByTenantIdAndSettlementIdOrderByAllocationPositionAsc(
            entity.getTenantId(), entity.getId());
    return persistenceMapper.toDomain(entity, allocations);
  }

  private static ReceivableSettlementStateCorruptedException corrupted(String detail) {
    return new ReceivableSettlementStateCorruptedException(detail);
  }

  private record CurrencyTotal(String currencyCode, BigDecimal amount) {}
}
