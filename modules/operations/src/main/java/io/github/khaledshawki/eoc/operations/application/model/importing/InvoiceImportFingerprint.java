package io.github.khaledshawki.eoc.operations.application.model.importing;

import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public final class InvoiceImportFingerprint {

  private InvoiceImportFingerprint() {}

  public static SourceRecordFingerprint record(InvoiceImportRecord record) {
    Objects.requireNonNull(record, "Invoice import record cannot be null");
    MessageDigest digest = sha256();
    updateText(digest, record.customerSourceIdentity().kind().name());
    updateText(digest, record.customerSourceIdentity().value());
    updateText(digest, record.invoiceNumber().value());
    updateText(digest, record.originalAmount().currency().value());
    updateText(digest, record.originalAmount().amount().toPlainString());
    updateText(digest, record.paidAmount().currency().value());
    updateText(digest, record.paidAmount().amount().toPlainString());
    updateText(digest, record.issueDate().toString());
    updateText(digest, record.dueDate().toString());
    digest.update((byte) (record.cancelled() ? 1 : 0));
    return SourceRecordFingerprint.of(HexFormat.of().formatHex(digest.digest()));
  }

  public static String page(ImportInvoicesCommand command) {
    Objects.requireNonNull(command, "Invoice import command cannot be null");
    MessageDigest digest = sha256();
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(command.records().size()).array());
    for (InvoiceImportRecord record : command.records()) {
      updateText(digest, record.sourceIdentity().kind().name());
      updateText(digest, record.sourceIdentity().value());
      updateText(digest, record.sourceVersion().value());
      updateOptionalInstant(digest, record.sourceModifiedAt());
      updateText(digest, record(record).value());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 message digest is not available", exception);
    }
  }

  private static void updateOptionalInstant(
      MessageDigest digest, Optional<Instant> sourceModifiedAt) {
    digest.update((byte) (sourceModifiedAt.isPresent() ? 1 : 0));
    sourceModifiedAt.ifPresent(value -> updateText(digest, value.toString()));
  }

  private static void updateText(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
