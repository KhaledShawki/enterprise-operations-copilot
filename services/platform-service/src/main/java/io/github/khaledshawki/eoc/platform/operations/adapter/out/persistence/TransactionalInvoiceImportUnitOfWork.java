package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportUnitOfWork;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class TransactionalInvoiceImportUnitOfWork implements InvoiceImportUnitOfWork {

  private final TransactionTemplate transactionTemplate;

  TransactionalInvoiceImportUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactionTemplate =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "Transaction manager cannot be null"));
  }

  @Override
  public InvoiceImportResult execute(Supplier<InvoiceImportResult> work) {
    Objects.requireNonNull(work, "Invoice import work cannot be null");
    InvoiceImportResult result = transactionTemplate.execute(status -> work.get());
    return Objects.requireNonNull(result, "Invoice import transaction returned null");
  }
}
