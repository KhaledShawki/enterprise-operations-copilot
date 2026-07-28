package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("f6df18c0-306e-4be0-b2c2-b985e3aadcb7");

  private static final String TENANT_ENDPOINT = "/api/v1/tenants/" + TENANT_ID;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CreateTenantUseCase createTenantUseCase;

  @MockitoBean private GetTenantUseCase getTenantUseCase;

  @Test
  public void shouldCreateTenant() throws Exception {
    CreateTenantCommand createTenantCommand = new CreateTenantCommand("tenant-key", "Tenant Name");
    CreateTenantResult result =
        new CreateTenantResult(
            TenantId.of(TENANT_ID),
            TenantKey.of("tenant-key"),
            TenantName.of("Tenant Name"),
            TenantStatus.ACTIVE);
    when(createTenantUseCase.create(createTenantCommand)).thenReturn(result);

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "tenantKey": "tenant-key",
                        "displayName": "Tenant Name"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost/api/v1/tenants/" + TENANT_ID))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenantKey").value("tenant-key"))
        .andExpect(jsonPath("$.displayName").value("Tenant Name"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(createTenantUseCase).create(createTenantCommand);
  }

  @Test
  void shouldRejectInvalidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "tenantKey": "Invalid key",
                      "displayName": ""
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:request-validation-failed"))
        .andExpect(jsonPath("$.title").value("Request validation failed"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", hasItems("tenantKey", "displayName")));

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldRejectMalformedJson() throws Exception {

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantKey\":"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:malformed-request"))
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldReturnConflictIfTenantAlreadyExists() throws Exception {
    CreateTenantCommand createTenantCommand = new CreateTenantCommand("tenant-key", "Tenant Name");
    when(createTenantUseCase.create(createTenantCommand))
        .thenThrow(new TenantKeyAlreadyExistsException(TenantKey.of("tenant-key")));

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "tenantKey": "tenant-key",
                        "displayName": "Tenant Name"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-key-already-exists"))
        .andExpect(jsonPath("$.title").value("Tenant key already exists"))
        .andExpect(jsonPath("$.detail").value("Tenant key tenant-key already exists"))
        .andExpect(jsonPath("$.code").value("TENANT_KEY_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.status").value(409));

    verify(createTenantUseCase).create(createTenantCommand);
  }

  @Test
  void shouldGetActiveTenant() throws Exception {
    GetTenantQuery query = new GetTenantQuery(TENANT_ID);

    GetTenantResult result =
        new GetTenantResult(
            TenantId.of(TENANT_ID),
            TenantKey.of("tenant-key"),
            TenantName.of("Tenant Name"),
            TenantStatus.ACTIVE);

    when(getTenantUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(get(TENANT_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenantKey").value("tenant-key"))
        .andExpect(jsonPath("$.displayName").value("Tenant Name"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(getTenantUseCase).get(query);

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldGetSuspendedTenant() throws Exception {
    GetTenantQuery query = new GetTenantQuery(TENANT_ID);

    GetTenantResult result =
        new GetTenantResult(
            TenantId.of(TENANT_ID),
            TenantKey.of("suspended-tenant"),
            TenantName.of("Suspended Tenant"),
            TenantStatus.SUSPENDED);

    when(getTenantUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(get(TENANT_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenantKey").value("suspended-tenant"))
        .andExpect(jsonPath("$.displayName").value("Suspended Tenant"))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(getTenantUseCase).get(query);

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenGettingUnknownTenant() throws Exception {
    GetTenantQuery query = new GetTenantQuery(TENANT_ID);

    when(getTenantUseCase.get(query))
        .thenThrow(new TenantNotFoundException(TenantId.of(TENANT_ID)));

    mockMvc
        .perform(get(TENANT_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + TENANT_ID + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

    verify(getTenantUseCase).get(query);

    verifyNoInteractions(createTenantUseCase);
  }
}
