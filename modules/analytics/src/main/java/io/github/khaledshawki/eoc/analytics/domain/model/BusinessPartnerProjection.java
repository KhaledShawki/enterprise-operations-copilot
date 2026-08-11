package io.github.khaledshawki.eoc.analytics.domain.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

public record BusinessPartnerProjection(
    AnalyticsTenantId tenantId,
    UUID businessPartnerId,
    String partnerNumber,
    String displayName,
    Set<String> roles,
    ProjectionCursor source) {

  public static final int MAX_PARTNER_NUMBER_LENGTH = 100;
  public static final int MAX_DISPLAY_NAME_LENGTH = 255;
  private static final Pattern ROLE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public BusinessPartnerProjection {
    Objects.requireNonNull(tenantId, "Business partner projection tenant id cannot be null");
    Objects.requireNonNull(
        businessPartnerId, "Business partner projection business partner id cannot be null");
    partnerNumber =
        requiredText(
            partnerNumber, "Business partner projection partner number", MAX_PARTNER_NUMBER_LENGTH);
    displayName =
        requiredText(
            displayName, "Business partner projection display name", MAX_DISPLAY_NAME_LENGTH);
    roles = requireRoles(roles);
    Objects.requireNonNull(source, "Business partner projection source cannot be null");
  }

  private static Set<String> requireRoles(Set<String> roles) {
    Objects.requireNonNull(roles, "Business partner projection roles cannot be null");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("Business partner projection roles cannot be empty");
    }
    TreeSet<String> canonical = new TreeSet<>();
    for (String role : roles) {
      Objects.requireNonNull(role, "Business partner projection role cannot be null");
      if (!ROLE.matcher(role).matches()) {
        throw new IllegalArgumentException(
            "Business partner projection role must be an uppercase contract code");
      }
      canonical.add(role);
    }
    return Collections.unmodifiableSet(canonical);
  }

  private static String requiredText(String value, String field, int maxLength) {
    Objects.requireNonNull(value, field + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be blank");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
    }
    return normalized;
  }
}
