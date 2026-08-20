package io.github.khaledshawki.eoc.webbff.platform;

public class PlatformApiUnavailableException extends RuntimeException {
  PlatformApiUnavailableException(Throwable cause) {
    super("Platform API is unavailable", cause);
  }
}
