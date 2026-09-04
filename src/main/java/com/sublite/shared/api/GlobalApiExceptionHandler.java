package com.sublite.shared.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Truly cross-module concerns, unlike SubscriptionApiExceptionHandler/
 * RetentionApiExceptionHandler/AuthExceptionHandler which each map one
 * module's own domain exceptions. A lost @Version race
 * (ObjectOptimisticLockingFailureException) can surface from a
 * subscription-lifecycle write, a retention-flow write, or a loyalty
 * redeem - any of them touching a row someone else updated in between the
 * read and the write - and every caller should see the same clean 409
 * ("try again") rather than Boot's generic unstructured 500.
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This was just updated by another request - please retry.");
    }
}
