package com.notebook.lumen.content;

import com.notebook.lumen.content.audit.AuditAdminProperties;
import com.notebook.lumen.content.config.ContentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ContentProperties.class, AuditAdminProperties.class})
public class ContentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ContentServiceApplication.class, args);
  }
}
