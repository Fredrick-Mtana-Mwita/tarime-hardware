package africa.finishers.orders.application;

public class OrderProblem extends RuntimeException {
    private final int status;
    public OrderProblem(int status,String message){super(message);this.status=status;}
    public int status(){return status;}
}
