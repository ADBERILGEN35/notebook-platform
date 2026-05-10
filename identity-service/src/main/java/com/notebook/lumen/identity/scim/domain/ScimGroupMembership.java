package com.notebook.lumen.identity.scim.domain;

import com.notebook.lumen.identity.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scim_group_memberships")
public class ScimGroupMembership {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private ScimGroup group;

  @Enumerated(EnumType.STRING)
  @Column(name = "member_type", nullable = false, length = 10)
  private ScimMemberType memberType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_user_id")
  private User memberUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_group_id")
  private ScimGroup memberGroup;

  @Column(name = "member_external_id", length = 255)
  private String memberExternalId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ScimGroupMembership() {}

  public ScimGroupMembership(
      UUID id,
      ScimGroup group,
      ScimMemberType memberType,
      User memberUser,
      ScimGroup memberGroup,
      String memberExternalId,
      Instant createdAt) {
    this.id = id;
    this.group = group;
    this.memberType = memberType;
    this.memberUser = memberUser;
    this.memberGroup = memberGroup;
    this.memberExternalId = memberExternalId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public ScimGroup getGroup() {
    return group;
  }

  public ScimMemberType getMemberType() {
    return memberType;
  }

  public User getMemberUser() {
    return memberUser;
  }

  public ScimGroup getMemberGroup() {
    return memberGroup;
  }

  public String getMemberExternalId() {
    return memberExternalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
