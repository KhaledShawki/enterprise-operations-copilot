package io.github.khaledshawki.eoc.operations.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsEventPayloadTest {

  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 1);
  private static final LocalDate DUE_DATE = LocalDate.of(2026, 8, 31);

  @Test
  void shouldNormalizeMoneyAndSourceEvidenceIntoPortableValues() {
    OperationsMoneyPayload money = new OperationsMoneyPayload(new BigDecimal("10"), "eur");
    SourceRecordEvidence source =
        SourceRecordEvidence.from(
            SourceSystemId.of(SOURCE_SYSTEM_ID),
            SourceRecordIdentity.canonicalRecordHash("A".repeat(64)),
            new SourceRecordVersion(" source-version "),
            Optional.of(Instant.parse("2026-08-01T07:00:00Z")));

    assertEquals(new BigDecimal("10.00"), money.amount());
    assertEquals("EUR", money.currency());
    assertEquals("CANONICAL_RECORD_HASH", source.sourceIdentityKind());
    assertEquals("a".repeat(64), source.sourceIdentity());
    assertEquals("source-version", source.sourceVersion());
  }

  @Test
  void shouldRejectUnsupportedSourceEvidence() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceRecordEvidence(
                SOURCE_SYSTEM_ID, "UNSUPPORTED", "source-1", "v1", Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceRecordEvidence(
                SOURCE_SYSTEM_ID, "SOURCE_RECORD_ID", " ", "v1", Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            new SourceRecordEvidence(SOURCE_SYSTEM_ID, "SOURCE_RECORD_ID", "source-1", "v1", null));
  }

  @Test
  void shouldCanonicalizeBusinessPartnerRoles() {
    BusinessPartnerSynchronizedPayload payload =
        new BusinessPartnerSynchronizedPayload(
            AGGREGATE_ID,
            " BP-100 ",
            " Example Customer ",
            List.of("VENDOR", "CUSTOMER"),
            source());

    assertEquals("BP-100", payload.partnerNumber());
    assertEquals("Example Customer", payload.displayName());
    assertEquals(List.of("CUSTOMER", "VENDOR"), payload.roles());
    assertEquals(AGGREGATE_ID, payload.aggregateId());
  }

  @Test
  void shouldRejectDuplicateOrMalformedBusinessPartnerRoles() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BusinessPartnerSynchronizedPayload(
                AGGREGATE_ID,
                "BP-100",
                "Example Customer",
                List.of("CUSTOMER", "CUSTOMER"),
                source()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BusinessPartnerSynchronizedPayload(
                AGGREGATE_ID, "BP-100", "Example Customer", List.of("customer"), source()));
  }

  @Test
  void shouldAcceptCanonicalInvoicePayload() {
    InvoiceSynchronizedPayload payload = invoicePayload("100.00", "25.00", false, "PARTIALLY_PAID");

    assertEquals(AGGREGATE_ID, payload.aggregateId());
    assertEquals("PARTIALLY_PAID", payload.status());
  }

  @Test
  void shouldRejectInvoiceFactsThatConflictWithStatusMoneyOrDates() {
    assertThrows(
        IllegalArgumentException.class, () -> invoicePayload("100.00", "25.00", false, "PAID"));
    assertThrows(
        IllegalArgumentException.class, () -> invoicePayload("100.00", "100.01", false, "PAID"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InvoiceSynchronizedPayload(
                AGGREGATE_ID,
                CUSTOMER_ID,
                "INV-100",
                money("100.00", "EUR"),
                money("25.00", "USD"),
                ISSUE_DATE,
                DUE_DATE,
                false,
                "PARTIALLY_PAID",
                source()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InvoiceSynchronizedPayload(
                AGGREGATE_ID,
                CUSTOMER_ID,
                "INV-100",
                money("100.00", "EUR"),
                money("25.00", "EUR"),
                DUE_DATE,
                ISSUE_DATE,
                false,
                "PARTIALLY_PAID",
                source()));
  }

  @Test
  void shouldRejectPaymentStatusOrAmountThatConflictsWithFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentSynchronizedPayload(
                AGGREGATE_ID,
                CUSTOMER_ID,
                money("10.00", "EUR"),
                ISSUE_DATE,
                true,
                "RECORDED",
                source()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentSynchronizedPayload(
                AGGREGATE_ID,
                CUSTOMER_ID,
                money("0.00", "EUR"),
                ISSUE_DATE,
                false,
                "RECORDED",
                source()));
  }

  @Test
  void shouldRejectNonPositiveAllocationPayload() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableAllocationAppliedPayload(
                AGGREGATE_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                money("0.00", "EUR")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReceivableAllocationReversedPayload(
                AGGREGATE_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                money("-1.00", "EUR")));
  }

  @Test
  void shouldRejectUnsupportedMoneyPrecision() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OperationsMoneyPayload(new BigDecimal("10.001"), "EUR"));
  }

  private static InvoiceSynchronizedPayload invoicePayload(
      String originalAmount, String paidAmount, boolean cancelled, String status) {
    return new InvoiceSynchronizedPayload(
        AGGREGATE_ID,
        CUSTOMER_ID,
        "INV-100",
        money(originalAmount, "EUR"),
        money(paidAmount, "EUR"),
        ISSUE_DATE,
        DUE_DATE,
        cancelled,
        status,
        source());
  }

  private static OperationsMoneyPayload money(String amount, String currency) {
    return OperationsMoneyPayload.from(Money.of(amount, CurrencyCode.of(currency)));
  }

  private static SourceRecordEvidence source() {
    return SourceRecordEvidence.from(
        SourceSystemId.of(SOURCE_SYSTEM_ID),
        SourceRecordIdentity.sourceRecordId("source-100"),
        new SourceRecordVersion("v1"),
        Optional.empty());
  }
}
