package com.possaas.identity.api;

import com.possaas.identity.api.dto.AuthDtos.AcceptInviteRequest;
import com.possaas.identity.api.dto.AuthDtos.BootstrapPlatformAdminRequest;
import com.possaas.identity.api.dto.AuthDtos.ForgotPasswordRequest;
import com.possaas.identity.api.dto.AuthDtos.LoginRequest;
import com.possaas.identity.api.dto.AuthDtos.LogoutRequest;
import com.possaas.identity.api.dto.AuthDtos.MessageResponse;
import com.possaas.identity.api.dto.AuthDtos.RefreshRequest;
import com.possaas.identity.api.dto.AuthDtos.ResetPasswordRequest;
import com.possaas.identity.api.dto.AuthDtos.SignupRequest;
import com.possaas.identity.api.dto.AuthDtos.TokenResponse;
import com.possaas.identity.api.dto.AuthDtos.UserResponse;
import com.possaas.identity.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    /**
     * Creates the platform operator account. Only works while none exists yet
     * (enforced in AuthService, not here) - after that it always 409s, so this
     * stays safe to leave reachable rather than needing to be torn out post-launch.
     */
    @PostMapping("/bootstrap-platform-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse bootstrapPlatformAdmin(@Valid @RequestBody BootstrapPlatformAdminRequest request) {
        return authService.bootstrapPlatformAdmin(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request == null ? new LogoutRequest(null) : request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/accept-invite")
    public TokenResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }
}
