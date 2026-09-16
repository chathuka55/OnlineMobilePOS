package com.possaas.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Request/response shapes for the identity HTTP surface.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Size(max = 160) String businessName,
            @NotBlank @Email @Size(max = 255) String contactEmail,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 40) String slug,
            @Size(max = 32) String phone
    ) {
    }

    public record BootstrapPlatformAdminRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank String emailOrUsername,
            @NotBlank String password,
            String tenantSlug,
            String deviceId,
            String deviceLabel
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email String email,
            String tenantSlug
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 160) String fullName
    ) {
    }

    public record LogoutRequest(
            String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Instant expiresAt,
            UserResponse user
    ) {
        public TokenResponse(String accessToken, String refreshToken, long expiresIn,
                             Instant expiresAt, UserResponse user) {
            this(accessToken, refreshToken, "Bearer", expiresIn, expiresAt, user);
        }
    }

    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            UUID tenantId,
            String tenantSlug,
            Set<String> roles,
            Set<String> permissions,
            boolean platformAdmin,
            boolean hasPin
    ) {
    }

    public record InviteUserRequest(
            @NotBlank @Email String email,
            @Size(max = 160) String fullName,
            @NotBlank String roleCode
    ) {
    }

    public record InviteUserResponse(
            UserResponse user,
            String inviteToken,
            Instant expiresAt
    ) {
    }

    public record UpdateUserRequest(
            @Size(max = 160) String fullName,
            @Size(max = 32) String phone,
            String roleCode
    ) {
    }

    public record StepUpRequest(
            @NotBlank String password
    ) {
    }

    public record AccessTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            Instant expiresAt
    ) {
        public AccessTokenResponse(String accessToken, long expiresIn, Instant expiresAt) {
            this(accessToken, "Bearer", expiresIn, expiresAt);
        }
    }

    public record MessageResponse(String message) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    public record SetPinRequest(
            @NotBlank String password,
            @Pattern(regexp = "^(\\d{4,6})?$", message = "PIN must be 4 to 6 digits, or empty to remove it")
            String pin
    ) {
    }

    public record VerifyPinRequest(
            @NotBlank String pin
    ) {
    }
}
