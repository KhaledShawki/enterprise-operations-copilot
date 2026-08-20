package io.github.khaledshawki.eoc.webbff.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff")
class BffSessionController {
  @GetMapping("/session")
  BffSessionResponse session(Authentication authentication, HttpServletRequest request) {
    Object attribute = request.getAttribute(CsrfToken.class.getName());
    if (!(attribute instanceof CsrfToken csrf)) {
      throw new IllegalStateException("CSRF token is not available");
    }
    boolean authenticated =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    return new BffSessionResponse(
        authenticated,
        new BffSessionResponse.Csrf(
            csrf.getHeaderName(), csrf.getParameterName(), csrf.getToken()));
  }
}
