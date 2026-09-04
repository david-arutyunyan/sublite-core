package com.sublite.retention.api;

import com.sublite.retention.domain.RetentionOfferCodeAlreadyExistsException;
import com.sublite.retention.domain.RetentionOfferNotFoundException;
import com.sublite.retention.domain.RetentionStepNotFoundException;
import com.sublite.retention.domain.RetentionStepOrderAlreadyExistsException;
import com.sublite.retention.domain.RetentionStepRequiresOfferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RetentionApiExceptionHandler {

    @ExceptionHandler({RetentionOfferNotFoundException.class, RetentionStepNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({RetentionOfferCodeAlreadyExistsException.class, RetentionStepOrderAlreadyExistsException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RetentionStepRequiresOfferException.class)
    ProblemDetail handleBadRequest(RetentionStepRequiresOfferException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
