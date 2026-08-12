package io.github.khaledshawki.eoc.copilot.application.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.khaledshawki.eoc.copilot.application.model.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CopilotToolContractTest {
  @Test
  void exposesStableApprovedToolNames() {
    assertEquals("get_receivable", CopilotToolName.GET_RECEIVABLE.contractName());
    assertEquals("list_receivables", CopilotToolName.LIST_RECEIVABLES.contractName());
    assertEquals("get_receivables_summary", CopilotToolName.GET_RECEIVABLES_SUMMARY.contractName());
  }

  @Test
  void toolArgumentsCannotSelectTenant() {
    assertHasNoTenantArgument(GetReceivableToolRequest.class);
    assertHasNoTenantArgument(ListReceivablesToolRequest.class);
    assertHasNoTenantArgument(GetReceivablesSummaryToolRequest.class);
  }

  private static void assertHasNoTenantArgument(Class<?> requestType) {
    assertFalse(
        Arrays.stream(requestType.getRecordComponents())
            .anyMatch(
                component ->
                    component.getName().toLowerCase(java.util.Locale.ROOT).contains("tenant")));
  }
}
