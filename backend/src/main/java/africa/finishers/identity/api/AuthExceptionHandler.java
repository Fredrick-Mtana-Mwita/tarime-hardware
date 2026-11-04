package africa.finishers.identity.api;

import africa.finishers.identity.application.AuthProblem;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={CustomerAuthController.class,StaffAuthController.class,StaffManagementController.class})
public class AuthExceptionHandler {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> accountError(AuthProblem exception){
        var problem=ProblemDetail.forStatus(exception.status());problem.setTitle(exception.getMessage());
        var builder=ResponseEntity.status(exception.status()).header("Cache-Control","no-store");
        if(exception.status()==429)builder.header("Retry-After","900");
        return builder.body(problem);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidInput(MethodArgumentNotValidException exception){
        var problem=ProblemDetail.forStatus(400);problem.setTitle(exception.getParameter().getContainingClass()==CustomerAuthController.class
                ?"Check your details. Use a valid name, phone, optional email and a password of 12 to 128 characters."
                :"Check the staff username, name, role and password length. Passwords must contain 12–128 characters.");
        return ResponseEntity.badRequest().header("Cache-Control","no-store").body(problem);
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> malformedInput(){return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(400),"Check the submitted details."));}
}
