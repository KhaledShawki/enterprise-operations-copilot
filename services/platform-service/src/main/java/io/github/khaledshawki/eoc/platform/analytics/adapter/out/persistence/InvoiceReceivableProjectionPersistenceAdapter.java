package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.port.out.InvoiceReceivableProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class InvoiceReceivableProjectionPersistenceAdapter
    implements InvoiceReceivableProjectionRepository {

  private static final String PERSISTENCE_UNAVAILABLE =
      "analytics-projection-persistence-unavailable";

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  InvoiceReceivableProjectionPersistenceAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public Optional<InvoiceReceivableProjection> findById(
      AnalyticsTenantId tenantId, UUID invoiceId) {
    Objects.requireNonNull(tenantId, "Analytics tenant id cannot be null");
    Objects.requireNonNull(invoiceId, "Invoice id cannot be null");
    try {
      return jdbcTemplate
          .query(
              """
              SELECT tenant_id,
                     invoice_id,
                     customer_id,
                     invoice_number,
                     original_amount,
                     paid_amount,
                     currency,
                     issue_date,
                     due_date,
                     cancelled,
                     status,
                     source_event_id,
                     aggregate_version,
                     occurred_at
              FROM analytics_invoice_receivable_projections
              WHERE tenant_id = ? AND invoice_id = ?
              """,
              (resultSet, rowNum) -> {
                try {
                  CurrencyCode currency = CurrencyCode.of(resultSet.getString("currency"));
                  return new InvoiceReceivableProjection(
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
                } catch (IllegalArgumentException | NullPointerException exception) {
                  throw corrupted("stored invoice receivable projection is invalid", exception);
                }
              },
              tenantId.value(),
              invoiceId)
          .stream()
          .findFirst();
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw retryable(exception);
    }
  }

  @Override
  public boolean saveIfCurrentVersion(
      InvoiceReceivableProjection projection, long expectedCurrentVersion) {
    Objects.requireNonNull(projection, "Invoice receivable projection cannot be null");
    if (expectedCurrentVersion < 0) {
      throw new IllegalArgumentException("Expected current projection version cannot be negative");
    }
    AnalyticsPersistenceTransactionGuard.requireActive();
    Timestamp projectedAt = Timestamp.from(clock.instant());
    try {
      if (expectedCurrentVersion == 0) {
        return jdbcTemplate.update(
                """
                INSERT INTO analytics_invoice_receivable_projections (
                  tenant_id,
                  invoice_id,
                  customer_id,
                  invoice_number,
                  original_amount,
                  paid_amount,
                  currency,
                  issue_date,
                  due_date,
                  cancelled,
                  status,
                  source_event_id,
                  aggregate_version,
                  occurred_at,
                  projected_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, invoice_id) DO NOTHING
                """,
                projection.tenantId().value(),
                projection.invoiceId(),
                projection.customerId(),
                projection.invoiceNumber(),
                projection.originalAmount().amount(),
                projection.paidAmount().amount(),
                projection.originalAmount().currency().value(),
                projection.issueDate(),
                projection.dueDate(),
                projection.cancelled(),
                projection.status().name(),
                projection.source().eventId(),
                projection.source().aggregateVersion(),
                Timestamp.from(projection.source().occurredAt()),
                projectedAt)
            == 1;
      }

      return jdbcTemplate.update(
              """
              UPDATE analytics_invoice_receivable_projections
              SET customer_id = ?,
                  invoice_number = ?,
                  original_amount = ?,
                  paid_amount = ?,
                  currency = ?,
                  issue_date = ?,
                  due_date = ?,
                  cancelled = ?,
                  status = ?,
                  source_event_id = ?,
                  aggregate_version = ?,
                  occurred_at = ?,
                  projected_at = ?
              WHERE tenant_id = ?
                AND invoice_id = ?
                AND aggregate_version = ?
              """,
              projection.customerId(),
              projection.invoiceNumber(),
              projection.originalAmount().amount(),
              projection.paidAmount().amount(),
              projection.originalAmount().currency().value(),
              projection.issueDate(),
              projection.dueDate(),
              projection.cancelled(),
              projection.status().name(),
              projection.source().eventId(),
              projection.source().aggregateVersion(),
              Timestamp.from(projection.source().occurredAt()),
              projectedAt,
              projection.tenantId().value(),
              projection.invoiceId(),
              expectedCurrentVersion)
          == 1;
    } catch (DataIntegrityViolationException exception) {
      throw corrupted("database constraints rejected an invoice receivable projection", exception);
    } catch (DataAccessException exception) {
      throw retryable(exception);
    }
  }

  private static AnalyticsProjectionStateCorruptedException corrupted(
      String detail, Throwable cause) {
    AnalyticsProjectionStateCorruptedException exception =
        new AnalyticsProjectionStateCorruptedException(detail);
    exception.initCause(cause);
    return exception;
  }

  private static AnalyticsEventConsumptionException retryable(DataAccessException cause) {
    return new AnalyticsEventConsumptionException(PERSISTENCE_UNAVAILABLE, true, cause);
  }
}
