package com.notebook.lumen.identity.siem.domain;

public enum SiemOutboxStatus {
  PENDING,
  SENDING,
  SENT,
  FAILED,
  DEAD
}
