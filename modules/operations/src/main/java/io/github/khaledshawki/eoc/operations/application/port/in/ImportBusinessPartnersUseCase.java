package io.github.khaledshawki.eoc.operations.application.port.in;

public interface ImportBusinessPartnersUseCase {

  BusinessPartnerImportResult importPage(ImportBusinessPartnersCommand command);
}
