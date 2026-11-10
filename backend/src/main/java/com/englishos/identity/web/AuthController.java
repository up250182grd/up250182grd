package com.englishos.identity.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.application.LoginService;
import com.englishos.identity.application.PasswordResetService;
import com.englishos.identity.application.ProfileService;
import com.englishos.identity.application.ProfileService.MeView;
import com.englishos.identity.application.RegistrationService;
import com.englishos.identity.application.RegistrationService.RegisterCommand;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrationService registration;
    private final LoginService loginService;
    private final PasswordResetService passwordReset;
    private final ProfileService profiles;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(RegistrationService registration, LoginService loginService, PasswordResetService passwordReset,
            ProfileService profiles, SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.registration = registration;
        this.loginService = loginService;
        this.passwordReset = passwordReset;
        this.profiles = profiles;
        this.securityContextRepository = securityContextRepository;
        this.csrfAuthenticationStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
    }

    // ---- DTOs -------------------------------------------------------------------------------------

    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(min = 1, max = 80) String displayName,
            @AssertTrue(message = "Debes aceptar los términos de uso") boolean acceptTerms,
            @AssertTrue(message = "Debes aceptar el aviso de privacidad") boolean acceptPrivacy) {
    }

    record RegisterResponse(String publicId, String email, boolean emailVerificationRequired) {
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
    }

    record TokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record PasswordResetConfirmRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    record CsrfResponse(String headerName, String token) {
    }

    // ---- Endpoints --------------------------------------------------------------------------------

    /** La SPA llama a este endpoint al cargar para obtener la cookie XSRF-TOKEN. */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        var user = registration.registerStudent(new RegisterCommand(req.email(), req.password(), req.displayName(),
                req.acceptTerms(), req.acceptPrivacy()));
        return new RegisterResponse(user.getPublicId(), user.getEmail(), true);
    }

    @PostMapping("/login")
    MeView login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        CurrentUser user = loginService.authenticate(req.email(), req.password());

        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, authorities(user));
        // Protección contra fijación de sesión: nuevo ID de sesión tras autenticar.
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Nuevo token CSRF tras el login.
        csrfAuthenticationStrategy.onAuthentication(authentication, request, response);
        return profiles.me(user);
    }

    @PostMapping("/verify-email")
    Map<String, Boolean> verifyEmail(@Valid @RequestBody TokenRequest req) {
        registration.verifyEmail(req.token());
        return Map.of("verified", true);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody EmailRequest req) {
        registration.resendVerification(req.email());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody EmailRequest req) {
        passwordReset.requestReset(req.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        passwordReset.confirmReset(req.token(), req.newPassword());
    }

    static List<GrantedAuthority> authorities(CurrentUser user) {
        List<GrantedAuthority> list = new ArrayList<>();
        user.roles().forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r)));
        user.permissions().forEach(p -> list.add(new SimpleGrantedAuthority(p)));
        return list;
    }
}
