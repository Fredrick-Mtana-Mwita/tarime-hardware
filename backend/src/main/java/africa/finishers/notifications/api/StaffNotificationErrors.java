package africa.finishers.notifications.api;
import africa.finishers.identity.application.AuthProblem;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=StaffNotificationController.class)
public class StaffNotificationErrors {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> problem(AuthProblem e){return ResponseEntity.status(e.status()).body(ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(e.status()),e.getMessage()));}
}
