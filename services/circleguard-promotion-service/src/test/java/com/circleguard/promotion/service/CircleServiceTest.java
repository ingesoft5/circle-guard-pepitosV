package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CircleServiceTest {

    private CircleNodeRepository repository;
    private HealthStatusService healthStatusService;
    private CircleService service;

    @BeforeEach
    void setUp() {
        repository = mock(CircleNodeRepository.class);
        healthStatusService = mock(HealthStatusService.class);
        service = new CircleService(repository, healthStatusService);
        when(repository.save(any(CircleNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CircleNode circleWith(UserNode... members) {
        return CircleNode.builder().id(7L).name("Lab").members(Set.of(members)).build();
    }

    @Test
    void invalidatingACircleReEvaluatesEveryMember() {
        CircleNode circle = circleWith(UserNode.builder().anonymousId("a").build(), UserNode.builder().anonymousId("b").build());
        when(repository.findById(7L)).thenReturn(Optional.of(circle));

        service.toggleCircleValidity(7L);

        assertThat(circle.getIsValid()).isFalse();
        verify(repository).save(circle);
        verify(healthStatusService).resolveStatus("a");
        verify(healthStatusService).resolveStatus("b");
    }

    @Test
    void revalidatingACircleDoesNotReEvaluateMembers() {
        CircleNode circle = circleWith(UserNode.builder().anonymousId("a").build());
        circle.setIsValid(false);
        when(repository.findById(7L)).thenReturn(Optional.of(circle));

        service.toggleCircleValidity(7L);

        assertThat(circle.getIsValid()).isTrue();
        verify(healthStatusService, never()).resolveStatus(anyString());
    }

    @Test
    void forceFencePromotesOnlyActiveMembers() {
        CircleNode circle = circleWith(
                UserNode.builder().anonymousId("active").status("ACTIVE").build(),
                UserNode.builder().anonymousId("suspect").status("SUSPECT").build());
        when(repository.findById(7L)).thenReturn(Optional.of(circle));

        service.forceFenceCircle(7L);

        assertThat(circle.getForceFence()).isTrue();
        verify(healthStatusService).updateStatus("active", "PROBABLE");
        verify(healthStatusService, never()).updateStatus("suspect", "PROBABLE");
    }

    @Test
    void unknownCircleIsRejected() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleCircleValidity(99L)).hasMessage("Circle not found");
        assertThatThrownBy(() -> service.forceFenceCircle(99L)).hasMessage("Circle not found");
    }

    @Test
    void newCircleGetsAnUnusedInviteCode() {
        when(repository.existsByInviteCode(anyString())).thenReturn(true, false);

        CircleNode circle = service.createCircle("Study group", "ap-1");

        assertThat(circle.getInviteCode()).matches("MESH-[A-Z2-9]{4}");
        assertThat(circle.getName()).isEqualTo("Study group");
        assertThat(circle.getLocationId()).isEqualTo("ap-1");
        assertThat(circle.getIsActive()).isTrue();
        assertThat(circle.getCreatedAt()).isNotNull();
        verify(repository, org.mockito.Mockito.times(2)).existsByInviteCode(anyString());
    }

    @Test
    void joiningAndAddingMembersDelegateToTheGraph() {
        CircleNode circle = circleWith();
        when(repository.joinCircle("u1", "MESH-ABCD")).thenReturn(Optional.of(circle));
        when(repository.addUserToCircle("u2", 7L)).thenReturn(Optional.of(circle));
        when(repository.findCirclesByUser("u1")).thenReturn(List.of(circle));

        assertThat(service.joinCircle("u1", "MESH-ABCD")).isSameAs(circle);
        assertThat(service.addMember(7L, "u2")).isSameAs(circle);
        assertThat(service.getUserCircles("u1")).containsExactly(circle);
    }

    @Test
    void invalidInviteOrMemberIsRejected() {
        when(repository.joinCircle("u1", "BAD")).thenReturn(Optional.empty());
        when(repository.addUserToCircle("u2", 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.joinCircle("u1", "BAD")).hasMessageContaining("Invalid invite code");
        assertThatThrownBy(() -> service.addMember(7L, "u2")).hasMessage("Failed to add user to circle");
    }
}
