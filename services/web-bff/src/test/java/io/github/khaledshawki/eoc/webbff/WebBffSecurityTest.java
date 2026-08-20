package io.github.khaledshawki.eoc.webbff;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "eoc.web-bff.oidc.client-secret=test-secret")
@AutoConfigureMockMvc
class WebBffSecurityTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void shouldExposeAnonymousSessionAndServerCsrfToken() throws Exception {
    mockMvc
        .perform(get("/bff/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.csrf.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.csrf.parameterName").value("_csrf"))
        .andExpect(jsonPath("$.csrf.token", not(blankOrNullString())));
  }

  @Test
  void shouldRejectPlatformApiWithoutAuthenticatedBffSession() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRequireCsrfForLogout() throws Exception {
    mockMvc.perform(post("/logout")).andExpect(status().isForbidden());
  }

  @Test
  void shouldInitiateAuthorizationCodeFlowWithPkce() throws Exception {
    mockMvc
        .perform(get("/oauth2/authorization/eoc-web"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", containsString("code_challenge=")))
        .andExpect(header().string("Location", containsString("code_challenge_method=S256")));
  }
}
