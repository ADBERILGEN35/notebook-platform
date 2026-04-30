package com.notebook.lumen.identity.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder(Argon2Properties props) {
    // Spring Security Argon2PasswordEncoder uses Argon2id by default in modern versions.
    return new Argon2PasswordEncoder(
        props.getSaltLength(),
        props.getHashLength(),
        props.getParallelism(),
        props.getMemoryInKb(),
        props.getIterations());
  }
}
