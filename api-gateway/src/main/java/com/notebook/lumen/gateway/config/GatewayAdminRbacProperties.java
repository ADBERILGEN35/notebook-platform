package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gateway.admin.rbac")
public record GatewayAdminRbacProperties(@DefaultValue("false") boolean enforce) {}
