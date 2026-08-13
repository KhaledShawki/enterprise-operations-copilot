package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

public final class CopilotMcpToolException extends RuntimeException {
  public static final String INVALID_CONTEXT = "INVALID_CONTEXT";
  public static final String INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String ACCESS_DENIED = "ACCESS_DENIED";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String DATA_UNAVAILABLE = "DATA_UNAVAILABLE";
  public static final String DATA_CORRUPTED = "DATA_CORRUPTED";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private final String code;

  private CopilotMcpToolException(String code, String message, Throwable cause) {
    super(code + ": " + message, cause);
    this.code = code;
  }

  public static CopilotMcpToolException invalidContext(String message) {
    return new CopilotMcpToolException(INVALID_CONTEXT, message, null);
  }

  public static CopilotMcpToolException invalidContext(String message, Throwable cause) {
    return new CopilotMcpToolException(INVALID_CONTEXT, message, cause);
  }

  public static CopilotMcpToolException mapped(String code, String message, Throwable cause) {
    return new CopilotMcpToolException(code, message, cause);
  }

  public String code() {
    return code;
  }
}
