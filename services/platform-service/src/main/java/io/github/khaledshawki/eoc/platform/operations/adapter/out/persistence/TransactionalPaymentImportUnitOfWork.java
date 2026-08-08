package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportUnitOfWork;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransactionalPaymentImportUnitOfWork implements PaymentImportUnitOfWork {

  private final TransactionTemplate transactionTemplate;

  TransactionalPaymentImportUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactionTemplate =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "Transaction manager cannot be null"));
  }

  @Override
  public PaymentImportResult execute(Supplier<PaymentImportResult> work) {
    Objects.requireNonNull(work, "Payment import work cannot be null");
    PaymentImportResult result = transactionTemplate.execute(status -> work.get());
    return Objects.requireNonNull(result, "Payment import transaction returned null");
  }
}
