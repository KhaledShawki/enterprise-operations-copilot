package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentInvoiceImportException;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class InvoicePersistenceAdapter implements InvoiceRepository {

  private final SpringDataInvoiceRepository invoiceRepository;
  private final InvoicePersistenceMapper persistenceMapper;
  private final Clock clock;

  InvoicePersistenceAdapter(
      SpringDataInvoiceRepository invoiceRepository,
      InvoicePersistenceMapper persistenceMapper,
      Clock clock) {
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Invoice mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public Invoice save(Invoice invoice) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    Instant now = clock.instant();
    InvoiceJpaEntity entity =
        invoiceRepository
            .findByIdAndTenantId(invoice.id().value(), invoice.tenantId().value())
            .map(existing -> persistenceMapper.updateEntity(invoice, existing, now))
            .orElseGet(() -> persistenceMapper.toEntity(invoice, now));
    try {
      return persistenceMapper.toDomain(invoiceRepository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception) {
      throw concurrentModification(invoice.id(), exception);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
    Objects.requireNonNull(tenantId, "Invoice tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice id cannot be null");
    return invoiceRepository
        .findByIdAndTenantId(invoiceId.value(), tenantId.value())
        .map(persistenceMapper::toDomain);
  }

  private static ConcurrentInvoiceImportException concurrentModification(
      InvoiceId invoiceId, RuntimeException cause) {
    return new ConcurrentInvoiceImportException(
        "Invoice " + invoiceId.value() + " was created or modified concurrently", cause);
  }
}
