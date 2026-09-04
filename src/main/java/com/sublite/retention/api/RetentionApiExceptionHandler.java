package com.sublite.retention.api;

import com.sublite.retention.domain.CancellationAttemptNotFoundException;
import com.sublite.retention.domain.InvalidCancellationStepException;
import com.sublite.retention.domain.RetentionOfferCodeAlreadyExistsException;
import com.sublite.retention.domain.RetentionOfferInUseException;
import com.sublite.retention.domain.RetentionOfferNotActiveException;
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

    @ExceptionHandler({RetentionOfferNotFoundException.class, RetentionStepNotFoundException.class, CancellationAttemptNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({RetentionOfferCodeAlreadyExistsException.class, RetentionStepOrderAlreadyExistsException.class, RetentionOfferInUseException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({RetentionStepRequiresOfferException.class, InvalidCancellationStepException.class, RetentionOfferNotActiveException.class})
    ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
