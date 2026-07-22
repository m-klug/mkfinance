package br.com.mkfinance.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.LoginRequest;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.pillar.PillarAllocationResponse;
import br.com.mkfinance.api.workspace.InviteResponse;
import br.com.mkfinance.api.workspace.WorkspaceResponse;
import br.com.mkfinance.domain.model.MemberRole;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.support.ApiSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerCreatesUserPersonalWorkspaceOwnerMembershipAndSeeds() {
        ApiSession session = newSession();
        session.primeCsrf();

        ResponseEntity<CurrentUserResponse> response = session.post(
                "/auth/register",
                new RegisterRequest("Ada Lovelace", uniqueEmail(), "s3cr3t-password"),
                CurrentUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CurrentUserResponse user = response.getBody();
        assertThat(user).isNotNull();
        assertThat(user.workspaces()).hasSize(1);
        WorkspaceResponse workspace = user.workspaces().get(0);
        assertThat(workspace.role()).isEqualTo(MemberRole.OWNER);

        // categories copied from category_template (V2__seed.sql has 22 rows: 6 FIXED_COSTS +
        // 3 COMFORT + 1 GOALS + 5 PLEASURES + 1 FINANCIAL_FREEDOM + 2 KNOWLEDGE + 4 INCOME)
        ResponseEntity<CategoryResponse[]> categories = session.get(
                "/workspaces/" + workspace.id() + "/categories", CategoryResponse[].class);
        assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categories.getBody()).hasSize(22);

        // fixed pillar allocation 30/10/20/10/25/5, summing to 100
        ResponseEntity<PillarAllocationResponse[]> allocations = session.get(
                "/workspaces/" + workspace.id() + "/pillar-allocations", PillarAllocationResponse[].class);
        assertThat(allocations.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<Pillar, Integer> byPillar = new java.util.HashMap<>();
        for (PillarAllocationResponse allocation : allocations.getBody()) {
            byPillar.put(allocation.pillar(), allocation.targetPercent());
        }
        assertThat(byPillar)
                .containsEntry(Pillar.FIXED_COSTS, 30)
                .containsEntry(Pillar.COMFORT, 10)
                .containsEntry(Pillar.GOALS, 20)
                .containsEntry(Pillar.PLEASURES, 10)
                .containsEntry(Pillar.FINANCIAL_FREEDOM, 25)
                .containsEntry(Pillar.KNOWLEDGE, 5);
        assertThat(byPillar.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    @Test
    void duplicateEmailIsRejected() {
        String email = uniqueEmail();
        ApiSession first = newSession();
        first.primeCsrf();
        first.post("/auth/register", new RegisterRequest("First", email, "s3cr3t-password"), CurrentUserResponse.class);

        ApiSession second = newSession();
        second.primeCsrf();
        ResponseEntity<ErrorResponse> response =
                second.post("/auth/register", new RegisterRequest("Second", email, "other-password"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_USED");
    }

    @Test
    void loginThenLogoutInvalidatesSession() {
        String email = uniqueEmail();
        ApiSession registerSession = newSession();
        registerSession.primeCsrf();
        registerSession.post("/auth/register", new RegisterRequest("Grace Hopper", email, "s3cr3t-password"), CurrentUserResponse.class);

        ApiSession loginSession = newSession();
        loginSession.primeCsrf();
        ResponseEntity<CurrentUserResponse> loginResponse =
                loginSession.post("/auth/login", new LoginRequest(email, "s3cr3t-password"), CurrentUserResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<CurrentUserResponse> me = loginSession.get("/auth/me", CurrentUserResponse.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().email()).isEqualTo(email);

        ResponseEntity<Void> logout = loginSession.post("/auth/logout", null, Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ErrorResponse> meAfterLogout = loginSession.get("/auth/me", ErrorResponse.class);
        assertThat(meAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordIsInvalidCredentials() {
        String email = uniqueEmail();
        ApiSession registerSession = newSession();
        registerSession.primeCsrf();
        registerSession.post("/auth/register", new RegisterRequest("Alan Turing", email, "s3cr3t-password"), CurrentUserResponse.class);

        ApiSession loginSession = newSession();
        loginSession.primeCsrf();
        ResponseEntity<ErrorResponse> response =
                loginSession.post("/auth/login", new LoginRequest(email, "wrong-password"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void accessingAForeignWorkspaceReturnsForbiddenNotAMember() {
        ApiSession userA = newSession();
        userA.primeCsrf();
        CurrentUserResponse a = userA
                .post("/auth/register", new RegisterRequest("User A", uniqueEmail(), "s3cr3t-password"), CurrentUserResponse.class)
                .getBody();
        UUID workspaceAId = a.workspaces().get(0).id();

        ApiSession userB = newSession();
        userB.primeCsrf();
        userB.post("/auth/register", new RegisterRequest("User B", uniqueEmail(), "s3cr3t-password"), CurrentUserResponse.class);

        ResponseEntity<ErrorResponse> response =
                userB.get("/workspaces/" + workspaceAId + "/accounts", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("NOT_A_MEMBER");
    }

    @Test
    void anonymousRequestIsUnauthenticated() {
        ApiSession session = newSession();
        ResponseEntity<ErrorResponse> response = session.get("/auth/me", ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void inviteAcceptFlowAddsMemberWhoCanThenAccessTheWorkspace() {
        ApiSession owner = newSession();
        owner.primeCsrf();
        CurrentUserResponse ownerUser = owner
                .post("/auth/register", new RegisterRequest("Owner", uniqueEmail(), "s3cr3t-password"), CurrentUserResponse.class)
                .getBody();
        UUID workspaceId = ownerUser.workspaces().get(0).id();

        ResponseEntity<InviteResponse> inviteResponse =
                owner.post("/workspaces/" + workspaceId + "/invites", null, InviteResponse.class);
        assertThat(inviteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = inviteResponse.getBody().token();

        ApiSession invitee = newSession();
        invitee.primeCsrf();
        invitee.post("/auth/register", new RegisterRequest("Invitee", uniqueEmail(), "s3cr3t-password"), CurrentUserResponse.class);

        ResponseEntity<WorkspaceResponse> acceptResponse =
                invitee.post("/invites/" + token + "/accept", null, WorkspaceResponse.class);
        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acceptResponse.getBody().id()).isEqualTo(workspaceId);
        assertThat(acceptResponse.getBody().role()).isEqualTo(MemberRole.MEMBER);

        ResponseEntity<AccountResponse[]> accounts =
                invitee.get("/workspaces/" + workspaceId + "/accounts", AccountResponse[].class);
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredOrUnknownInviteTokenIsGone() {
        ApiSession session = newSession();
        session.primeCsrf();
        session.post("/auth/register", new RegisterRequest("Someone", uniqueEmail(), "s3cr3t-password"), CurrentUserResponse.class);

        ResponseEntity<ErrorResponse> response = session.post("/invites/does-not-exist/accept", null, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().code()).isEqualTo("INVITE_EXPIRED");
    }

    static String uniqueEmail() {
        return "test+" + UUID.randomUUID() + "@example.com";
    }
}
