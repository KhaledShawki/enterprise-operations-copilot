package io.github.khaledshawki.eoc.webbff.session;

record BffSessionResponse(boolean authenticated, Csrf csrf) {
  record Csrf(String headerName, String parameterName, String token) {}
}
