package com.sublite.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the "bearerAuth" scheme (so Swagger UI shows an Authorize
 * button and knows how to attach the token), but doesn't apply it
 * globally - /health and POST /auth/login are genuinely public (see
 * SecurityConfig), so marking every operation as requiring a bearer
 * token would document endpoints as protected when they aren't. Each
 * controller that IS behind auth carries its own
 * @SecurityRequirement("bearerAuth") instead - see PlanAdminController,
 * RetentionAdminController, LoyaltyRuleAdminController, and
 * AuthController.me().
 *
 * Lives in shared, not security: it only references springdoc/swagger
 * types, nothing from the security module, so it doesn't create the
 * shared-depends-on-a-business-module cycle ArchUnit's ModuleBoundaryTest
 * checks for.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI subliteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sublite Core API")
                        .description("Subscription management platform: plans, subscription lifecycle, "
                                + "billing, retention flow, loyalty points. Public endpoints: /health and "
                                + "/auth/login. Everything under /admin/** requires an ADMIN token - log in "
                                + "as admin@sublite.dev (see V23 migration for the seeded demo password), "
                                + "then Authorize with the returned accessToken.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
