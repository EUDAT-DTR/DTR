/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

public class AdminPasswordChecker implements AdminPasswordCheckerInterface {

    private net.cnri.apps.doserver.Main serverMain;
    private String defaultAdminPassword;
    
    public AdminPasswordChecker(net.cnri.apps.doserver.Main serverMain, String defaultAdminPassword) {
        this.defaultAdminPassword = defaultAdminPassword;
        this.serverMain = serverMain;
    }
    
    public boolean check(String password) {
        String adminPassword = null;
        if (serverMain != null) {
            adminPassword = serverMain.getStoredPasswordForUser("admin");
        } else {
            adminPassword = defaultAdminPassword;
        }
        if (adminPassword == null) return false;
        return adminPassword.equals(password);
    }
    
    public void setPassword(String password) throws Exception {
        serverMain.setStoredPasswordForUser("admin", password);
    }
}
