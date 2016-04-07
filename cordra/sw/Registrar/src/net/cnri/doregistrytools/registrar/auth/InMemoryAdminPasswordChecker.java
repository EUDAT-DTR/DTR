package net.cnri.doregistrytools.registrar.auth;

public class InMemoryAdminPasswordChecker implements AdminPasswordCheckerInterface {

    private String password;
    public InMemoryAdminPasswordChecker(String password) {
        this.password = password;
    }
    
    @Override
    public boolean check(String password) {
        return this.password.equals(password);
    }

    @Override
    public void setPassword(String password) throws Exception {
        this.password = password;
    }

}
