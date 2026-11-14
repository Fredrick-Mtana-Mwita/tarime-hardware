package africa.finishers.finance.api;

import africa.finishers.identity.application.AuthProblem;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={StaffFinanceController.class,CustomerPaymentController.class})
public class FinanceErrors {
    @ExceptionHandler(AuthProblem.class)
    public ResponseEntity<ProblemDetail> problem(AuthProblem e){return problem(e.status(),e.getMessage());}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalid(){return problem(400,"Check the amount, method and other details, then retry.");}
    /** Two staff saving the same mobile money transaction at once: the database keeps one. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> duplicate(){return problem(409,"This transaction or receipt is already recorded. Refresh the order.");}
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ProblemDetail> busy(){return problem(409,"Someone else is updating this order. Refresh and retry.");}
    private static ResponseEntity<ProblemDetail> problem(int status,String title){
        var detail=ProblemDetail.forStatus(status);detail.setTitle(title);detail.setDetail(title);
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(detail);
    }
}
