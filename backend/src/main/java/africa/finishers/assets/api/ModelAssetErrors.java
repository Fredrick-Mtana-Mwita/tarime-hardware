package africa.finishers.assets.api;

import africa.finishers.identity.application.AuthProblem;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ModelAssetController.class)
public class ModelAssetErrors {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> problem(AuthProblem e){return ResponseEntity.status(e.status()).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status()),e.getMessage()));}
    @ExceptionHandler({org.springframework.dao.ConcurrencyFailureException.class,org.springframework.dao.DuplicateKeyException.class})
    public ResponseEntity<ProblemDetail> conflict(){return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"This model was changed at the same time. Refresh and try again."));}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> unreadable(){return ResponseEntity.status(400).body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"The request could not be read. Refresh and try again."));}
}
