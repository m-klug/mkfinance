package br.com.mkfinance.application.workspace;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.MemberId;
import br.com.mkfinance.domain.model.MemberRole;
import br.com.mkfinance.domain.model.User;
import br.com.mkfinance.domain.model.Workspace;
import br.com.mkfinance.domain.model.WorkspaceInvite;
import br.com.mkfinance.infrastructure.persistence.MemberRepository;
import br.com.mkfinance.infrastructure.persistence.UserRepository;
import br.com.mkfinance.infrastructure.persistence.WorkspaceInviteRepository;
import br.com.mkfinance.infrastructure.persistence.WorkspaceRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final WorkspaceInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final WorkspaceDefaultsSeeder defaultsSeeder;

    /** Creates the workspace, makes {@code owner} its OWNER member, and seeds defaults (BE-2). */
    public Workspace createWorkspace(User owner, String name) {
        return createWorkspace(owner.getId(), name);
    }

    /** Same as {@link #createWorkspace(User, String)} but only needs the owner's id. */
    public Workspace createWorkspace(UUID ownerId, String name) {
        Workspace workspace = workspaceRepository.save(Workspace.builder().name(name).build());
        Member member = Member.builder()
                .id(new MemberId(workspace.getId(), ownerId))
                .workspace(workspace)
                .user(userRepository.getReferenceById(ownerId))
                .role(MemberRole.OWNER)
                .build();
        memberRepository.save(member);
        defaultsSeeder.seed(workspace);
        return workspace;
    }

    @Transactional(readOnly = true)
    public Member getMembership(UUID workspaceId, UUID userId) {
        return memberRepository.findById(new MemberId(workspaceId, userId)).orElseThrow(AppException::notAMember);
    }

    @Transactional(readOnly = true)
    public List<Member> listMemberships(UUID userId) {
        return memberRepository.findByUserIdWithWorkspace(userId);
    }

    public WorkspaceInvite createInvite(UUID workspaceId, UUID createdByUserId) {
        WorkspaceInvite invite = WorkspaceInvite.builder()
                .token(generateToken())
                .workspace(workspaceRepository.getReferenceById(workspaceId))
                .createdBy(userRepository.getReferenceById(createdByUserId))
                .expiresAt(OffsetDateTime.now().plus(INVITE_TTL))
                .build();
        return inviteRepository.save(invite);
    }

    /** Joins {@code user} as MEMBER of the invite's workspace and marks the invite used. */
    public Member acceptInvite(String token, User user) {
        WorkspaceInvite invite = inviteRepository.findById(token).orElseThrow(AppException::inviteExpired);
        if (invite.getUsedAt() != null || invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw AppException.inviteExpired();
        }
        // invite.getWorkspace() is a lazy proxy; getId() is safe on it (no fetch needed), but we
        // re-load a fully-initialized Workspace so the returned Member can be serialized safely
        // after this transaction/session closes (avoids LazyInitializationException).
        UUID workspaceId = invite.getWorkspace().getId();
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(AppException::notAMember);
        MemberId memberId = new MemberId(workspaceId, user.getId());

        Member member = memberRepository.findById(memberId).orElseGet(() -> memberRepository.save(
                Member.builder().id(memberId).workspace(workspace).user(user).role(MemberRole.MEMBER).build()));
        member.setWorkspace(workspace);

        invite.setUsedAt(OffsetDateTime.now());
        inviteRepository.save(invite);
        return member;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
