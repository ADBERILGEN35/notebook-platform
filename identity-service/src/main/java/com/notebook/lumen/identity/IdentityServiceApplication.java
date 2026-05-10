package com.notebook.lumen.identity;

import com.notebook.lumen.identity.admin.InternalAdminStatusProperties;
import com.notebook.lumen.identity.admin.changerequest.AdminChangeRequestProperties;
import com.notebook.lumen.identity.audit.AuditAdminProperties;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.notification.IdentityNotificationProperties;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.siem.SiemProperties;
import com.notebook.lumen.identity.sso.SsoProperties;
import com.notebook.lumen.identity.shared.config.AuthTransportProperties;
import com.notebook.lumen.identity.shared.config.Argon2Properties;
import com.notebook.lumen.identity.shared.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  Argon2Properties.class,
  JwtProperties.class,
  AuthTransportProperties.class,
  AuditAdminProperties.class,
  InternalAdminStatusProperties.class,
  AdminChangeRequestProperties.class,
  IdentityNotificationProperties.class,
  MfaProperties.class,
  SsoProperties.class,
  ScimProperties.class,
  SiemProperties.class
})
public class IdentityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityServiceApplication.class, args);
  }
}
