package com.notebook.lumen.workspace;

import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionAdminProperties;
import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionProperties;
import com.notebook.lumen.workspace.audit.AuditAdminProperties;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
  WorkspaceProperties.class,
  AuditAdminProperties.class,
  WorkspaceRetentionProperties.class,
  WorkspaceRetentionAdminProperties.class
})
public class WorkspaceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkspaceServiceApplication.class, args);
  }
}
