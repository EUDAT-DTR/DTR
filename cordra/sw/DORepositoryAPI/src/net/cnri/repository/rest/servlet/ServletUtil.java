/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.internal.InternalRepository;
import net.cnri.repository.internal.SimpleAuthenticatedCaller;

public class ServletUtil {
    private static Repository repository = null;
    
    public synchronized static Repository getRepository(ServletContext context, @SuppressWarnings("unused") HttpServletRequest request) throws RepositoryException {
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
      
        if (repository == null) {
            repository = new InternalRepository(serverMain, new SimpleAuthenticatedCaller(serverMain.getServerID())) {};
        }
        return repository;
    }   
    
    public static boolean checkPassword(String userID, String password, ServletContext context) {
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
        String storedPassword = serverMain.getStoredPasswordForUser(userID);
        return password.equals(storedPassword);
    }
}
