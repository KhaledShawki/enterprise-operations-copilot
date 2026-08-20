package io.github.khaledshawki.eoc.webbff;

import io.github.khaledshawki.eoc.webbff.configuration.WebBffProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WebBffProperties.class)
public class WebBffApplication {
  public static void main(String[] args) {
    SpringApplication.run(WebBffApplication.class, args);
  }
}
