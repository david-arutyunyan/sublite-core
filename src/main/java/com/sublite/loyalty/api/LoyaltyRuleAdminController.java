package com.sublite.loyalty.api;

import com.sublite.loyalty.api.dto.LoyaltyRuleResponse;
import com.sublite.loyalty.api.dto.SetLoyaltyRuleRequest;
import com.sublite.loyalty.application.LoyaltyRuleAdminService;
import com.sublite.loyalty.domain.LoyaltyRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Under /admin - see SecurityConfig, gated to ROLE_ADMIN by URL prefix.
 */
@RestController
@RequestMapping("/admin/loyalty/rules")
@Tag(name = "Admin: Loyalty", description = "Point-award rules per event type")
@SecurityRequirement(name = "bearerAuth")
public class LoyaltyRuleAdminController {

    private final LoyaltyRuleAdminService service;

    public LoyaltyRuleAdminController(LoyaltyRuleAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Set the active rule for an event type",
            description = "Deactivates whichever rule currently governs this event type and creates a new "
                    + "active one - only one active rule per event type is allowed.")
    public LoyaltyRuleResponse setRule(@Valid @RequestBody SetLoyaltyRuleRequest request) {
        LoyaltyRule rule = service.setRule(request.eventType(), request.points());
        return LoyaltyRuleResponse.from(rule);
    }

    @GetMapping
    public List<LoyaltyRuleResponse> list() {
        return service.listRules().stream().map(LoyaltyRuleResponse::from).toList();
    }
}
