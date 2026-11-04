package africa.finishers.inventory.api;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=InventoryController.class)
public class InventoryErrors {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> problem(AuthProblem e){return ResponseEntity.status(e.status()).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status()),e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> conflict(){return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Stock is being updated. Retry the same receipt request."));}
}
