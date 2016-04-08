package net.cnri.doregistrytools.registrar.auth;

public interface AdminPasswordCheckerInterface {
    
    public boolean check(String password);
    
    public void setPassword(String password) throws Exception;
}
