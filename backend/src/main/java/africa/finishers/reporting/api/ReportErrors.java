package africa.finishers.reporting.api;

import africa.finishers.identity.application.AuthProblem;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=ReportController.class)
public class ReportErrors {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> problem(AuthProblem e){return problem(e.status(),e.getMessage());}
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> invalid(){return problem(400,"Use dates in the form YYYY-MM-DD.");}
    private static ResponseEntity<ProblemDetail> problem(int status,String title){
        var d=ProblemDetail.forStatus(status);d.setTitle(title);d.setDetail(title);
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(d);
    }
}
