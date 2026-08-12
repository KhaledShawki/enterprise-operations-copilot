package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class KafkaAnalyticsIntegrationEventDecoder {

  static final String INVALID_ENVELOPE = "kafka-analytics-event-envelope-invalid";
  static final String INVALID_PAYLOAD = "kafka-analytics-event-payload-invalid";
  static final String UNSUPPORTED_CONTRACT = "kafka-analytics-event-contract-unsupported";
  static final String EVENT_TOO_LARGE = "kafka-analytics-event-too-large";

  private static final String BUSINESS_PARTNER_EVENT =
      "operations.business-partner.synchronized.v1";
  private static final String INVOICE_EVENT = "operations.invoice.synchronized.v1";
  private static final String PAYMENT_EVENT = "operations.payment.synchronized.v1";
  private static final String ALLOCATION_APPLIED_EVENT =
      "operations.receivable-allocation.applied.v1";
  private static final String ALLOCATION_REVERSED_EVENT =
      "operations.receivable-allocation.reversed.v1";

  private static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "eventId",
          "eventType",
          "schemaVersion",
          "tenantId",
          "aggregateType",
          "aggregateId",
          "aggregateVersion",
          "payload",
          "occurredAt");
  private static final Set<String> BUSINESS_PARTNER_FIELDS =
      Set.of("businessPartnerId", "partnerNumber", "displayName", "roles", "source");
  private static final Set<String> INVOICE_FIELDS =
      Set.of(
          "invoiceId",
          "customerId",
          "invoiceNumber",
          "originalAmount",
          "paidAmount",
          "issueDate",
          "dueDate",
          "cancelled",
          "status",
          "source");
  private static final Set<String> PAYMENT_FIELDS =
      Set.of("paymentId", "customerId", "amount", "paymentDate", "reversed", "status", "source");
  private static final Set<String> ALLOCATION_FIELDS =
      Set.of("settlementId", "paymentId", "allocationId", "invoiceId", "amount");
  private static final Set<String> MONEY_FIELDS = Set.of("amount", "currency");
  private static final Set<String> SOURCE_FIELDS =
      Set.of(
          "sourceSystemId",
          "sourceIdentityKind",
          "sourceIdentity",
          "sourceVersion",
          "sourceModifiedAt");
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final Pattern SHA_256_HEX = Pattern.compile("[a-f0-9]{64}");

  private final JsonMapper jsonMapper;
  private final int maxEventBytes;

  KafkaAnalyticsIntegrationEventDecoder(JsonMapper jsonMapper, int maxEventBytes) {
    if (jsonMapper == null) {
      throw new NullPointerException("JSON mapper cannot be null");
    }
    if (maxEventBytes < 1) {
      throw new IllegalArgumentException("Maximum Analytics event bytes must be positive");
    }
    this.jsonMapper = jsonMapper;
    this.maxEventBytes = maxEventBytes;
  }

  AnalyticsIntegrationEvent decode(String value) {
    if (value == null || value.isBlank()) {
      throw terminal(INVALID_ENVELOPE, null);
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > maxEventBytes) {
      throw terminal(EVENT_TOO_LARGE, null);
    }

    try {
      JsonNode root = jsonMapper.readTree(value);
      requireExactObject(root, ENVELOPE_FIELDS, "Kafka Analytics event envelope");

      UUID eventId = parseText(root, "eventId", UUID::fromString);
      String eventType = text(root, "eventType");
      int schemaVersion = integer(root, "schemaVersion");
      UUID tenantId = parseText(root, "tenantId", UUID::fromString);
      String aggregateType = text(root, "aggregateType");
      UUID aggregateId = parseText(root, "aggregateId", UUID::fromString);
      long aggregateVersion = positiveLong(root, "aggregateVersion");
      JsonNode payload = required(root, "payload");
      if (!payload.isObject()) {
        throw new IllegalArgumentException("Kafka Analytics event payload must be an object");
      }
      Instant occurredAt = parseText(root, "occurredAt", Instant::parse);
      String canonicalPayload = jsonMapper.writeValueAsString(payload);

      AnalyticsProjectionPayload projectionPayload =
          decodePayload(eventType, schemaVersion, aggregateType, aggregateId, payload);

      return new AnalyticsIntegrationEvent(
          eventId,
          eventType,
          schemaVersion,
          tenantId,
          aggregateType,
          aggregateId,
          aggregateVersion,
          canonicalPayload,
          occurredAt,
          projectionPayload);
    } catch (AnalyticsKafkaConsumptionException exception) {
      throw exception;
    } catch (JacksonException | IllegalArgumentException | DateTimeParseException exception) {
      throw terminal(INVALID_ENVELOPE, exception);
    }
  }

  private AnalyticsProjectionPayload decodePayload(
      String eventType,
      int schemaVersion,
      String aggregateType,
      UUID aggregateId,
      JsonNode payload) {
    if (schemaVersion != 1) {
      throw terminal(UNSUPPORTED_CONTRACT, null);
    }

    try {
      return switch (eventType) {
        case BUSINESS_PARTNER_EVENT -> {
          requireAggregateType(aggregateType, "BUSINESS_PARTNER");
          requireExactObject(payload, BUSINESS_PARTNER_FIELDS, "Business partner payload");
          validateSource(required(payload, "source"));
          UUID businessPartnerId = parseText(payload, "businessPartnerId", UUID::fromString);
          requireAggregateId(aggregateId, businessPartnerId);
          String partnerNumber = boundedText(payload, "partnerNumber", 100);
          String displayName = boundedText(payload, "displayName", 255);
          Set<String> roles = roles(payload);
          yield new AnalyticsProjectionPayload.BusinessPartner(
              businessPartnerId, partnerNumber, displayName, roles);
        }
        case INVOICE_EVENT -> {
          requireAggregateType(aggregateType, "INVOICE");
          requireExactObject(payload, INVOICE_FIELDS, "Invoice payload");
          validateSource(required(payload, "source"));
          UUID invoiceId = parseText(payload, "invoiceId", UUID::fromString);
          requireAggregateId(aggregateId, invoiceId);
          UUID customerId = parseText(payload, "customerId", UUID::fromString);
          String invoiceNumber = boundedText(payload, "invoiceNumber", 100);
          Money original = money(required(payload, "originalAmount"), false);
          Money paid = money(required(payload, "paidAmount"), false);
          if (!original.currency().equals(paid.currency())) {
            throw new IllegalArgumentException("Invoice currencies must match");
          }
          if (paid.amount().compareTo(original.amount()) > 0) {
            throw new IllegalArgumentException("Invoice paid amount cannot exceed original amount");
          }
          LocalDate issueDate = parseText(payload, "issueDate", LocalDate::parse);
          LocalDate dueDate = parseText(payload, "dueDate", LocalDate::parse);
          if (dueDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Invoice due date cannot precede issue date");
          }
          boolean cancelled = bool(payload, "cancelled");
          String status = text(payload, "status");
          requireInvoiceStatus(original.amount(), paid.amount(), cancelled, status);
          yield new AnalyticsProjectionPayload.InvoiceReceivable(
              invoiceId,
              customerId,
              invoiceNumber,
              original.amount(),
              paid.amount(),
              original.currency(),
              issueDate,
              dueDate,
              cancelled,
              status);
        }
        case PAYMENT_EVENT -> {
          requireAggregateType(aggregateType, "PAYMENT");
          requireExactObject(payload, PAYMENT_FIELDS, "Payment payload");
          validateSource(required(payload, "source"));
          UUID paymentId = parseText(payload, "paymentId", UUID::fromString);
          requireAggregateId(aggregateId, paymentId);
          parseText(payload, "customerId", UUID::fromString);
          money(required(payload, "amount"), true);
          parseText(payload, "paymentDate", LocalDate::parse);
          boolean reversed = bool(payload, "reversed");
          String status = text(payload, "status");
          String expectedStatus = reversed ? "REVERSED" : "RECORDED";
          if (!expectedStatus.equals(status)) {
            throw new IllegalArgumentException("Payment status does not match canonical facts");
          }
          yield new AnalyticsProjectionPayload.Ignored();
        }
        case ALLOCATION_APPLIED_EVENT, ALLOCATION_REVERSED_EVENT -> {
          requireAggregateType(aggregateType, "RECEIVABLE_SETTLEMENT");
          requireExactObject(payload, ALLOCATION_FIELDS, "Receivable allocation payload");
          UUID settlementId = parseText(payload, "settlementId", UUID::fromString);
          requireAggregateId(aggregateId, settlementId);
          parseText(payload, "paymentId", UUID::fromString);
          parseText(payload, "allocationId", UUID::fromString);
          parseText(payload, "invoiceId", UUID::fromString);
          money(required(payload, "amount"), true);
          yield new AnalyticsProjectionPayload.Ignored();
        }
        default -> throw terminal(UNSUPPORTED_CONTRACT, null);
      };
    } catch (AnalyticsKafkaConsumptionException exception) {
      throw exception;
    } catch (IllegalArgumentException | NullPointerException | DateTimeParseException exception) {
      throw terminal(INVALID_PAYLOAD, exception);
    }
  }

  private static void requireInvoiceStatus(
      BigDecimal original, BigDecimal paid, boolean cancelled, String status) {
    String expected;
    if (cancelled) {
      expected = "CANCELLED";
    } else if (paid.compareTo(original) == 0) {
      expected = "PAID";
    } else if (paid.signum() > 0) {
      expected = "PARTIALLY_PAID";
    } else {
      expected = "OPEN";
    }
    if (!expected.equals(status)) {
      throw new IllegalArgumentException("Invoice status does not match canonical facts");
    }
  }

  private static Set<String> roles(JsonNode payload) {
    JsonNode node = required(payload, "roles");
    if (!node.isArray() || node.size() == 0) {
      throw new IllegalArgumentException("Business partner roles must be a non-empty array");
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    for (JsonNode roleNode : node) {
      if (!roleNode.isTextual() || !ROLE.matcher(roleNode.textValue()).matches()) {
        throw new IllegalArgumentException("Business partner role is invalid");
      }
      if (!roles.add(roleNode.textValue())) {
        throw new IllegalArgumentException("Business partner roles cannot contain duplicates");
      }
    }
    return Set.copyOf(roles);
  }

  private static Money money(JsonNode node, boolean positive) {
    requireExactObject(node, MONEY_FIELDS, "Money payload");
    JsonNode amountNode = required(node, "amount");
    if (!amountNode.isNumber()) {
      throw new IllegalArgumentException("Money amount must be numeric");
    }
    BigDecimal amount = amountNode.decimalValue();
    String currency = text(node, "currency").strip().toUpperCase(Locale.ROOT);
    Currency currencyDefinition = Currency.getInstance(currency);
    if (currencyDefinition.getDefaultFractionDigits() < 0) {
      throw new IllegalArgumentException("Currency must define monetary fraction digits");
    }
    try {
      amount =
          amount.setScale(currencyDefinition.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Money amount has unsupported precision for currency " + currency, exception);
    }
    if (positive ? amount.signum() <= 0 : amount.signum() < 0) {
      throw new IllegalArgumentException(
          positive ? "Money amount must be positive" : "Money amount cannot be negative");
    }
    return new Money(amount, currency);
  }

  private static void validateSource(JsonNode source) {
    requireExactObject(source, SOURCE_FIELDS, "Source evidence");
    parseText(source, "sourceSystemId", UUID::fromString);
    String identityKind = requiredNonBlankText(source, "sourceIdentityKind");
    if (!identityKind.equals("SOURCE_RECORD_ID") && !identityKind.equals("CANONICAL_RECORD_HASH")) {
      throw new IllegalArgumentException("Source identity kind is not supported");
    }
    String identity = boundedText(source, "sourceIdentity", 255);
    if (identityKind.equals("CANONICAL_RECORD_HASH") && !SHA_256_HEX.matcher(identity).matches()) {
      throw new IllegalArgumentException("Canonical source identity must be a SHA-256 hex value");
    }
    boundedText(source, "sourceVersion", 512);
    JsonNode modifiedAt = source.get("sourceModifiedAt");
    if (modifiedAt == null) {
      throw new IllegalArgumentException("Source evidence is missing sourceModifiedAt");
    }
    if (!modifiedAt.isNull()) {
      if (!modifiedAt.isTextual()) {
        throw new IllegalArgumentException("Source modified timestamp must be text or null");
      }
      Instant.parse(modifiedAt.textValue());
    }
  }

  private static void requireAggregateType(String actual, String expected) {
    if (!expected.equals(actual)) {
      throw terminal(UNSUPPORTED_CONTRACT, null);
    }
  }

  private static void requireAggregateId(UUID envelopeId, UUID payloadId) {
    if (!envelopeId.equals(payloadId)) {
      throw new IllegalArgumentException("Payload aggregate id does not match the envelope");
    }
  }

  private static void requireExactObject(JsonNode node, Set<String> fields, String description) {
    if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(fields)) {
      throw new IllegalArgumentException(description + " must contain exactly the contract fields");
    }
  }

  private static JsonNode required(JsonNode root, String name) {
    JsonNode node = root.get(name);
    if (node == null || node.isNull()) {
      throw new IllegalArgumentException("Kafka Analytics event field is missing: " + name);
    }
    return node;
  }

  private static String text(JsonNode root, String name) {
    JsonNode node = required(root, name);
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Kafka Analytics event field must be text: " + name);
    }
    return node.textValue();
  }

  private static String requiredNonBlankText(JsonNode root, String name) {
    String value = text(root, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Kafka Analytics event field cannot be blank: " + name);
    }
    return value;
  }

  private static String boundedText(JsonNode root, String name, int maxLength) {
    String value = requiredNonBlankText(root, name);
    if (value.length() > maxLength || !value.equals(value.strip())) {
      throw new IllegalArgumentException(
          "Kafka Analytics event text field is not canonical: " + name);
    }
    return value;
  }

  private static int integer(JsonNode root, String name) {
    JsonNode node = required(root, name);
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw new IllegalArgumentException("Kafka Analytics event field must be an integer: " + name);
    }
    return node.intValue();
  }

  private static long positiveLong(JsonNode root, String name) {
    JsonNode node = required(root, name);
    if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 1) {
      throw new IllegalArgumentException(
          "Kafka Analytics event field must be a positive long: " + name);
    }
    return node.longValue();
  }

  private static boolean bool(JsonNode root, String name) {
    JsonNode node = required(root, name);
    if (!node.isBoolean()) {
      throw new IllegalArgumentException("Kafka Analytics event field must be boolean: " + name);
    }
    return node.booleanValue();
  }

  private static <T> T parseText(JsonNode root, String name, Function<String, T> parser) {
    return parser.apply(text(root, name));
  }

  private static TerminalAnalyticsKafkaConsumptionException terminal(
      String failureCode, Throwable cause) {
    return new TerminalAnalyticsKafkaConsumptionException(failureCode, cause);
  }

  private record Money(BigDecimal amount, String currency) {}
}
