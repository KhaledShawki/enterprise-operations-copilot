package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KafkaAnalyticsIntegrationEventDecoderTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000181");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID PARTNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000182");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000183");

  private final KafkaAnalyticsIntegrationEventDecoder decoder =
      new KafkaAnalyticsIntegrationEventDecoder(JsonMapper.builder().build(), 32_768);

  @Test
  void decodesBusinessPartnerIntoAnalyticsOwnedProjectionFacts() {
    AnalyticsIntegrationEvent event = decoder.decode(businessPartnerEnvelope());

    assertEquals(EVENT_ID, event.eventId());
    assertEquals(TENANT_ID, event.tenantId());
    assertEquals(PARTNER_ID, event.aggregateId());
    assertEquals(7, event.aggregateVersion());
    AnalyticsProjectionPayload.BusinessPartner payload =
        assertInstanceOf(
            AnalyticsProjectionPayload.BusinessPartner.class, event.projectionPayload());
    assertEquals(PARTNER_ID, payload.businessPartnerId());
    assertEquals("C-100", payload.partnerNumber());
    assertEquals("Acme AG", payload.displayName());
    assertEquals(java.util.Set.of("CUSTOMER"), payload.roles());
  }

  @Test
  void decodesCanonicalInvoiceFacts() {
    AnalyticsIntegrationEvent event = decoder.decode(invoiceEnvelope());

    AnalyticsProjectionPayload.InvoiceReceivable payload =
        assertInstanceOf(
            AnalyticsProjectionPayload.InvoiceReceivable.class, event.projectionPayload());
    assertEquals(INVOICE_ID, payload.invoiceId());
    assertEquals(new BigDecimal("100.00"), payload.originalAmount());
    assertEquals(new BigDecimal("20.00"), payload.paidAmount());
    assertEquals("CHF", payload.currency());
    assertEquals("PARTIALLY_PAID", payload.status());
  }

  @Test
  void recognizesValidPaymentAsKnownButCurrentlyIgnored() {
    AnalyticsIntegrationEvent event = decoder.decode(paymentEnvelope());

    assertInstanceOf(AnalyticsProjectionPayload.Ignored.class, event.projectionPayload());
    assertEquals("PAYMENT", event.aggregateType());
  }

  @Test
  void rejectsUnknownVersionsAndAggregateContractMismatches() {
    assertTerminal(
        businessPartnerEnvelope().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
        KafkaAnalyticsIntegrationEventDecoder.UNSUPPORTED_CONTRACT);
    assertTerminal(
        businessPartnerEnvelope()
            .replace("\"aggregateType\":\"BUSINESS_PARTNER\"", "\"aggregateType\":\"INVOICE\""),
        KafkaAnalyticsIntegrationEventDecoder.UNSUPPORTED_CONTRACT);
  }

  @Test
  void rejectsInvalidPayloadFactsBeforeApplicationProcessing() {
    assertTerminal(
        invoiceEnvelope().replace("\"status\":\"PARTIALLY_PAID\"", "\"status\":\"PAID\""),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_PAYLOAD);
    assertTerminal(
        businessPartnerEnvelope().replace("\"roles\":[\"CUSTOMER\"]", "\"roles\":[\"customer\"]"),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_PAYLOAD);
    assertTerminal(
        businessPartnerEnvelope().replace("SOURCE_RECORD_ID", "EXTERNAL_ID"),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_PAYLOAD);
    assertTerminal(
        businessPartnerEnvelope()
            .replace(
                "\"businessPartnerId\":\"" + PARTNER_ID + "\"",
                "\"businessPartnerId\":\"" + UUID.randomUUID() + "\""),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_PAYLOAD);
  }

  @Test
  void rejectsMalformedOrExtendedEnvelopes() {
    assertTerminal(
        businessPartnerEnvelope()
            .replace("\"occurredAt\"", "\"claimOwner\":\"worker\",\"occurredAt\""),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_ENVELOPE);
    assertTerminal(
        businessPartnerEnvelope().replace("\"aggregateVersion\":7", "\"aggregateVersion\":0"),
        KafkaAnalyticsIntegrationEventDecoder.INVALID_ENVELOPE);
    assertTerminal("not-json", KafkaAnalyticsIntegrationEventDecoder.INVALID_ENVELOPE);
    assertTerminal(null, KafkaAnalyticsIntegrationEventDecoder.INVALID_ENVELOPE);
  }

  @Test
  void enforcesUtf8ByteLimit() {
    KafkaAnalyticsIntegrationEventDecoder sizeLimited =
        new KafkaAnalyticsIntegrationEventDecoder(JsonMapper.builder().build(), 10);

    TerminalAnalyticsKafkaConsumptionException exception =
        assertThrows(
            TerminalAnalyticsKafkaConsumptionException.class,
            () -> sizeLimited.decode(businessPartnerEnvelope()));

    assertEquals(KafkaAnalyticsIntegrationEventDecoder.EVENT_TOO_LARGE, exception.failureCode());
    assertFalse(exception.retryable());
  }

  private void assertTerminal(String value, String failureCode) {
    TerminalAnalyticsKafkaConsumptionException exception =
        assertThrows(TerminalAnalyticsKafkaConsumptionException.class, () -> decoder.decode(value));
    assertEquals(failureCode, exception.failureCode());
    assertFalse(exception.retryable());
  }

  private static String businessPartnerEnvelope() {
    return """
        {
          "eventId":"00000000-0000-0000-0000-000000000181",
          "eventType":"operations.business-partner.synchronized.v1",
          "schemaVersion":1,
          "tenantId":"00000000-0000-0000-0000-000000000010",
          "aggregateType":"BUSINESS_PARTNER",
          "aggregateId":"00000000-0000-0000-0000-000000000182",
          "aggregateVersion":7,
          "payload":{
            "businessPartnerId":"00000000-0000-0000-0000-000000000182",
            "partnerNumber":"C-100",
            "displayName":"Acme AG",
            "roles":["CUSTOMER"],
            "source":{
              "sourceSystemId":"00000000-0000-0000-0000-000000000090",
              "sourceIdentityKind":"SOURCE_RECORD_ID",
              "sourceIdentity":"BP-100",
              "sourceVersion":"7",
              "sourceModifiedAt":"2026-08-11T20:00:00Z"
            }
          },
          "occurredAt":"2026-08-11T20:00:01Z"
        }
        """;
  }

  private static String invoiceEnvelope() {
    return """
        {
          "eventId":"00000000-0000-0000-0000-000000000184",
          "eventType":"operations.invoice.synchronized.v1",
          "schemaVersion":1,
          "tenantId":"00000000-0000-0000-0000-000000000010",
          "aggregateType":"INVOICE",
          "aggregateId":"00000000-0000-0000-0000-000000000183",
          "aggregateVersion":2,
          "payload":{
            "invoiceId":"00000000-0000-0000-0000-000000000183",
            "customerId":"00000000-0000-0000-0000-000000000182",
            "invoiceNumber":"INV-100",
            "originalAmount":{"amount":100.00,"currency":"CHF"},
            "paidAmount":{"amount":20.00,"currency":"CHF"},
            "issueDate":"2026-07-01",
            "dueDate":"2026-07-31",
            "cancelled":false,
            "status":"PARTIALLY_PAID",
            "source":{
              "sourceSystemId":"00000000-0000-0000-0000-000000000090",
              "sourceIdentityKind":"SOURCE_RECORD_ID",
              "sourceIdentity":"INV-100",
              "sourceVersion":"2",
              "sourceModifiedAt":null
            }
          },
          "occurredAt":"2026-08-11T20:00:02Z"
        }
        """;
  }

  private static String paymentEnvelope() {
    return """
        {
          "eventId":"00000000-0000-0000-0000-000000000185",
          "eventType":"operations.payment.synchronized.v1",
          "schemaVersion":1,
          "tenantId":"00000000-0000-0000-0000-000000000010",
          "aggregateType":"PAYMENT",
          "aggregateId":"00000000-0000-0000-0000-000000000186",
          "aggregateVersion":1,
          "payload":{
            "paymentId":"00000000-0000-0000-0000-000000000186",
            "customerId":"00000000-0000-0000-0000-000000000182",
            "amount":{"amount":20.00,"currency":"CHF"},
            "paymentDate":"2026-08-11",
            "reversed":false,
            "status":"RECORDED",
            "source":{
              "sourceSystemId":"00000000-0000-0000-0000-000000000090",
              "sourceIdentityKind":"SOURCE_RECORD_ID",
              "sourceIdentity":"PAY-100",
              "sourceVersion":"1",
              "sourceModifiedAt":null
            }
          },
          "occurredAt":"2026-08-11T20:00:03Z"
        }
        """;
  }
}
