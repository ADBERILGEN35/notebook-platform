package com.notebook.lumen.content.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantCleanupFilter extends OncePerRequestFilter {
  private final TenantContext tenantContext;

  public TenantCleanupFilter(TenantContext tenantContext) {
    this.tenantContext = tenantContext;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } finally {
      tenantContext.clear();
    }
  }
}
