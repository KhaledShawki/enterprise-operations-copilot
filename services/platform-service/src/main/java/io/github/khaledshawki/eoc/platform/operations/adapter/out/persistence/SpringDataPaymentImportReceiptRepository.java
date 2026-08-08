package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentImportReceiptRepository
    extends JpaRepository<PaymentImportReceiptJpaEntity, PaymentImportReceiptJpaId> {}
