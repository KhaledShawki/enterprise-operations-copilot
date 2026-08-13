package io.github.khaledshawki.eoc.platform.copilot.configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.copilot.mcp")
public class CopilotMcpProperties {

  private List<String> allowedHosts = List.of("localhost:*", "127.0.0.1:*");
  private List<String> allowedOrigins = List.of("http://localhost:*", "http://127.0.0.1:*");

  public List<String> getAllowedHosts() {
    return allowedHosts;
  }

  public void setAllowedHosts(List<String> allowedHosts) {
    this.allowedHosts = normalize(allowedHosts, "allowed hosts", true);
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = normalize(allowedOrigins, "allowed origins", false);
  }

  private static List<String> normalize(
      List<String> values, String fieldName, boolean requireNonEmpty) {
    Objects.requireNonNull(values, "Copilot MCP " + fieldName + " cannot be null");

    List<String> normalized =
        values.stream()
            .map(
                value ->
                    Objects.requireNonNull(
                            value, "Copilot MCP " + fieldName + " cannot contain null values")
                        .strip())
            .toList();

    if (normalized.stream().anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException(
          "Copilot MCP " + fieldName + " cannot contain blank values");
    }
    if (requireNonEmpty && normalized.isEmpty()) {
      throw new IllegalArgumentException("Copilot MCP " + fieldName + " cannot be empty");
    }
    if (new HashSet<>(normalized).size() != normalized.size()) {
      throw new IllegalArgumentException("Copilot MCP " + fieldName + " cannot contain duplicates");
    }

    return List.copyOf(normalized);
  }
}
