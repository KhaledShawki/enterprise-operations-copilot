package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.IncrementalCursor;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceCustomerRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceEntity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceIdentity;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceModificationVersion;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePageToken;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourcePaymentRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceRecordMetadata;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaIssue;
import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceSchemaVerificationResult;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.CheckConnectionRequest;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.CustomerPageResponse;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.CustomerRecord;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.InvoicePageResponse;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.InvoiceRecord;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.MockErpSourceServiceGrpc;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.PaymentPageResponse;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.PaymentRecord;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.SchemaIssue;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.SchemaIssueType;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.SourcePagePosition;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.SourcePageRequest;
import io.github.khaledshawki.eoc.contracts.mockerp.v1.VerifySchemaRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
final class GrpcMockErpRpcClient implements MockErpRpcClient {

  private static final long DEADLINE_SECONDS = 5;

  private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

  @Override
  public void checkConnection(ConnectorEndpoint endpoint) {
    invoke(
        () -> {
          stub(endpoint).checkConnection(CheckConnectionRequest.getDefaultInstance());
          return null;
        },
        false);
  }

  @Override
  public SourceSchemaVerificationResult verifySchema(ConnectorEndpoint endpoint) {
    return invoke(
        () -> {
          var response = stub(endpoint).verifySchema(VerifySchemaRequest.getDefaultInstance());
          if (response.getIssuesCount() == 0) {
            return SourceSchemaVerificationResult.verified();
          }
          return SourceSchemaVerificationResult.withIssues(
              response.getIssuesList().stream().map(GrpcMockErpRpcClient::schemaIssue).toList());
        },
        false);
  }

  @Override
  public SourcePage<SourceCustomerRecord> retrieveCustomers(
      ConnectorEndpoint endpoint, SourceFetchRequest request) {
    return invoke(
        () -> {
          CustomerPageResponse response = stub(endpoint).getCustomers(pageRequest(request));
          return page(
              response.getRecordsList().stream().map(GrpcMockErpRpcClient::customer).toList(),
              response.getPosition());
        },
        true);
  }

  @Override
  public SourcePage<SourceInvoiceRecord> retrieveInvoices(
      ConnectorEndpoint endpoint, SourceFetchRequest request) {
    return invoke(
        () -> {
          InvoicePageResponse response = stub(endpoint).getInvoices(pageRequest(request));
          return page(
              response.getRecordsList().stream().map(GrpcMockErpRpcClient::invoice).toList(),
              response.getPosition());
        },
        true);
  }

  @Override
  public SourcePage<SourcePaymentRecord> retrievePayments(
      ConnectorEndpoint endpoint, SourceFetchRequest request) {
    return invoke(
        () -> {
          PaymentPageResponse response = stub(endpoint).getPayments(pageRequest(request));
          return page(
              response.getRecordsList().stream().map(GrpcMockErpRpcClient::payment).toList(),
              response.getPosition());
        },
        true);
  }

