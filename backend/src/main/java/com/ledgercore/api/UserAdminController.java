package com.ledgercore.api;

import com.ledgercore.auth.AuthPrincipal;
import com.ledgercore.authz.AuthzService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User role management (Requirements 3.4, 3.6, 3.9). ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final AuthzService authzService;

    public UserAdminController(AuthzService authzService) {
        this.authzService = authzService;
    }

    public record ChangeRoleRequest(@NotBlank String role) {
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public Envelopes.Success<String> changeRole(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody ChangeRoleRequest request) {
        authzService.changeUserRole(principal, id, request.role());
        return Envelopes.ok("role_updated");
    }
}
