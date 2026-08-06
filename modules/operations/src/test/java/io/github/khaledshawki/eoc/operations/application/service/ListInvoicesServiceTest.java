package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoicePageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesQuery;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ListInvoicesServiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 6);

  @Test
  void shouldPassExactCriteriaAndReturnDerivedPageResults() {
    AtomicReference<InvoiceQueryCriteria> captured = new AtomicReference<>();
    Invoice invoice = invoice();
    InvoiceQueryRepository repository =
        criteria -> {
          captured.set(criteria);
          return new InvoiceQueryPage(List.of(invoice), 0, 50, 1);
        };
    ListInvoicesService service =
        new ListInvoicesService(repository, (actor, tenantId, permission) -> true);
    ListInvoicesQuery query = query();

    InvoicePageResult result = service.list(query);

    assertEquals(query.criteria(), captured.get());
    assertEquals(1, result.totalElements());
    assertEquals(InvoiceDueState.DUE_TODAY, result.invoices().getFirst().dueState());
  }

  @Test
  void shouldFailClosedBeforeQueryRepositoryAccessWhenAuthorizationIsDenied() {
    ListInvoicesService service =
        new ListInvoicesService(
            criteria -> {
              throw new AssertionError("Repository must not be called");
            },
            (actor, tenantId, permission) -> false);

    assertThrows(OperationsAccessDeniedException.class, () -> service.list(query()));
  }

  private static ListInvoicesQuery query() {
    return new ListInvoicesQuery(
        ACTOR,
        TENANT_ID.value(),
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        BUSINESS_DATE,
        0,
        50,
        InvoiceSortField.ISSUE_DATE,
        SortDirection.DESC);
  }

  private static Invoice invoice() {
    CurrencyCode eur = CurrencyCode.of("EUR");
    return Invoice.reconstitute(
        InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
        TENANT_ID,
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", eur),
        Money.of("0.00", eur),
        LocalDate.of(2026, 7, 1),
        BUSINESS_DATE,
        false);
  }
}
