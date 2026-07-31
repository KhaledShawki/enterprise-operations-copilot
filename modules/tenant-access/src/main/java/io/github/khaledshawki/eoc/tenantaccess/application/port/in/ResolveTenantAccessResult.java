package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public record ResolveTenantAccessResult(boolean granted) {

  private static final ResolveTenantAccessResult ALLOW = new ResolveTenantAccessResult(true);
  private static final ResolveTenantAccessResult DENY = new ResolveTenantAccessResult(false);

  public static ResolveTenantAccessResult allow() {
    return ALLOW;
  }

  public static ResolveTenantAccessResult deny() {
    return DENY;
  }
}
