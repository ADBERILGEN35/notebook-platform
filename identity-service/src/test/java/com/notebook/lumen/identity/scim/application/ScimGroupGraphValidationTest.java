package com.notebook.lumen.identity.scim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ScimGroupGraphValidationTest {

  @Mock private ScimGroupMembershipRepository membershipRepository;

  @InjectMocks private ScimGroupGraphValidation validation;

  @Test
  void rejectsSelfMembership() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> validation.validateNewNestedMembership(id, id, 5))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex -> {
              ScimException sx = (ScimException) ex;
              assertEquals(HttpStatus.BAD_REQUEST, sx.getStatus());
              assertThat(sx.getMessage()).contains("SCIM_GROUP_CYCLE_DETECTED");
            });
  }

  @Test
  void rejectsCycleWhenChildIsAncestorOfParent() {
    UUID parent = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    when(membershipRepository.findParentGroupIdsContainingChild(
            eq(ScimMemberType.GROUP), eq(parent)))
        .thenReturn(List.of(child));

    assertThatThrownBy(() -> validation.validateNewNestedMembership(parent, child, 5))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex ->
                assertThat(((ScimException) ex).getMessage())
                    .contains("SCIM_GROUP_CYCLE_DETECTED"));
  }

  @Test
  void rejectsWhenDepthWouldExceedLimit() {
    UUID parent = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    UUID root = UUID.randomUUID();
    when(membershipRepository.findParentGroupIdsContainingChild(
            eq(ScimMemberType.GROUP), eq(parent)))
        .thenReturn(List.of(root));
    when(membershipRepository.findParentGroupIdsContainingChild(eq(ScimMemberType.GROUP), eq(root)))
        .thenReturn(List.of());
    when(membershipRepository.findNestedChildGroupIds(eq(ScimMemberType.GROUP), eq(child)))
        .thenReturn(List.of());

    assertThatThrownBy(() -> validation.validateNewNestedMembership(parent, child, 1))
        .isInstanceOf(ScimException.class)
        .satisfies(
            ex ->
                assertThat(((ScimException) ex).getMessage())
                    .contains("SCIM_GROUP_NESTING_DEPTH_EXCEEDED"));
  }

  @Test
  void allowsShallowNestedLink() {
    UUID parent = UUID.randomUUID();
    UUID child = UUID.randomUUID();
    when(membershipRepository.findParentGroupIdsContainingChild(
            eq(ScimMemberType.GROUP), eq(parent)))
        .thenReturn(List.of());
    when(membershipRepository.findNestedChildGroupIds(eq(ScimMemberType.GROUP), eq(child)))
        .thenReturn(List.of());

    assertThatCode(() -> validation.validateNewNestedMembership(parent, child, 5))
        .doesNotThrowAnyException();
  }
}
