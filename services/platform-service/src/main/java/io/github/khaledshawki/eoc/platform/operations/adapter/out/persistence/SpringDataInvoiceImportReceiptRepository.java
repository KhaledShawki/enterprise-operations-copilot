package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataInvoiceImportReceiptRepository
    extends JpaRepository<InvoiceImportReceiptJpaEntity, InvoiceImportReceiptJpaId> {}
