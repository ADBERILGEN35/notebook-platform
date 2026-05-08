package com.notebook.lumen.identity.scim.domain;

import com.notebook.lumen.identity.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_scim_group_memberships")
public class UserScimGroupMembership {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "group_external_id", nullable = false, length = 255)
  private String groupExternalId;

  @Column(name = "group_display_name", nullable = false, length = 255)
  private String groupDisplayName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserScimGroupMembership() {}

  public UserScimGroupMembership(
      UUID id, User user, String groupExternalId, String groupDisplayName, Instant createdAt) {
    this.id = id;
    this.user = user;
    this.groupExternalId = groupExternalId;
    this.groupDisplayName = groupDisplayName;
    this.createdAt = createdAt;
  }

  public User getUser() {
    return user;
  }

  public String getGroupExternalId() {
    return groupExternalId;
  }

  public String getGroupDisplayName() {
    return groupDisplayName;
  }
}
