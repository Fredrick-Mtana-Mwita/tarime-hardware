package africa.finishers.orders.api;

import africa.finishers.orders.application.OrderProblem;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.dao.CannotAcquireLockException;

@RestControllerAdvice(assignableTypes={CustomerOrderController.class,StaffOrderController.class})
public class OrderExceptionHandler {
    @ExceptionHandler(OrderProblem.class)
    public ResponseEntity<ProblemDetail> business(OrderProblem e){return problem(e.status(),e.getMessage());}
    @ExceptionHandler(africa.finishers.identity.application.AuthProblem.class)
    public ResponseEntity<ProblemDetail> access(africa.finishers.identity.application.AuthProblem e){return problem(e.status(),e.getMessage());}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,MissingRequestHeaderException.class})
    public ResponseEntity<ProblemDetail> invalid(){return problem(400,"Check your cart, contact details and retry key.");}
    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ProblemDetail> busy(){return problem(409,"Your cart is being updated. Please retry the same request.");}
    private ResponseEntity<ProblemDetail> problem(int status,String title){
        var detail=ProblemDetail.forStatus(status);detail.setTitle(title);
        var builder=ResponseEntity.status(status).header("Cache-Control","no-store");
        if(status==429)builder.header("Retry-After","3600");
        return builder.body(detail);
    }
}
