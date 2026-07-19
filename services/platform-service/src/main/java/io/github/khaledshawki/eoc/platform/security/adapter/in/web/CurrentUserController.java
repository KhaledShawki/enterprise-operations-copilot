package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

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

  CurrentUserController(JwtAuthenticatedUserMapper authenticatedUserMapper) {
    this.authenticatedUserMapper =
        Objects.requireNonNull(authenticatedUserMapper, "Authenticated user mapper cannot be null");
  }

  @GetMapping
  CurrentUserResponse getCurrentUser(JwtAuthenticationToken authentication) {
    return CurrentUserResponse.from(authenticatedUserMapper.map(authentication));
  }
}
