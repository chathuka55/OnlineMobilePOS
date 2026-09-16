package com.possaas.identity.api;

import com.possaas.common.api.PageResponse;
import com.possaas.identity.api.dto.AuthDtos.AccessTokenResponse;
import com.possaas.identity.api.dto.AuthDtos.InviteUserRequest;
import com.possaas.identity.api.dto.AuthDtos.InviteUserResponse;
import com.possaas.identity.api.dto.AuthDtos.StepUpRequest;
import com.possaas.identity.api.dto.AuthDtos.UpdateUserRequest;
import com.possaas.identity.api.dto.AuthDtos.UserResponse;
import com.possaas.identity.domain.UserStatus;
import com.possaas.identity.service.AuthService;
import com.possaas.identity.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user.view')")
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return userService.list(search, status, pageable);
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user.manage')")
    public InviteUserResponse invite(@Valid @RequestBody InviteUserRequest request) {
        return userService.invite(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('user.manage')")
    public UserResponse update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('user.manage')")
    public UserResponse disable(@PathVariable UUID id) {
        return userService.disable(id);
    }

    @PostMapping("/step-up")
    public AccessTokenResponse stepUp(@Valid @RequestBody StepUpRequest request) {
        return authService.stepUp(request);
    }
}
