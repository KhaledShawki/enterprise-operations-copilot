package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Local cash-application aggregate for one canonical customer Payment.
 *
 * <p>The settlement owns local Invoice allocation history. It deliberately does not mutate or use
 * {@link Invoice#paidAmount()} as allocation truth: that value belongs to the authoritative Invoice
 * source snapshot. Local allocation totals are compared with source-paid evidence later through a
 * separate reconciliation model.
 */
public final class ReceivableSettlement {

  private final ReceivableSettlementId id;
  private final OperationsTenantId tenantId;
  private final BusinessPartnerId customerId;
  private final PaymentId paymentId;
  private final CurrencyCode currency;
  private final List<ReceivableAllocation> allocations;

  private ReceivableSettlement(
      ReceivableSettlementId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      PaymentId paymentId,
      CurrencyCode currency,
      List<ReceivableAllocation> allocations) {
    this.id = Objects.requireNonNull(id, "Receivable settlement id cannot be null");
    this.tenantId =
        Objects.requireNonNull(tenantId, "Receivable settlement tenant id cannot be null");
    this.customerId =
        Objects.requireNonNull(customerId, "Receivable settlement customer id cannot be null");
    this.paymentId =
        Objects.requireNonNull(paymentId, "Receivable settlement payment id cannot be null");
    this.currency =
        Objects.requireNonNull(currency, "Receivable settlement currency cannot be null");
    this.allocations = validateAllocations(currency, allocations);
  }

  public static ReceivableSettlement open(Payment payment) {
    Objects.requireNonNull(payment, "Settlement payment cannot be null");
    if (payment.reversed()) {
      throw new IllegalArgumentException("Cannot open a settlement for a reversed payment");
    }
    return new ReceivableSettlement(
        ReceivableSettlementId.generate(),
        payment.tenantId(),
        payment.customerId(),
        payment.id(),
        payment.amount().currency(),
        List.of());
  }

  public static ReceivableSettlement reconstitute(
      ReceivableSettlementId id,
      OperationsTenantId tenantId,
      BusinessPartnerId customerId,
      PaymentId paymentId,
      CurrencyCode currency,
      List<ReceivableAllocation> allocations) {
    return new ReceivableSettlement(id, tenantId, customerId, paymentId, currency, allocations);
  }

  public ReceivableAllocation allocate(
      ReceivableAllocationId allocationId, Payment payment, Invoice invoice, Money amount) {
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
    Objects.requireNonNull(payment, "Settlement payment cannot be null");
    Objects.requireNonNull(invoice, "Settlement invoice cannot be null");
    Objects.requireNonNull(amount, "Receivable allocation amount cannot be null");

    validatePaymentCompatibility(payment);
    validateInvoiceCompatibility(invoice);
    validateAllocationAmount(amount);
    requireUnusedAllocationId(allocationId);

    Money resultingPaymentAllocation = allocatedAmount().add(amount);
    if (resultingPaymentAllocation.compareTo(payment.effectiveAmount()) > 0) {
      throw new IllegalArgumentException(
          "Receivable allocations cannot exceed the payment effective amount");
    }

    Money resultingInvoiceAllocation = allocatedAmountForInvoice(invoice.id()).add(amount);
    if (resultingInvoiceAllocation.compareTo(invoice.originalAmount()) > 0) {
      throw new IllegalArgumentException(
          "Receivable allocations cannot exceed the invoice original amount");
    }

    ReceivableAllocation allocation =
        ReceivableAllocation.active(allocationId, invoice.id(), amount);
    allocations.add(allocation);
    return allocation;
  }

  public ReceivableAllocation reverseAllocation(ReceivableAllocationId allocationId) {
    Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null");
    for (int index = 0; index < allocations.size(); index++) {
      ReceivableAllocation current = allocations.get(index);
      if (current.id().equals(allocationId)) {
        ReceivableAllocation reversed = current.reverse();
        allocations.set(index, reversed);
        return reversed;
      }
    }
    throw new IllegalArgumentException("Receivable allocation does not belong to this settlement");
  }

  public Money allocatedAmount() {
    Money total = Money.zero(currency);
    for (ReceivableAllocation allocation : allocations) {
      if (allocation.active()) {
        total = total.add(allocation.amount());
      }
    }
    return total;
  }

  public Money allocatedAmountForInvoice(InvoiceId invoiceId) {
    Objects.requireNonNull(invoiceId, "Invoice id cannot be null");
    Money total = Money.zero(currency);
    for (ReceivableAllocation allocation : allocations) {
      if (allocation.active() && allocation.invoiceId().equals(invoiceId)) {
        total = total.add(allocation.amount());
      }
    }
    return total;
  }

  public Money unappliedAmount(Payment payment) {
    Objects.requireNonNull(payment, "Settlement payment cannot be null");
    validatePaymentIdentity(payment);
    Money unapplied = payment.effectiveAmount().subtract(allocatedAmount());
    if (unapplied.isNegative()) {
      throw new IllegalStateException(
          "Receivable allocations exceed the current payment effective amount");
    }
    return unapplied;
  }

  public ReceivableSettlementId id() {
    return id;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public BusinessPartnerId customerId() {
    return customerId;
  }

  public PaymentId paymentId() {
    return paymentId;
  }

  public CurrencyCode currency() {
    return currency;
  }

  public List<ReceivableAllocation> allocations() {
    return List.copyOf(allocations);
  }

  private void validatePaymentCompatibility(Payment payment) {
    validatePaymentIdentity(payment);
    if (payment.reversed()) {
      throw new IllegalArgumentException("Cannot allocate a reversed payment");
    }
  }

  private void validatePaymentIdentity(Payment payment) {
    if (!payment.id().equals(paymentId)) {
      throw new IllegalArgumentException("Payment does not belong to this settlement");
    }
    if (!payment.tenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Payment tenant does not match settlement tenant");
    }
    if (!payment.customerId().equals(customerId)) {
      throw new IllegalArgumentException("Payment customer does not match settlement customer");
    }
    if (!payment.amount().currency().equals(currency)) {
      throw new IllegalArgumentException("Payment currency does not match settlement currency");
    }
  }

  private void validateInvoiceCompatibility(Invoice invoice) {
    if (!invoice.tenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Invoice tenant does not match settlement tenant");
    }
    if (!invoice.customerId().equals(customerId)) {
      throw new IllegalArgumentException("Invoice customer does not match settlement customer");
    }
    if (!invoice.originalAmount().currency().equals(currency)) {
      throw new IllegalArgumentException("Invoice currency does not match settlement currency");
    }
    if (invoice.cancelled()) {
      throw new IllegalArgumentException("Cannot allocate a cancelled invoice");
    }
  }

  private void validateAllocationAmount(Money amount) {
    if (!amount.currency().equals(currency)) {
      throw new IllegalArgumentException("Allocation currency does not match settlement currency");
    }
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Receivable allocation amount must be positive");
    }
  }

  private void requireUnusedAllocationId(ReceivableAllocationId allocationId) {
    if (allocations.stream().anyMatch(allocation -> allocation.id().equals(allocationId))) {
      throw new IllegalArgumentException(
          "Receivable allocation id is already used by this settlement");
    }
  }

  private static List<ReceivableAllocation> validateAllocations(
      CurrencyCode currency, List<ReceivableAllocation> allocations) {
    Objects.requireNonNull(allocations, "Receivable settlement allocations cannot be null");
    ArrayList<ReceivableAllocation> validated = new ArrayList<>(allocations.size());
    Set<ReceivableAllocationId> allocationIds = new HashSet<>();
    for (ReceivableAllocation allocation : allocations) {
      Objects.requireNonNull(allocation, "Receivable settlement allocation cannot be null");
      if (!allocation.amount().currency().equals(currency)) {
        throw new IllegalArgumentException(
            "Receivable allocation currency does not match settlement currency");
      }
      if (!allocationIds.add(allocation.id())) {
        throw new IllegalArgumentException(
            "Receivable settlement cannot contain duplicate allocation ids");
      }
      validated.add(allocation);
    }
    return validated;
  }
}
