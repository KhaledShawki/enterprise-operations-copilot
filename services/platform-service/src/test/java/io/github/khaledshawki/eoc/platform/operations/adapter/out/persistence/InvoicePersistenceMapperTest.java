package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoicePersistenceMapperTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  private final InvoicePersistenceMapper mapper = new InvoicePersistenceMapper();

  @Test
  void shouldRoundTripInvoiceFactsAndIdentity() {
    Invoice invoice =
        Invoice.reconstitute(
            InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
            TENANT_ID,
            CUSTOMER_ID,
            new InvoiceNumber("INV-100"),
            Money.of("120.00", EUR),
            Money.of("20.00", EUR),
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            false);

    InvoiceJpaEntity entity = mapper.toEntity(invoice, NOW);
    Invoice restored = mapper.toDomain(entity);

    assertEquals(invoice.id(), restored.id());
    assertEquals(invoice.tenantId(), restored.tenantId());
    assertEquals(invoice.customerId(), restored.customerId());
    assertEquals(invoice.invoiceNumber(), restored.invoiceNumber());
    assertEquals(invoice.originalAmount(), restored.originalAmount());
    assertEquals(invoice.paidAmount(), restored.paidAmount());
    assertEquals(invoice.issueDate(), restored.issueDate());
    assertEquals(invoice.dueDate(), restored.dueDate());
    assertFalse(restored.cancelled());
    assertEquals(NOW, entity.getCreatedAt());
    assertEquals(NOW, entity.getUpdatedAt());
  }

  @Test
  void shouldUpdateOnlyMutableFactsAndRejectIdentityMismatches() {
    Invoice original =
        Invoice.reconstitute(
            InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000010")),
            TENANT_ID,
            CUSTOMER_ID,
            new InvoiceNumber("INV-100"),
            Money.of("120.00", EUR),
            Money.of("0.00", EUR),
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            false);
    InvoiceJpaEntity entity = mapper.toEntity(original, NOW);
    Instant later = NOW.plusSeconds(60);
    Invoice updated =
        Invoice.reconstitute(
            original.id(),
            TENANT_ID,
            CUSTOMER_ID,
            new InvoiceNumber("INV-100-A"),
            Money.of("150.00", EUR),
            Money.of("50.00", EUR),
            LocalDate.parse("2026-08-02"),
            LocalDate.parse("2026-09-01"),
            true);

    mapper.updateEntity(updated, entity, later);

    assertEquals("INV-100-A", entity.getInvoiceNumber());
    assertEquals(Money.of("150.00", EUR).amount(), entity.getOriginalAmount());
    assertEquals(Money.of("50.00", EUR).amount(), entity.getPaidAmount());
    assertEquals(later, entity.getUpdatedAt());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            mapper.updateEntity(
                Invoice.reconstitute(
                    InvoiceId.generate(),
                    TENANT_ID,
                    CUSTOMER_ID,
                    updated.invoiceNumber(),
                    updated.originalAmount(),
                    updated.paidAmount(),
                    updated.issueDate(),
                    updated.dueDate(),
                    updated.cancelled()),
                entity,
                later));
  }
}
