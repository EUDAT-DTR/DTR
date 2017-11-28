/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.auth.AclEnforcer.Permission;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.cnri.repository.RepositoryException;

public class RegistrarAuthorizationFilter implements Filter {
    private static Logger logger = LoggerFactory.getLogger(RegistrarAuthorizationFilter.class);

    private RegistrarService registrar;
    private AclEnforcer aclEnforcer;
    private RegistrarAuthenticator authenticator;
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if(req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            doHttpFilter((HttpServletRequest)req, (HttpServletResponse)resp, chain);
        } else {
            chain.doFilter(req, resp);
        }
    }
    
    private void doHttpFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if (isReplicationRequest(req)) {
            chain.doFilter(req, resp);
            return;
        }
        try {
            boolean authenticated = authenticator.authenticate(req, resp);
            if (isAuthorized(req, resp, authenticated)) {
                chain.doFilter(req, resp);
            } else {
                if (authenticated) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }
            }
        } catch (RepositoryException e) {
            logger.error("Unexpected error checking authorization", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
    
    private boolean isAuthorized(HttpServletRequest req, HttpServletResponse resp, boolean authenticated) throws RepositoryException {
        String servletPath = req.getServletPath();
        
        if (req.getMethod().equalsIgnoreCase("POST") && servletPath.equals("/sessions")) {
            return true;
        }
        
        String userId = null;
        HttpSession session = req.getSession(false);
        // authenticated could be false, but there could still be a session, if CSRF failure, or if bad Authorization: header sent in a session.
        // That could even be sent by a third party so we want to ignore it instead of affecting the legitimate user's session in some way.
        // So we require authenticated to be true.
        if (authenticated && session != null) {
            userId = (String) session.getAttribute("userId");
        }
        if (isAdministrativeRequest(req)) {
            return "admin".equals(userId);
        }
        if (servletPath.startsWith("/objects") || servletPath.startsWith("/acls")) {
            String objectId = getObjectId(req);
            if (objectId != null) {
                Permission perm = aclEnforcer.permittedOperations(userId, objectId);
                resp.addHeader("X-Permission", perm.toString());
                Permission requiredPermission;
                if (requestRequiresOnlyReadPermission(req, servletPath)) {
                    requiredPermission = Permission.READ;
                } else {
                    requiredPermission = Permission.WRITE;
                }
                return AclEnforcer.doesPermissionAllowOperation(perm, requiredPermission);
            } else {
                if (isCreate(req, servletPath)) {
                    return isPermittedToCreate(userId, req);
                }
                // anyone can query; queries are filtered 
                return true;
            }
        }
        return true;
    }

    private boolean isPermittedToCreate(String userId, HttpServletRequest req) throws RepositoryException {
        String objectType = req.getParameter("type");
        if (objectType == null) return true;
        return aclEnforcer.isPermittedToCreate(userId, objectType);
    }
    
    private boolean isCreate(HttpServletRequest req, String servletPath) {
        return servletPath.startsWith("/objects") && "POST".equals(req.getMethod());
    }

    private boolean requestRequiresOnlyReadPermission(HttpServletRequest req, String servletPath) {
        return servletPath.startsWith("/objects") && ("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod()) || isGetViaPost(req));
    }
    
    private boolean isGetViaPost(HttpServletRequest req) {
        return "POST".equals(req.getMethod()) && "application/x-www-form-urlencoded".equals(req.getContentType());
    }
    
    private String getObjectId(HttpServletRequest req) {
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty() && !"/".equals(objectId)) {
            objectId = objectId.substring(1);
            return objectId;
        }
        return null;
    }
    
    private boolean isReplicationRequest(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        if (servletPath.startsWith("/replicate")) {
            return true;
        } else {
            return false;
        }
    }
    
    private boolean isAdministrativeRequest(HttpServletRequest req) {
        if (req.getMethod().equalsIgnoreCase("POST") || req.getMethod().equalsIgnoreCase("PUT") || req.getMethod().equalsIgnoreCase("DELETE")) {
            String servletPath = req.getServletPath();
            if (servletPath.startsWith("/uiConfig") ||
                servletPath.startsWith("/remoteRepositories") ||
                servletPath.startsWith("/handleMintingConfig") ||
                servletPath.startsWith("/replicationCredentials") ||
                servletPath.startsWith("/adminPassword") ||
                servletPath.startsWith("/config") ||
                servletPath.startsWith("/generateKeys") ||
                servletPath.startsWith("/updateHandles") ||
                servletPath.startsWith("/schemas")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            registrar = RegistrarServiceFactory.getRegistrarService(filterConfig.getServletContext());
            this.authenticator = registrar.getAuthenticator();
            this.aclEnforcer = registrar.getAclEnforcer();
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    @Override
    public void destroy() {
        
    }
}
