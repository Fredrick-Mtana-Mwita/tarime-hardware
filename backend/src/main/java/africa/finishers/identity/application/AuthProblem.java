package africa.finishers.identity.application;

public class AuthProblem extends RuntimeException {
    private final int status;
    public AuthProblem(int status,String message){super(message);this.status=status;}
    public int status(){return status;}
}
