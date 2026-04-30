package com.notebook.lumen.workspace;

import com.notebook.lumen.workspace.config.WorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WorkspaceProperties.class)
public class WorkspaceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkspaceServiceApplication.class, args);
  }
}
