package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class InvoiceQueryPersistenceAdapter implements InvoiceQueryRepository {

  private final SpringDataInvoiceRepository invoiceRepository;
  private final InvoicePersistenceMapper persistenceMapper;

  InvoiceQueryPersistenceAdapter(
      SpringDataInvoiceRepository invoiceRepository, InvoicePersistenceMapper persistenceMapper) {
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Invoice mapper cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public InvoiceQueryPage findPage(InvoiceQueryCriteria criteria) {
    Objects.requireNonNull(criteria, "Invoice query criteria cannot be null");
    Page<InvoiceJpaEntity> page =
        invoiceRepository.findAll(specification(criteria), pageable(criteria));
    return new InvoiceQueryPage(
        page.getContent().stream().map(persistenceMapper::toDomain).toList(),
        criteria.pageNumber(),
        criteria.pageSize(),
        page.getTotalElements());
  }

  private static Specification<InvoiceJpaEntity> specification(InvoiceQueryCriteria criteria) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(criteriaBuilder.equal(root.get("tenantId"), criteria.tenantId().value()));
      criteria
          .customerId()
          .ifPresent(
              customerId ->
                  predicates.add(
                      criteriaBuilder.equal(root.get("customerId"), customerId.value())));

      Path<Boolean> cancelled = root.get("cancelled");
      Path<BigDecimal> originalAmount = root.get("originalAmount");
      Path<BigDecimal> paidAmount = root.get("paidAmount");
      Path<LocalDate> dueDate = root.get("dueDate");

      if (!criteria.statuses().isEmpty()) {
        Predicate[] statuses =
            criteria.statuses().stream()
                .map(
                    status ->
                        statusPredicate(
                            status, cancelled, originalAmount, paidAmount, criteriaBuilder))
                .toArray(Predicate[]::new);
        predicates.add(criteriaBuilder.or(statuses));
      }

      criteria
          .dueState()
          .ifPresent(
              dueState ->
                  predicates.add(
                      dueStatePredicate(
                          dueState,
                          criteria.businessDate(),
                          cancelled,
                          originalAmount,
                          paidAmount,
                          dueDate,
                          criteriaBuilder)));

      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate statusPredicate(
      InvoiceStatus status,
      Path<Boolean> cancelled,
      Path<BigDecimal> originalAmount,
      Path<BigDecimal> paidAmount,
      jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
    return switch (status) {
      case CANCELLED -> criteriaBuilder.isTrue(cancelled);
      case PAID ->
          criteriaBuilder.and(
              criteriaBuilder.isFalse(cancelled),
              criteriaBuilder.equal(paidAmount, originalAmount));
      case PARTIALLY_PAID ->
          criteriaBuilder.and(
              criteriaBuilder.isFalse(cancelled),
              criteriaBuilder.greaterThan(paidAmount, BigDecimal.ZERO),
              criteriaBuilder.lessThan(paidAmount, originalAmount));
      case OPEN ->
          criteriaBuilder.and(
              criteriaBuilder.isFalse(cancelled),
              criteriaBuilder.equal(paidAmount, BigDecimal.ZERO),
              criteriaBuilder.greaterThan(originalAmount, BigDecimal.ZERO));
    };
  }

  private static Predicate dueStatePredicate(
      InvoiceDueState dueState,
      LocalDate businessDate,
      Path<Boolean> cancelled,
      Path<BigDecimal> originalAmount,
      Path<BigDecimal> paidAmount,
      Path<LocalDate> dueDate,
      jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
    Predicate unsettled =
        criteriaBuilder.and(
            criteriaBuilder.isFalse(cancelled),
            criteriaBuilder.lessThan(paidAmount, originalAmount));
    return switch (dueState) {
      case OVERDUE ->
          criteriaBuilder.and(unsettled, criteriaBuilder.lessThan(dueDate, businessDate));
      case DUE_TODAY ->
          criteriaBuilder.and(unsettled, criteriaBuilder.equal(dueDate, businessDate));
      case NOT_DUE ->
          criteriaBuilder.and(unsettled, criteriaBuilder.greaterThan(dueDate, businessDate));
      case SETTLED ->
          criteriaBuilder.or(
              criteriaBuilder.isTrue(cancelled), criteriaBuilder.equal(paidAmount, originalAmount));
    };
  }

  private static PageRequest pageable(InvoiceQueryCriteria criteria) {
    Sort.Direction direction =
        criteria.sortDirection() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, property(criteria.sortField())).and(Sort.by(direction, "id"));
    return PageRequest.of(criteria.pageNumber(), criteria.pageSize(), sort);
  }

  private static String property(InvoiceSortField sortField) {
    return switch (sortField) {
      case ISSUE_DATE -> "issueDate";
      case DUE_DATE -> "dueDate";
      case INVOICE_NUMBER -> "invoiceNumber";
    };
  }
}
