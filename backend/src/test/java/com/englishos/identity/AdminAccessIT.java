package com.englishos.identity;

import java.util.List;
import java.util.Map;

import com.englishos.audit.AuditService;
import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-IDN-5, RF-IDN-8, RF-ADM-1: autorización en backend, suspensión, roles y auditoría. */
class AdminAccessIT extends IntegrationTest {

    @Autowired
    AuditService auditService;

    @Test
    void bootstrapAdminExistsWithAdminRole() {
        var me = admin().get("/api/v1/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().path("roles").toString()).contains("ADMIN");
        assertThat(me.json().path("permissions").toString()).contains("user:suspend");
        assertThat(me.json().path("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void studentCannotUseAdminEndpoints() {
        ApiClient student = verifiedStudent(uniqueEmail());
        var list = student.get("/api/v1/admin/users");
        assertThat(list.status()).isEqualTo(403);
        assertThat(list.code()).isEqualTo("FORBIDDEN");

        String someone = admin().get("/api/v1/admin/users?size=1").json().path("items").get(0)
                .path("publicId").asString();
        assertThat(student.post("/api/v1/admin/users/" + someone + "/suspend", null).status()).isEqualTo(403);
        assertThat(student.put("/api/v1/admin/users/" + someone + "/roles", Map.of("roles", List.of("ADMIN")))
                .status()).isEqualTo(403);
    }

    @Test
    void adminCanSearchUsers() {
        String email = uniqueEmail();
        verifiedStudent(email);
        var res = admin().get("/api/v1/admin/users?q=" + email.substring(0, 20));
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.json().path("items").toString()).contains(email);
        assertThat(res.json().path("items").toString()).doesNotContain("passwordHash");
    }

    @Test
    void suspendingRevokesSessionsBlocksLoginAndIsAudited() {
        String email = uniqueEmail();
        ApiClient student = verifiedStudent(email);
        String publicId = student.get("/api/v1/me").json().path("publicId").asString();

        ApiClient admin = admin();
        var suspended = admin.post("/api/v1/admin/users/" + publicId + "/suspend", null);
        assertThat(suspended.status()).isEqualTo(200);
        assertThat(suspended.json().path("status").asString()).isEqualTo("SUSPENDED");

        assertThat(student.get("/api/v1/me").status()).as("sesión revocada").isEqualTo(401);
        var login = newClient().login(email, STRONG_PASSWORD);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.code()).isEqualTo("ACCOUNT_SUSPENDED");

        assertThat(auditService.findByTarget("USER", publicId))
                .extracting(AuditService.AuditEntry::action)
                .contains("USER_SUSPENDED");

        assertThat(admin.post("/api/v1/admin/users/" + publicId + "/reactivate", null).status()).isEqualTo(200);
        assertThat(newClient().login(email, STRONG_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void adminCannotSuspendOrDemoteSelf() {
        ApiClient admin = admin();
        String adminId = admin.get("/api/v1/me").json().path("publicId").asString();
        assertThat(admin.post("/api/v1/admin/users/" + adminId + "/suspend", null).code())
                .isEqualTo("CANNOT_SUSPEND_SELF");
        assertThat(admin.put("/api/v1/admin/users/" + adminId + "/roles", Map.of("roles", List.of("STUDENT"))).code())
                .isEqualTo("CANNOT_DEMOTE_SELF");
    }

    @Test
    void roleChangeTakesEffectImmediatelyByRevokingSessions() {
        String email = uniqueEmail();
        ApiClient student = verifiedStudent(email);
        String publicId = student.get("/api/v1/me").json().path("publicId").asString();

        var changed = admin().put("/api/v1/admin/users/" + publicId + "/roles",
                Map.of("roles", List.of("STUDENT", "ADMIN")));
        assertThat(changed.status()).isEqualTo(200);
        assertThat(student.get("/api/v1/me").status()).isEqualTo(401);

        ApiClient promoted = newClient();
        assertThat(promoted.login(email, STRONG_PASSWORD).status()).isEqualTo(200);
        assertThat(promoted.get("/api/v1/admin/users").status()).isEqualTo(200);
        assertThat(auditService.findByTarget("USER", publicId))
                .extracting(AuditService.AuditEntry::action)
                .contains("USER_ROLES_CHANGED");
    }

    @Test
    void registrationNeverGrantsAdminEvenIfRequested() {
        String email = uniqueEmail();
        ApiClient client = newClient();
        var res = client.post("/api/v1/auth/register", Map.of(
                "email", email, "password", STRONG_PASSWORD, "displayName", "Mallory",
                "acceptTerms", true, "acceptPrivacy", true, "roles", List.of("ADMIN")));
        assertThat(res.status()).isEqualTo(201);
        client.login(email, STRONG_PASSWORD);
        assertThat(client.get("/api/v1/me").json().path("roles").toString()).doesNotContain("ADMIN");
        assertThat(client.get("/api/v1/admin/users").status()).isEqualTo(403);
    }
}
