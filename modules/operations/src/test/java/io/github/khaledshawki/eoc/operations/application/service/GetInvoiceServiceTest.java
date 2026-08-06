package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetInvoiceServiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 6);

  @Test
  void shouldReturnTenantScopedInvoiceWithBusinessDateDerivedFacts() {
    Invoice invoice = invoice();
    GetInvoiceService service =
        new GetInvoiceService(
            repository(Optional.of(invoice)), (actor, tenantId, permission) -> true);

    InvoiceResult result =
        service.get(
            new GetInvoiceQuery(ACTOR, TENANT_ID.value(), INVOICE_ID.value(), BUSINESS_DATE));

    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals("75.00", result.openAmount().amount().toPlainString());
    assertEquals(
        io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState.OVERDUE,
        result.dueState());
  }

  @Test
  void shouldFailClosedBeforeRepositoryAccessWhenAuthorizationIsDenied() {
    InvoiceRepository repository =
        new InvoiceRepository() {
          @Override
          public Invoice save(Invoice invoice) {
            throw new AssertionError("Repository must not be called");
          }

          @Override
          public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
            throw new AssertionError("Repository must not be called");
          }
        };
    GetInvoiceService service =
        new GetInvoiceService(repository, (actor, tenantId, permission) -> false);

    assertThrows(
        OperationsAccessDeniedException.class,
        () ->
            service.get(
                new GetInvoiceQuery(ACTOR, TENANT_ID.value(), INVOICE_ID.value(), BUSINESS_DATE)));
  }

  @Test
  void shouldNotRevealAnotherTenantInvoiceAsPresent() {
    GetInvoiceService service =
        new GetInvoiceService(repository(Optional.empty()), (actor, tenantId, permission) -> true);

    assertThrows(
        InvoiceNotFoundException.class,
        () ->
            service.get(
                new GetInvoiceQuery(ACTOR, TENANT_ID.value(), INVOICE_ID.value(), BUSINESS_DATE)));
  }

  private static InvoiceRepository repository(Optional<Invoice> result) {
    return new InvoiceRepository() {
      @Override
      public Invoice save(Invoice invoice) {
        return invoice;
      }

      @Override
      public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
        assertEquals(TENANT_ID, tenantId);
        assertEquals(INVOICE_ID, invoiceId);
        return result;
      }
    };
  }

  private static Invoice invoice() {
    CurrencyCode eur = CurrencyCode.of("EUR");
    return Invoice.reconstitute(
        INVOICE_ID,
        TENANT_ID,
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", eur),
        Money.of("25.00", eur),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        false);
  }
}
