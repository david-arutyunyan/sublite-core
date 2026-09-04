package com.sublite.retention.api;

import com.sublite.retention.api.dto.CreateRetentionOfferRequest;
import com.sublite.retention.api.dto.CreateRetentionStepRequest;
import com.sublite.retention.api.dto.RetentionOfferResponse;
import com.sublite.retention.api.dto.RetentionStepResponse;
import com.sublite.retention.application.RetentionAdminService;
import com.sublite.retention.domain.RetentionOffer;
import com.sublite.retention.domain.RetentionStep;
import com.sublite.shared.api.dto.SetActiveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Under /admin - see SecurityConfig, gated to ROLE_ADMIN by URL prefix.
 */
@RestController
@RequestMapping("/admin/retention")
@Tag(name = "Admin: Retention", description = "Cancellation-flow offers and steps")
@SecurityRequirement(name = "bearerAuth")
public class RetentionAdminController {

    private final RetentionAdminService service;

    public RetentionAdminController(RetentionAdminService service) {
        this.service = service;
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public RetentionOfferResponse createOffer(@Valid @RequestBody CreateRetentionOfferRequest request) {
        RetentionOffer offer = service.createOffer(request.code(), request.type(), request.parameters());
        return RetentionOfferResponse.from(offer);
    }

    @GetMapping("/offers")
    public List<RetentionOfferResponse> listOffers() {
        return service.listOffers().stream().map(RetentionOfferResponse::from).toList();
    }

    @PostMapping("/steps")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a step to the cancellation flow",
            description = "offerId is required when type is OFFER, and must not be set otherwise. "
                    + "stepOrder must be unique across the whole flow.")
    public RetentionStepResponse createStep(@Valid @RequestBody CreateRetentionStepRequest request) {
        RetentionStep step = service.createStep(request.stepOrder(), request.type(), request.offerId());
        return RetentionStepResponse.from(step);
    }

    @GetMapping("/steps")
    public List<RetentionStepResponse> listSteps() {
        return service.listSteps().stream().map(RetentionStepResponse::from).toList();
    }

    @PatchMapping("/steps/{id}/active")
    public RetentionStepResponse setStepActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest request) {
        return RetentionStepResponse.from(service.setStepActive(id, request.active()));
    }
}
