package africa.finishers.identity.application;

public interface AuthenticationThrottle {
    enum Operation { LOGIN, REGISTRATION, STAFF_LOGIN, STAFF_ACCOUNT_LOGIN, STAFF_PASSWORD_CHANGE }
    void check(Operation operation,String clientAddress);
}
