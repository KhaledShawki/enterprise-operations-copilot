package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/analytics/receivables",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ReceivableSummaryController {

  private static final String READ_RECEIVABLES =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final GetReceivablesSummaryUseCase getReceivablesSummaryUseCase;
  private final Clock clock;

  public ReceivableSummaryController(
      GetReceivablesSummaryUseCase getReceivablesSummaryUseCase, Clock clock) {
    this.getReceivablesSummaryUseCase =
        Objects.requireNonNull(
            getReceivablesSummaryUseCase, "Get Receivables Summary use case cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @GetMapping("/summary")
  @PreAuthorize(READ_RECEIVABLES)
  public ReceivablesSummaryResponse getSummary(
      @PathVariable UUID tenantId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate) {
    LocalDate evaluationDate = businessDate == null ? LocalDate.now(clock) : businessDate;
    return ReceivablesSummaryResponse.from(
        getReceivablesSummaryUseCase.get(new GetReceivablesSummaryQuery(tenantId, evaluationDate)));
  }
}
