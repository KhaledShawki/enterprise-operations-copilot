package io.github.khaledshawki.eoc.copilot.application.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.khaledshawki.eoc.copilot.application.exception.*;
import io.github.khaledshawki.eoc.copilot.application.model.*;
import io.github.khaledshawki.eoc.copilot.application.port.out.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CopilotToolExecutorServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000211");
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC);
  private static final CopilotExecutionContext CONTEXT =
      new CopilotExecutionContext(URI.create("https://issuer.example"), "subject-1", TENANT_ID);

  @Test
  void authorizesBeforeReadingAndUsesClockWhenBusinessDateIsAbsent() {
    var authorizations = new AtomicInteger();
    var reads = new AtomicInteger();
    CopilotReceivablesAuthorizationPort authorization =
        context -> {
          authorizations.incrementAndGet();
          return true;
        };
    CopilotReceivablesDataPort data =
        new StubDataPort() {
          @Override
          public CopilotReceivable getReceivable(
              UUID tenantId, UUID invoiceId, LocalDate businessDate) {
            reads.incrementAndGet();
            assertEquals(TENANT_ID, tenantId);
            assertEquals(TODAY, businessDate);
            return receivable(tenantId, invoiceId, businessDate, false);
          }
        };
    var service = new CopilotToolExecutorService(authorization, data, CLOCK);
    CopilotReceivable result =
        service.execute(CONTEXT, GetReceivableToolRequest.current(INVOICE_ID));
    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals(1, authorizations.get());
    assertEquals(1, reads.get());
  }

  @Test
  void deniedAccessNeverInvokesAnalyticsPort() {
    var reads = new AtomicInteger();
    CopilotReceivablesDataPort data =
        new StubDataPort() {
          @Override
          public CopilotReceivable getReceivable(
              UUID tenantId, UUID invoiceId, LocalDate businessDate) {
            reads.incrementAndGet();
            return receivable(tenantId, invoiceId, businessDate, false);
          }
        };
    var service = new CopilotToolExecutorService(context -> false, data, CLOCK);
    assertThrows(
        CopilotToolAccessDeniedException.class,
        () -> service.execute(CONTEXT, GetReceivableToolRequest.current(INVOICE_ID)));
    assertEquals(0, reads.get());
  }

  @Test
  void rejectsAdversarialPaginationBeforeExecution() {
    assertThrows(
        InvalidCopilotToolArgumentsException.class,
        () -> listRequest(ListReceivablesToolRequest.MAX_PAGE_NUMBER + 1, 25));
    assertThrows(
        InvalidCopilotToolArgumentsException.class,
        () -> listRequest(0, ListReceivablesToolRequest.MAX_PAGE_SIZE + 1));
  }

  @Test
  void rejectsCrossTenantGetResult() {
    UUID otherTenant = UUID.fromString("00000000-0000-0000-0000-000000000999");
    CopilotReceivablesDataPort data =
        new StubDataPort() {
          @Override
          public CopilotReceivable getReceivable(
              UUID tenantId, UUID invoiceId, LocalDate businessDate) {
            return receivable(otherTenant, invoiceId, businessDate, false);
          }
        };
    var service = new CopilotToolExecutorService(context -> true, data, CLOCK);
    assertThrows(
        CopilotToolDataCorruptedException.class,
        () -> service.execute(CONTEXT, GetReceivableToolRequest.current(INVOICE_ID)));
  }

  @Test
  void rejectsListRowsOutsideRequestedFilters() {
    CopilotReceivablesDataPort data =
        new StubDataPort() {
          @Override
          public CopilotReceivablePage listReceivables(
              UUID tenantId, ReceivableListCriteria criteria) {
            return new CopilotReceivablePage(
                List.of(receivable(tenantId, INVOICE_ID, criteria.businessDate(), false)),
                criteria.pageNumber(),
                criteria.pageSize(),
                1,
                1,
                criteria.businessDate(),
                false,
                false);
          }
        };
    var service = new CopilotToolExecutorService(context -> true, data, CLOCK);
    var request =
        new ListReceivablesToolRequest(
            Optional.empty(),
            Set.of(ReceivableStatus.OPEN),
            Optional.of(true),
            Optional.empty(),
            0,
            10,
            ReceivableSortField.DUE_DATE,
            SortDirection.ASC);
    assertThrows(CopilotToolDataCorruptedException.class, () -> service.execute(CONTEXT, request));
  }

  @Test
  void preservesExplicitSummaryBusinessDateAndValidatesTenantScope() {
    LocalDate date = LocalDate.of(2026, 7, 31);
    CopilotReceivablesDataPort data =
        new StubDataPort() {
          @Override
          public CopilotReceivablesSummary getReceivablesSummary(
              UUID tenantId, LocalDate businessDate) {
            return new CopilotReceivablesSummary(tenantId, businessDate, 0, 0, 0, List.of());
          }
        };
    var service = new CopilotToolExecutorService(context -> true, data, CLOCK);
    var result = service.execute(CONTEXT, new GetReceivablesSummaryToolRequest(Optional.of(date)));
    assertEquals(date, result.businessDate());
  }

  private static ListReceivablesToolRequest listRequest(int page, int size) {
    return new ListReceivablesToolRequest(
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        Optional.empty(),
        page,
        size,
        ReceivableSortField.DUE_DATE,
        SortDirection.ASC);
  }

  private static CopilotReceivable receivable(
      UUID tenantId, UUID invoiceId, LocalDate businessDate, boolean overdue) {
    return new CopilotReceivable(
        tenantId,
        invoiceId,
        new CopilotCustomer(
            UUID.fromString("00000000-0000-0000-0000-000000000311"),
            false,
            Optional.empty(),
            Optional.empty()),
        "INV-1",
        money("100.00"),
        money("20.00"),
        money("80.00"),
        LocalDate.of(2026, 7, 1),
        overdue ? LocalDate.of(2026, 8, 1) : LocalDate.of(2026, 8, 31),
        businessDate,
        ReceivableStatus.PARTIALLY_PAID,
        false,
        overdue,
        new CopilotEvidence(
            UUID.fromString("00000000-0000-0000-0000-000000000411"),
            1,
            Instant.parse("2026-08-01T00:00:00Z")));
  }

  private static CopilotMoney money(String amount) {
    return new CopilotMoney(new BigDecimal(amount), "CHF");
  }

  private abstract static class StubDataPort implements CopilotReceivablesDataPort {
    @Override
    public CopilotReceivable getReceivable(UUID tenantId, UUID invoiceId, LocalDate businessDate) {
      throw new AssertionError("unexpected get");
    }

    @Override
    public CopilotReceivablePage listReceivables(UUID tenantId, ReceivableListCriteria criteria) {
      throw new AssertionError("unexpected list");
    }

    @Override
    public CopilotReceivablesSummary getReceivablesSummary(UUID tenantId, LocalDate businessDate) {
      throw new AssertionError("unexpected summary");
    }
  }
}
