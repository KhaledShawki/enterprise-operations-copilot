package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipAlreadySuspendedException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantMembershipNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantMembershipControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final UUID PLATFORM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/memberships";
  private static final String MEMBERSHIP_ENDPOINT = ENDPOINT + "/" + MEMBERSHIP_ID;
  private static final String SUSPENSION_ENDPOINT = MEMBERSHIP_ENDPOINT + "/suspension";

  private static final String VALID_REQUEST =
      """
      {
        "platformUserId": "00000000-0000-0000-0000-000000000002"
      }
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssignTenantMembershipUseCase assignTenantMembershipUseCase;

  @MockitoBean private GetTenantMembershipUseCase getTenantMembershipUseCase;

  @MockitoBean private SuspendTenantMembershipUseCase suspendTenantMembershipUseCase;

  @Test
  void shouldAssignTenantMembership() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    AssignTenantMembershipResult result =
        new AssignTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.ACTIVE);

    when(assignTenantMembershipUseCase.assign(command)).thenReturn(result);

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    "http://localhost/api/v1/tenants/"
                        + TENANT_ID
                        + "/memberships/"
                        + MEMBERSHIP_ID))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldRejectMissingPlatformUserId() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:request-validation-failed"))
        .andExpect(jsonPath("$.title").value("Request validation failed"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("platformUserId"))
        .andExpect(jsonPath("$.errors[0].message").value("Platform user id is required"));

    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldRejectMalformedRequest() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"platformUserId\":"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:malformed-request"))
        .andExpect(jsonPath("$.title").value("Malformed request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenTenantDoesNotExist() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    when(assignTenantMembershipUseCase.assign(command))
        .thenThrow(new TenantNotFoundException(TenantId.of(TENANT_ID)));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + TENANT_ID + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldReturnNotFoundWhenPlatformUserDoesNotExist() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    when(assignTenantMembershipUseCase.assign(command))
        .thenThrow(new PlatformUserNotFoundException(PlatformUserId.of(PLATFORM_USER_ID)));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-found"))
        .andExpect(jsonPath("$.title").value("Platform user not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail").value("Platform user " + PLATFORM_USER_ID + " was not found"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_FOUND"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldReturnConflictWhenTenantIsNotActive() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    when(assignTenantMembershipUseCase.assign(command))
        .thenThrow(new TenantNotActiveException(TenantId.of(TENANT_ID)));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-active"))
        .andExpect(jsonPath("$.title").value("Tenant is not active"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value("Tenant " + TENANT_ID + " is not active"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_ACTIVE"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldReturnConflictWhenPlatformUserIsNotActive() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    when(assignTenantMembershipUseCase.assign(command))
        .thenThrow(new PlatformUserNotActiveException(PlatformUserId.of(PLATFORM_USER_ID)));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-active"))
        .andExpect(jsonPath("$.title").value("Platform user is not active"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail").value("Platform user " + PLATFORM_USER_ID + " is not active"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_ACTIVE"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldReturnConflictWhenMembershipAlreadyExists() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    when(assignTenantMembershipUseCase.assign(command))
        .thenThrow(
            new TenantMembershipAlreadyExistsException(
                TenantId.of(TENANT_ID), PlatformUserId.of(PLATFORM_USER_ID)));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-already-exists"))
        .andExpect(jsonPath("$.title").value("Tenant membership already exists"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Membership already exists for tenant "
                        + TENANT_ID
                        + " and platform user "
                        + PLATFORM_USER_ID))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_ALREADY_EXISTS"));

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldGetActiveTenantMembership() throws Exception {
    GetTenantMembershipQuery query = new GetTenantMembershipQuery(TENANT_ID, MEMBERSHIP_ID);

    GetTenantMembershipResult result =
        new GetTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.ACTIVE);

    when(getTenantMembershipUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(get(MEMBERSHIP_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(getTenantMembershipUseCase).get(query);
    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldGetSuspendedTenantMembership() throws Exception {
    GetTenantMembershipQuery query = new GetTenantMembershipQuery(TENANT_ID, MEMBERSHIP_ID);

    GetTenantMembershipResult result =
        new GetTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.SUSPENDED);

    when(getTenantMembershipUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(get(MEMBERSHIP_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(getTenantMembershipUseCase).get(query);
    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenGettingMembershipForMissingTenant() throws Exception {
    GetTenantMembershipQuery query = new GetTenantMembershipQuery(TENANT_ID, MEMBERSHIP_ID);

    when(getTenantMembershipUseCase.get(query))
        .thenThrow(new TenantNotFoundException(TenantId.of(TENANT_ID)));

    mockMvc
        .perform(get(MEMBERSHIP_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + TENANT_ID + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

    verify(getTenantMembershipUseCase).get(query);
    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenGettingUnknownTenantMembership() throws Exception {
    GetTenantMembershipQuery query = new GetTenantMembershipQuery(TENANT_ID, MEMBERSHIP_ID);

    when(getTenantMembershipUseCase.get(query))
        .thenThrow(
            new TenantMembershipNotFoundException(
                TenantId.of(TENANT_ID), TenantMembershipId.of(MEMBERSHIP_ID)));

    mockMvc
        .perform(get(MEMBERSHIP_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant membership not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + MEMBERSHIP_ID
                        + " was not found for tenant "
                        + TENANT_ID))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_NOT_FOUND"));

    verify(getTenantMembershipUseCase).get(query);
    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldSuspendTenantMembership() throws Exception {
    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    SuspendTenantMembershipResult result =
        new SuspendTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.SUSPENDED);

    when(suspendTenantMembershipUseCase.suspend(command)).thenReturn(result);

    mockMvc
        .perform(post(SUSPENSION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(suspendTenantMembershipUseCase).suspend(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenSuspendingMembershipForMissingTenant() throws Exception {
    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    when(suspendTenantMembershipUseCase.suspend(command))
        .thenThrow(new TenantNotFoundException(TenantId.of(TENANT_ID)));

    mockMvc
        .perform(post(SUSPENSION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + TENANT_ID + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

    verify(suspendTenantMembershipUseCase).suspend(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldReturnNotFoundWhenSuspendingUnknownTenantMembership() throws Exception {
    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    when(suspendTenantMembershipUseCase.suspend(command))
        .thenThrow(
            new TenantMembershipNotFoundException(
                TenantId.of(TENANT_ID), TenantMembershipId.of(MEMBERSHIP_ID)));

    mockMvc
        .perform(post(SUSPENSION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant membership not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + MEMBERSHIP_ID
                        + " was not found for tenant "
                        + TENANT_ID))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_NOT_FOUND"));

    verify(suspendTenantMembershipUseCase).suspend(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldReturnConflictWhenTenantMembershipIsAlreadySuspended() throws Exception {
    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    when(suspendTenantMembershipUseCase.suspend(command))
        .thenThrow(
            new TenantMembershipAlreadySuspendedException(
                TenantId.of(TENANT_ID), TenantMembershipId.of(MEMBERSHIP_ID)));

    mockMvc
        .perform(post(SUSPENSION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-already-suspended"))
        .andExpect(jsonPath("$.title").value("Tenant membership already suspended"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + MEMBERSHIP_ID
                        + " is already suspended for tenant "
                        + TENANT_ID))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_ALREADY_SUSPENDED"));

    verify(suspendTenantMembershipUseCase).suspend(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }
}
