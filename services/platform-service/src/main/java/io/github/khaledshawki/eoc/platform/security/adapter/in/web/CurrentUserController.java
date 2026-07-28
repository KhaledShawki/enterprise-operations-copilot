package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsUseCase;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
class CurrentUserController {

  private final JwtAuthenticatedUserMapper authenticatedUserMapper;

  private final ListAccessibleTenantsUseCase listAccessibleTenantsUseCase;

  CurrentUserController(
      JwtAuthenticatedUserMapper authenticatedUserMapper,
      ListAccessibleTenantsUseCase listAccessibleTenantsUseCase) {
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");

    this.listAccessibleTenantsUseCase =
        Objects.requireNonNull(
            listAccessibleTenantsUseCase, "List accessible tenants use case cannot be null");
  }

  @GetMapping
  CurrentUserResponse getCurrentUser(JwtAuthenticationToken authentication) {
    return CurrentUserResponse.from(authenticatedUserMapper.map(authentication));
  }

  @GetMapping("/tenants")
  CurrentUserTenantsResponse getAccessibleTenants(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = authenticatedUserMapper.map(authentication);

    ListAccessibleTenantsResult result =
        listAccessibleTenantsUseCase.list(
            new ListAccessibleTenantsQuery(
                authenticatedUser.issuer().toString(), authenticatedUser.subject()));

    return CurrentUserTenantsResponse.from(result);
  }
}
