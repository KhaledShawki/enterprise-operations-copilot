package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsReadUnavailableException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCustomerSummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivablePage;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableQueryCriteria;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSnapshot;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSummarySnapshot;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableReadPort;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableSummaryReadPort;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReceivableReadPersistenceAdapter implements ReceivableReadPort, ReceivableSummaryReadPort {

  private static final String SELECT_RECEIVABLE =
      """
      SELECT i.tenant_id,
             i.invoice_id,
             i.customer_id,
             i.invoice_number,
             i.original_amount,
             i.paid_amount,
             i.currency,
             i.issue_date,
             i.due_date,
             i.cancelled,
             i.status,
             i.source_event_id,
             i.aggregate_version,
             i.occurred_at,
             bp.business_partner_id AS projected_customer_id,
             bp.partner_number AS customer_number,
             bp.display_name AS customer_display_name
      FROM analytics_invoice_receivable_projections i
      LEFT JOIN analytics_business_partner_projections bp
        ON bp.tenant_id = i.tenant_id
       AND bp.business_partner_id = i.customer_id
      """;

  private static final String SELECT_SUMMARY =
      """
      WITH classified AS (
        SELECT currency,
               (original_amount - paid_amount) AS outstanding_amount,
               (status IN ('OPEN', 'PARTIALLY_PAID')
                 AND (original_amount - paid_amount) > 0) AS open_receivable,
               (due_date < :businessDate
                 AND status IN ('OPEN', 'PARTIALLY_PAID')
                 AND (original_amount - paid_amount) > 0) AS overdue_receivable,
               (:businessDate - due_date) AS days_overdue
        FROM analytics_invoice_receivable_projections
        WHERE tenant_id = :tenantId
      )
      SELECT currency,
             count(*) AS invoice_count,
             count(*) FILTER (WHERE open_receivable) AS open_count,
             count(*) FILTER (WHERE overdue_receivable) AS overdue_count,
             COALESCE(sum(outstanding_amount) FILTER (WHERE open_receivable), 0)
               AS outstanding_amount,
             COALESCE(sum(outstanding_amount) FILTER (WHERE overdue_receivable), 0)
               AS overdue_amount,
             COALESCE(sum(outstanding_amount)
               FILTER (WHERE open_receivable AND NOT overdue_receivable), 0) AS current_amount,
             COALESCE(sum(outstanding_amount)
               FILTER (WHERE overdue_receivable AND days_overdue BETWEEN 1 AND 30), 0)
               AS overdue_1_30_amount,
             COALESCE(sum(outstanding_amount)
               FILTER (WHERE overdue_receivable AND days_overdue BETWEEN 31 AND 60), 0)
               AS overdue_31_60_amount,
             COALESCE(sum(outstanding_amount)
               FILTER (WHERE overdue_receivable AND days_overdue BETWEEN 61 AND 90), 0)
               AS overdue_61_90_amount,
             COALESCE(sum(outstanding_amount)
               FILTER (WHERE overdue_receivable AND days_overdue > 90), 0)
               AS overdue_91_plus_amount
      FROM classified
      GROUP BY currency
      ORDER BY currency
      """;

  private static final String OVERDUE_PREDICATE =
      """
      (i.due_date < :businessDate
       AND i.status IN ('OPEN', 'PARTIALLY_PAID')
       AND (i.original_amount - i.paid_amount) > 0)
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  ReceivableReadPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
  }

  @Override
  public Optional<ReceivableSnapshot> findById(AnalyticsTenantId tenantId, UUID invoiceId) {
    Objects.requireNonNull(tenantId, "Analytics tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice id cannot be null");
    try {
      return jdbcTemplate
          .query(
              SELECT_RECEIVABLE + " WHERE i.tenant_id = :tenantId AND i.invoice_id = :invoiceId",
              Map.of("tenantId", tenantId.value(), "invoiceId", invoiceId),
              (resultSet, rowNum) -> map(resultSet))
          .stream()
          .findFirst();
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw new AnalyticsReadUnavailableException(exception);
    }
  }

  @Override
  public ReceivablePage findPage(ReceivableQueryCriteria criteria) {
    Objects.requireNonNull(criteria, "Receivable query criteria cannot be null");
    MapSqlParameterSource parameters = new MapSqlParameterSource();
    String where = whereClause(criteria, parameters);
    String orderBy = orderBy(criteria.sortField(), criteria.sortDirection());
    parameters.addValue("limit", criteria.pageSize());
    parameters.addValue("offset", criteria.offset());

    try {
      Long total =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM analytics_invoice_receivable_projections i " + where,
              parameters,
              Long.class);
      List<ReceivableSnapshot> receivables =
          jdbcTemplate.query(
              SELECT_RECEIVABLE + where + orderBy + " LIMIT :limit OFFSET :offset",
              parameters,
              (resultSet, rowNum) -> map(resultSet));
      return new ReceivablePage(
          receivables,
          criteria.pageNumber(),
          criteria.pageSize(),
          Objects.requireNonNull(total, "Receivable count query returned null"));
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw new AnalyticsReadUnavailableException(exception);
    }
  }

  @Override
  public ReceivableSummarySnapshot summarize(AnalyticsTenantId tenantId, LocalDate businessDate) {
    Objects.requireNonNull(tenantId, "Analytics tenant id cannot be null");
    Objects.requireNonNull(businessDate, "Receivable summary business date cannot be null");
    try {
      List<ReceivableCurrencySummary> currencies =
          jdbcTemplate.query(
              SELECT_SUMMARY,
              Map.of("tenantId", tenantId.value(), "businessDate", businessDate),
              (resultSet, rowNum) -> mapSummary(resultSet));
      return new ReceivableSummarySnapshot(tenantId, businessDate, currencies);
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw new AnalyticsReadUnavailableException(exception);
    }
  }

  private static String whereClause(
      ReceivableQueryCriteria criteria, MapSqlParameterSource parameters) {
    StringBuilder where = new StringBuilder(" WHERE i.tenant_id = :tenantId");
    parameters.addValue("tenantId", criteria.tenantId().value());

    criteria
        .customerId()
        .ifPresent(
            customerId -> {
              where.append(" AND i.customer_id = :customerId");
              parameters.addValue("customerId", customerId);
            });

    if (!criteria.statuses().isEmpty()) {
      where.append(" AND i.status IN (:statuses)");
      parameters.addValue(
          "statuses", criteria.statuses().stream().map(Enum::name).sorted().toList());
    }

    criteria
        .overdue()
        .ifPresent(
            overdue -> {
              parameters.addValue("businessDate", criteria.businessDate());
              where.append(overdue ? " AND " : " AND NOT ").append(OVERDUE_PREDICATE);
            });
    return where.toString();
  }

  private static String orderBy(ReceivableSortField field, SortDirection direction) {
    String column =
        switch (field) {
          case DUE_DATE -> "i.due_date";
          case ISSUE_DATE -> "i.issue_date";
          case OUTSTANDING_AMOUNT -> "(i.original_amount - i.paid_amount)";
          case INVOICE_NUMBER -> "i.invoice_number";
        };
    String sqlDirection = direction == SortDirection.ASC ? "ASC" : "DESC";
    return " ORDER BY " + column + " " + sqlDirection + ", i.invoice_id " + sqlDirection;
  }

  private static ReceivableCurrencySummary mapSummary(ResultSet resultSet) throws SQLException {
    try {
      CurrencyCode currency = CurrencyCode.of(resultSet.getString("currency"));
      return new ReceivableCurrencySummary(
          currency,
          resultSet.getLong("invoice_count"),
          resultSet.getLong("open_count"),
          resultSet.getLong("overdue_count"),
          new AnalyticsMoney(resultSet.getBigDecimal("outstanding_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("overdue_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("current_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("overdue_1_30_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("overdue_31_60_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("overdue_61_90_amount"), currency),
          new AnalyticsMoney(resultSet.getBigDecimal("overdue_91_plus_amount"), currency));
    } catch (IllegalArgumentException | NullPointerException exception) {
      AnalyticsProjectionStateCorruptedException corrupted =
          new AnalyticsProjectionStateCorruptedException(
              "stored receivable summary projection is invalid");
      corrupted.initCause(exception);
      throw corrupted;
    }
  }

  private static ReceivableSnapshot map(ResultSet resultSet) throws SQLException {
    try {
      CurrencyCode currency = CurrencyCode.of(resultSet.getString("currency"));
      InvoiceReceivableProjection invoice =
          new InvoiceReceivableProjection(
              AnalyticsTenantId.of(resultSet.getObject("tenant_id", UUID.class)),
              resultSet.getObject("invoice_id", UUID.class),
              resultSet.getObject("customer_id", UUID.class),
              resultSet.getString("invoice_number"),
              new AnalyticsMoney(resultSet.getBigDecimal("original_amount"), currency),
              new AnalyticsMoney(resultSet.getBigDecimal("paid_amount"), currency),
              resultSet.getObject("issue_date", java.time.LocalDate.class),
              resultSet.getObject("due_date", java.time.LocalDate.class),
              resultSet.getBoolean("cancelled"),
              InvoiceReceivableStatus.fromContractCode(resultSet.getString("status")),
              new ProjectionCursor(
                  resultSet.getObject("source_event_id", UUID.class),
                  resultSet.getLong("aggregate_version"),
                  resultSet.getTimestamp("occurred_at").toInstant()));

      UUID projectedCustomerId = resultSet.getObject("projected_customer_id", UUID.class);
      String customerNumber = resultSet.getString("customer_number");
      String customerDisplayName = resultSet.getString("customer_display_name");
      if (projectedCustomerId == null) {
        if (customerNumber != null || customerDisplayName != null) {
          throw new IllegalArgumentException(
              "Joined customer projection is internally inconsistent");
        }
      } else if (!projectedCustomerId.equals(invoice.customerId())) {
        throw new IllegalArgumentException(
            "Joined customer projection belongs to another customer");
      }

      ReceivableCustomerSummary customer =
          new ReceivableCustomerSummary(
              invoice.customerId(),
              Optional.ofNullable(customerNumber),
              Optional.ofNullable(customerDisplayName));
      return new ReceivableSnapshot(invoice, customer);
    } catch (IllegalArgumentException | NullPointerException exception) {
      AnalyticsProjectionStateCorruptedException corrupted =
          new AnalyticsProjectionStateCorruptedException(
              "stored receivable read projection is invalid");
      corrupted.initCause(exception);
      throw corrupted;
    }
  }
}
