package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Instant;
import java.util.Objects;

final class InvoicePersistenceMapper {

  InvoiceJpaEntity toEntity(Invoice invoice, Instant now) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    return new InvoiceJpaEntity(
        invoice.id().value(),
        invoice.tenantId().value(),
        invoice.customerId().value(),
        invoice.invoiceNumber().value(),
        invoice.originalAmount().currency().value(),
        invoice.originalAmount().amount(),
        invoice.paidAmount().amount(),
        invoice.issueDate(),
        invoice.dueDate(),
        invoice.cancelled(),
        now,
        now);
  }

  Invoice toDomain(InvoiceJpaEntity entity) {
    Objects.requireNonNull(entity, "Invoice entity cannot be null");
    CurrencyCode currency = CurrencyCode.of(entity.getCurrencyCode());
    return Invoice.reconstitute(
        InvoiceId.of(entity.getId()),
        OperationsTenantId.of(entity.getTenantId()),
        BusinessPartnerId.of(entity.getCustomerId()),
        new InvoiceNumber(entity.getInvoiceNumber()),
        new Money(entity.getOriginalAmount(), currency),
        new Money(entity.getPaidAmount(), currency),
        entity.getIssueDate(),
        entity.getDueDate(),
        entity.isCancelled());
  }

  InvoiceJpaEntity updateEntity(Invoice invoice, InvoiceJpaEntity entity, Instant now) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    Objects.requireNonNull(entity, "Invoice entity cannot be null");
    Objects.requireNonNull(now, "Timestamp cannot be null");
    ensureImmutableStateMatches(invoice, entity);
    entity.updateMutableState(
        invoice.customerId().value(),
        invoice.invoiceNumber().value(),
        invoice.originalAmount().currency().value(),
        invoice.originalAmount().amount(),
        invoice.paidAmount().amount(),
        invoice.issueDate(),
        invoice.dueDate(),
        invoice.cancelled(),
        now);
    return entity;
  }

  private static void ensureImmutableStateMatches(Invoice invoice, InvoiceJpaEntity entity) {
    if (!invoice.id().value().equals(entity.getId())) {
      throw new IllegalArgumentException("Invoice id mismatch");
    }
    if (!invoice.tenantId().value().equals(entity.getTenantId())) {
      throw new IllegalArgumentException("Invoice tenant id mismatch");
    }
  }
}
