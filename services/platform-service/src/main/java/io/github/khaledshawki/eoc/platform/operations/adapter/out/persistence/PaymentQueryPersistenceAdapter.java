package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
class PaymentQueryPersistenceAdapter implements PaymentQueryRepository {

  private final SpringDataPaymentRepository paymentRepository;
  private final PaymentPersistenceMapper persistenceMapper;

  PaymentQueryPersistenceAdapter(
      SpringDataPaymentRepository paymentRepository, PaymentPersistenceMapper persistenceMapper) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment JPA repository cannot be null");
    this.persistenceMapper =
        Objects.requireNonNull(persistenceMapper, "Payment mapper cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public PaymentQueryPage findPage(PaymentQueryCriteria criteria) {
    Objects.requireNonNull(criteria, "Payment query criteria cannot be null");
    Page<PaymentJpaEntity> page =
        paymentRepository.findAll(specification(criteria), pageable(criteria));
    return new PaymentQueryPage(
        page.getContent().stream().map(persistenceMapper::toDomain).toList(),
        criteria.pageNumber(),
        criteria.pageSize(),
        page.getTotalElements());
  }

  private static Specification<PaymentJpaEntity> specification(PaymentQueryCriteria criteria) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(criteriaBuilder.equal(root.get("tenantId"), criteria.tenantId().value()));
      criteria
          .customerId()
          .ifPresent(
              customerId ->
                  predicates.add(
                      criteriaBuilder.equal(root.get("customerId"), customerId.value())));

      Path<Boolean> reversed = root.get("reversed");
      if (!criteria.statuses().isEmpty()) {
        Predicate[] statuses =
            criteria.statuses().stream()
                .map(status -> statusPredicate(status, reversed, criteriaBuilder))
                .toArray(Predicate[]::new);
        predicates.add(criteriaBuilder.or(statuses));
      }

      Path<LocalDate> paymentDate = root.get("paymentDate");
      criteria
          .paymentDateFrom()
          .ifPresent(
              from -> predicates.add(criteriaBuilder.greaterThanOrEqualTo(paymentDate, from)));
      criteria
          .paymentDateTo()
          .ifPresent(to -> predicates.add(criteriaBuilder.lessThanOrEqualTo(paymentDate, to)));

      return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate statusPredicate(
      PaymentStatus status,
      Path<Boolean> reversed,
      jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
    return switch (status) {
      case RECORDED -> criteriaBuilder.isFalse(reversed);
      case REVERSED -> criteriaBuilder.isTrue(reversed);
    };
  }

  private static PageRequest pageable(PaymentQueryCriteria criteria) {
    Sort.Direction direction =
        criteria.sortDirection() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    Sort sort = Sort.by(direction, property(criteria.sortField())).and(Sort.by(direction, "id"));
    return PageRequest.of(criteria.pageNumber(), criteria.pageSize(), sort);
  }

  private static String property(PaymentSortField sortField) {
    return switch (sortField) {
      case PAYMENT_DATE -> "paymentDate";
    };
  }
}
