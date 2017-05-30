/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.cnri.repository.RepositoryException;

import com.google.gson.Gson;

@WebServlet({"/sessions/*"})
public class AuthenticateServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(AuthenticateServlet.class);
                    
    private static RegistrarService registrar;
    private static RegistrarAuthenticator authenticator;
    
    private Gson gson;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
            authenticator = registrar.getAuthenticator();
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        
        IsActiveSessionResponse sessionResponse = null;
        if (session != null) {
            String userId = (String) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");
            sessionResponse = new IsActiveSessionResponse(true, userId, username);
        } else {
            sessionResponse = new IsActiveSessionResponse(false, null, null);
        }
        
        String responseJson = gson.toJson(sessionResponse);
        PrintWriter w = resp.getWriter();
        w.write(responseJson);
        w.close();
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        boolean authenticated;
        try {
            authenticated = authenticator.authenticate(req, resp);
        } catch (RepositoryException e) {
            logger.error("Exception in POST /sessions", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }
        if (authenticated) {
            PrintWriter w = resp.getWriter();
            HttpSession session = req.getSession(false);
            String userId = (String) session.getAttribute("userId");
            String username = (String) session.getAttribute("username");
            String json = gson.toJson(new AuthResponse(true, userId, username));
            w.println(json);
            w.close();
        } else {
            if (req.getParameter("basic") != null) resp.setHeader("WWW-Authenticate", "Basic realm=\"admin\"");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        path = path.substring(1);
        if (!"this".equals(path)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
            PrintWriter w = resp.getWriter();
            String json = gson.toJson(new AuthResponse(true, null, null));
            w.println(json);
            w.close();
        } else {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }
    
    
    
    @SuppressWarnings("unused")
    private static class IsActiveSessionResponse {
        boolean isActiveSession = false;
        String userId;
        String username;
        
        public IsActiveSessionResponse(boolean isActiveSession, String userId, String username) {
            this.isActiveSession = isActiveSession;
            this.userId = userId;
            this.username = username;
        }
    }
    
    @SuppressWarnings("unused")
    private static class AuthResponse {
        boolean success = false;
        String userId;
        String username;
        
        public AuthResponse(boolean success, String userId, String username) {
            this.success = success;
            this.userId = userId;
            this.username = username;
        }
    }
}
