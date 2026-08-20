package io.github.khaledshawki.eoc.webbff.platform;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

record PlatformApiResponse(HttpStatusCode status, MediaType contentType, byte[] body) {}
