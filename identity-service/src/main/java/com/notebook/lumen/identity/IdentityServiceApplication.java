package com.notebook.lumen.identity;

import com.notebook.lumen.identity.shared.config.Argon2Properties;
import com.notebook.lumen.identity.shared.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({Argon2Properties.class, JwtProperties.class})
public class IdentityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityServiceApplication.class, args);
  }
}
