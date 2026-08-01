package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public record ConnectorEndpoint(URI value) {

  public ConnectorEndpoint {
    Objects.requireNonNull(value, "Connector endpoint cannot be null");

    if (!value.isAbsolute()) {
      throw new IllegalArgumentException("Connector endpoint must be absolute");
    }

    String scheme = value.getScheme();
    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      throw new IllegalArgumentException("Connector endpoint must use HTTP or HTTPS");
    }

    if (value.getHost() == null || value.getHost().isBlank()) {
      throw new IllegalArgumentException("Connector endpoint must contain a host");
    }

    if (value.getUserInfo() != null) {
      throw new IllegalArgumentException("Connector endpoint cannot contain user information");
    }

    if (value.getFragment() != null) {
      throw new IllegalArgumentException("Connector endpoint cannot contain a fragment");
    }
  }

  public static ConnectorEndpoint of(String value) {
    Objects.requireNonNull(value, "Connector endpoint cannot be null");
    String normalizedValue = value.trim();

    if (normalizedValue.isEmpty()) {
      throw new IllegalArgumentException("Connector endpoint cannot be empty");
    }

    try {
      return new ConnectorEndpoint(new URI(normalizedValue));
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("Connector endpoint must be a valid URI", exception);
    }
  }
}
