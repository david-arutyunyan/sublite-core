package com.sublite.subscription.api;

import com.sublite.subscription.domain.PlanCodeAlreadyExistsException;
import com.sublite.subscription.domain.PlanNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SubscriptionApiExceptionHandler {

    @ExceptionHandler(PlanNotFoundException.class)
    ProblemDetail handleNotFound(PlanNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PlanCodeAlreadyExistsException.class)
    ProblemDetail handleConflict(PlanCodeAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
