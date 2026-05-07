package com.notebook.lumen.common.security.worker;

import java.net.InetAddress;
import java.security.SecureRandom;

public final class WorkerInstanceIds {
  private static final SecureRandom RANDOM = new SecureRandom();

  private WorkerInstanceIds() {}

  public static String resolve(String configuredValue, String workerName) {
    if (configuredValue != null && !configuredValue.isBlank()) {
      return sanitize(configuredValue);
    }
    return sanitize(hostname() + "-" + workerName + "-" + randomSuffix());
  }

  private static String hostname() {
    try {
      String hostName = InetAddress.getLocalHost().getHostName();
      return hostName == null || hostName.isBlank() ? "unknown-host" : hostName;
    } catch (RuntimeException | java.net.UnknownHostException e) {
      return "unknown-host";
    }
  }

  private static String randomSuffix() {
    return Long.toUnsignedString(RANDOM.nextLong(), 36);
  }

  private static String sanitize(String value) {
    String normalized = value.replaceAll("[^A-Za-z0-9._:-]", "-");
    return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
  }
}
