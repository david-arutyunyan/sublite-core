package com.sublite.loyalty.api;

import com.sublite.loyalty.api.dto.LoyaltyRuleResponse;
import com.sublite.loyalty.api.dto.SetLoyaltyRuleRequest;
import com.sublite.loyalty.application.LoyaltyRuleAdminService;
import com.sublite.loyalty.domain.LoyaltyRule;
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
public class LoyaltyRuleAdminController {

    private final LoyaltyRuleAdminService service;

    public LoyaltyRuleAdminController(LoyaltyRuleAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoyaltyRuleResponse setRule(@Valid @RequestBody SetLoyaltyRuleRequest request) {
        LoyaltyRule rule = service.setRule(request.eventType(), request.points());
        return LoyaltyRuleResponse.from(rule);
    }

    @GetMapping
    public List<LoyaltyRuleResponse> list() {
        return service.listRules().stream().map(LoyaltyRuleResponse::from).toList();
    }
}
