package africa.finishers.platform.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The database refuses new work for a closed branch (V11). Where that refusal reaches a request that the
 * modules do not check themselves, such as confirming or dispatching an older Mwanza order, report it as a
 * clear conflict instead of a server error. Every other database error is passed on unchanged.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ClosedBranchErrors {
    static final String MARKER="This branch is closed";
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> closed(DataAccessException e) throws DataAccessException {
        for(Throwable cause=e;cause!=null;cause=cause.getCause())
            if(cause.getMessage()!=null&&cause.getMessage().contains(MARKER))
                return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"This branch is closed. New stock movements and orders use Tarime; earlier records stay available to read."));
        throw e;
    }
}
