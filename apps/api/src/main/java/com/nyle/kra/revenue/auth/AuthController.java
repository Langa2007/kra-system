package com.nyle.kra.revenue.auth;

import java.util.Optional;
import java.util.Map;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.identity.AuthCredential;
import com.nyle.kra.revenue.identity.AuthCredentialRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import com.nyle.kra.revenue.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(
            AppUserRepository appUserRepository,
            AuthCredentialRepository authCredentialRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.appUserRepository = appUserRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request
    ) {
        Optional<AppUser> user = appUserRepository.findByEmailIgnoreCase(loginRequest.email());

        if (user.isEmpty() || !"ACTIVE".equals(user.get().getStatus())) {
            auditService.record(AuditService.LOGIN_FAILURE, Optional.empty(), "app_users", null, request, Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        AuthCredential credential = authCredentialRepository.findById(user.get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(loginRequest.password(), credential.getPasswordHash())) {
            auditService.record(AuditService.LOGIN_FAILURE, user, "app_users", user.get().getId(), request, Map.of());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        auditService.record(AuditService.LOGIN_SUCCESS, user, "app_users", user.get().getId(), request, Map.of());

        return new LoginResponse(
                "Bearer",
                jwtService.issueToken(user.get()),
                toSummary(user.get())
        );
    }

    @GetMapping("/me")
    public LoginResponse.UserSummary me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(authenticatedUser.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return toSummary(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }

    private LoginResponse.UserSummary toSummary(AppUser user) {
        return new LoginResponse.UserSummary(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles().stream().map(role -> role.getCode()).sorted().toList()
        );
    }
}
