package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserUseCase;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/v1/platform-users/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class PlatformUserProvisioningController {

  private final JwtAuthenticatedUserMapper authenticatedUserMapper;
  private final ProvisionPlatformUserUseCase provisionPlatformUserUseCase;

  public PlatformUserProvisioningController(
      JwtAuthenticatedUserMapper authenticatedUserMapper,
      ProvisionPlatformUserUseCase provisionPlatformUserUseCase) {
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");
    this.provisionPlatformUserUseCase =
        Objects.requireNonNull(
            provisionPlatformUserUseCase, "Provision platform user use case cannot be null");
  }

  @PutMapping
  public ResponseEntity<PlatformUserResponse> provisionPlatformUser(
      JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = authenticatedUserMapper.map(authentication);

    ProvisionPlatformUserCommand command =
        new ProvisionPlatformUserCommand(
            authenticatedUser.issuer().toString(), authenticatedUser.subject());

    ProvisionPlatformUserResult result = provisionPlatformUserUseCase.provision(command);

    PlatformUserResponse response = PlatformUserResponse.from(result);

    if (result.created()) {
      URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();

      return ResponseEntity.created(location).body(response);
    }

    return ResponseEntity.ok(response);
  }
}
