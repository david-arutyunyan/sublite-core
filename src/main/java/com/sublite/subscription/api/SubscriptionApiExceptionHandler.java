package com.sublite.subscription.api;

import com.sublite.subscription.domain.CustomerAlreadySubscribedException;
import com.sublite.subscription.domain.NoActiveSubscriptionException;
import com.sublite.subscription.domain.PlanCodeAlreadyExistsException;
import com.sublite.subscription.domain.PlanNotFoundException;
import com.sublite.subscription.domain.PlanPriceNotFoundException;
import com.sublite.subscription.domain.SubscriptionNotFoundException;
import com.sublite.subscription.domain.SubscriptionNotInGracePeriodException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles subscription.domain exceptions regardless of which controller
 * throws them - @RestControllerAdvice is resolved globally by exception
 * type, not by the advice bean's own package, so this also covers
 * billing.api.SubscriptionController (see its own javadoc for why buying
 * a subscription lives in the billing module) and
 * retention.api.RetentionCustomerController (via RetentionCustomerService,
 * which throws SubscriptionNotFoundException for the ownership check).
 */
@RestControllerAdvice
public class SubscriptionApiExceptionHandler {

    @ExceptionHandler({PlanNotFoundException.class, PlanPriceNotFoundException.class, NoActiveSubscriptionException.class, SubscriptionNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({PlanCodeAlreadyExistsException.class, CustomerAlreadySubscribedException.class, SubscriptionNotInGracePeriodException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