  private MockErpSourceServiceGrpc.MockErpSourceServiceBlockingStub stub(
      ConnectorEndpoint endpoint) {
    return MockErpSourceServiceGrpc.newBlockingStub(channel(endpoint))
        .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS);
  }

  private ManagedChannel channel(ConnectorEndpoint endpoint) {
    URI uri = endpoint.value();
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("grpc") && !scheme.equals("grpcs")) {
      throw rpcFailure(
          MockErpRpcException.Category.SOURCE_CONTRACT_VIOLATION,
          "mock-grpc-endpoint-required",
          null);
    }
    String path = uri.getPath();
    if ((path != null && !path.isEmpty() && !path.equals("/")) || uri.getQuery() != null) {
      throw rpcFailure(
          MockErpRpcException.Category.SOURCE_CONTRACT_VIOLATION,
          "mock-grpc-endpoint-invalid",
          null);
    }
    int port = uri.getPort();
    if (port < 1) {
      port = scheme.equals("grpcs") ? 443 : 80;
    }
    String key = scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    int resolvedPort = port;
    return channels.computeIfAbsent(
        key,
        ignored -> {
          ManagedChannelBuilder<?> builder =
              ManagedChannelBuilder.forAddress(uri.getHost(), resolvedPort);
          if (scheme.equals("grpcs")) {
            builder.useTransportSecurity();
          } else {
            builder.usePlaintext();
          }
          return builder.build();
        });
  }

  private static SourcePageRequest pageRequest(SourceFetchRequest request) {
    SourcePageRequest.Builder builder =
        SourcePageRequest.newBuilder().setPageSize(request.pageSize());
    request.pageToken().ifPresent(token -> builder.setPageToken(token.value()));
    request.incrementalCursor().ifPresent(cursor -> builder.setIncrementalCursor(cursor.value()));
    return builder.build();
  }

  private static SourceCustomerRecord customer(CustomerRecord record) {
    try {
      return new SourceCustomerRecord(
          metadata(
              SourceEntity.CUSTOMER,
              record.getSourceId(),
              record.getSourceVersion(),
              record.getSourceModifiedAt()),
          record.getCustomerNumber(),
          record.getDisplayName(),
          optionalText(record.getEmailAddress()));
    } catch (RuntimeException exception) {
      throw contractViolation(exception);
    }
  }

  private static SourceInvoiceRecord invoice(InvoiceRecord record) {
    try {
      return new SourceInvoiceRecord(
          metadata(
              SourceEntity.INVOICE,
              record.getSourceId(),
              record.getSourceVersion(),
              record.getSourceModifiedAt()),
          SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, record.getCustomerSourceId()),
          record.getInvoiceNumber(),
          LocalDate.parse(record.getIssueDate()),
          LocalDate.parse(record.getDueDate()),
          Currency.getInstance(record.getCurrency()),
          new BigDecimal(record.getTotalAmount()),
          new BigDecimal(record.getOpenAmount()),
          record.getSourceStatus());
    } catch (RuntimeException exception) {
      throw contractViolation(exception);
    }
  }

  private static SourcePaymentRecord payment(PaymentRecord record) {
    try {
      return new SourcePaymentRecord(
          metadata(
              SourceEntity.PAYMENT,
              record.getSourceId(),
              record.getSourceVersion(),
              record.getSourceModifiedAt()),
          SourceIdentity.sourceRecordId(SourceEntity.CUSTOMER, record.getCustomerSourceId()),
          LocalDate.parse(record.getPaymentDate()),
          Currency.getInstance(record.getCurrency()),
          new BigDecimal(record.getAmount()),
          record.getReversed());
    } catch (RuntimeException exception) {
      throw contractViolation(exception);
    }
  }

  private static SourceRecordMetadata metadata(
      SourceEntity entity, String sourceId, String version, String modifiedAt) {
    return new SourceRecordMetadata(
        SourceIdentity.sourceRecordId(entity, sourceId),
        new SourceModificationVersion(version),
        Optional.of(Instant.parse(modifiedAt)));
  }

  private static SourceSchemaIssue schemaIssue(SchemaIssue issue) {
    SourceEntity entity =
        switch (issue.getEntity()) {
          case SOURCE_ENTITY_CUSTOMER -> SourceEntity.CUSTOMER;
          case SOURCE_ENTITY_INVOICE -> SourceEntity.INVOICE;
          case SOURCE_ENTITY_PAYMENT -> SourceEntity.PAYMENT;
          default -> throw contractViolation(null);
        };
    SchemaIssueType type = issue.getType();
    return switch (type) {
      case SCHEMA_ISSUE_TYPE_MISSING_REQUIRED_ENTITY -> SourceSchemaIssue.missingEntity(entity);
      case SCHEMA_ISSUE_TYPE_MISSING_REQUIRED_FIELD ->
          SourceSchemaIssue.missingField(entity, issue.getField());
      case SCHEMA_ISSUE_TYPE_INCOMPATIBLE_FIELD_TYPE ->
          SourceSchemaIssue.incompatibleFieldType(entity, issue.getField());
      default -> throw contractViolation(null);
    };
  }

  private static <T> SourcePage<T> page(List<T> records, SourcePagePosition position) {
    Optional<SourcePageToken> nextPageToken =
        optionalText(position.getNextPageToken()).map(SourcePageToken::new);
    Optional<IncrementalCursor> candidateCursor =
        optionalText(position.getCandidateCursor()).map(IncrementalCursor::new);
    return new SourcePage<>(records, nextPageToken, candidateCursor);
  }

  private static Optional<String> optionalText(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }

  private static <T> T invoke(Supplier<T> call, boolean positionSensitive) {
    try {
      return call.get();
    } catch (MockErpRpcException exception) {
      throw exception;
    } catch (StatusRuntimeException exception) {
      throw statusFailure(exception, positionSensitive);
    } catch (RuntimeException exception) {
      throw rpcFailure(
          MockErpRpcException.Category.UNEXPECTED_SOURCE_FAILURE,
          "mock-grpc-unexpected-failure",
          exception);
    }
  }

  private static MockErpRpcException statusFailure(
      StatusRuntimeException exception, boolean positionSensitive) {
    Status.Code code = exception.getStatus().getCode();
    return switch (code) {
      case UNAUTHENTICATED ->
          rpcFailure(
              MockErpRpcException.Category.AUTHENTICATION_FAILED,
              "mock-grpc-authentication-failed",
              exception);
      case PERMISSION_DENIED ->
          rpcFailure(
              MockErpRpcException.Category.AUTHORIZATION_FAILED,
              "mock-grpc-authorization-failed",
              exception);
      case UNAVAILABLE ->
          rpcFailure(
              MockErpRpcException.Category.SOURCE_UNAVAILABLE,
              "mock-grpc-source-unavailable",
              exception);
      case DEADLINE_EXCEEDED ->
          rpcFailure(
              MockErpRpcException.Category.TIMEOUT, "mock-grpc-deadline-exceeded", exception);
      case RESOURCE_EXHAUSTED ->
          rpcFailure(
              MockErpRpcException.Category.RATE_LIMITED, "mock-grpc-rate-limited", exception);
      case INVALID_ARGUMENT ->
          rpcFailure(
              positionSensitive
                  ? MockErpRpcException.Category.INVALID_POSITION
                  : MockErpRpcException.Category.SOURCE_CONTRACT_VIOLATION,
              positionSensitive ? "mock-grpc-invalid-position" : "mock-grpc-invalid-request",
              exception);
      case FAILED_PRECONDITION, OUT_OF_RANGE ->
          rpcFailure(
              MockErpRpcException.Category.SOURCE_CONTRACT_VIOLATION,
              "mock-grpc-source-contract-violation",
              exception);
      default ->
          rpcFailure(
              MockErpRpcException.Category.UNEXPECTED_SOURCE_FAILURE,
              "mock-grpc-unexpected-status",
              exception);
    };
  }

  private static MockErpRpcException contractViolation(Throwable cause) {
    return rpcFailure(
        MockErpRpcException.Category.SOURCE_CONTRACT_VIOLATION,
        "mock-grpc-invalid-source-payload",
        cause);
  }

  private static MockErpRpcException rpcFailure(
      MockErpRpcException.Category category, String diagnosticCode, Throwable cause) {
    return new MockErpRpcException(category, diagnosticCode, cause);
  }

  @PreDestroy
  void closeChannels() {
    channels.values().forEach(ManagedChannel::shutdownNow);
    channels.clear();
  }
}
