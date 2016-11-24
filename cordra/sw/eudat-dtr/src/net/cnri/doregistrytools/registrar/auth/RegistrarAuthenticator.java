/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.networked.NetworkedRepository;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.RawQuery;
import net.cnri.util.FastDateFormat;
import net.cnri.doregistrytools.registrar.auth.HashAndSalt;
import net.cnri.doregistrytools.registrar.doip.RegistrarDoipOperations;

import javax.servlet.ServletException;
import net.cnri.doregistrytools.registrar.jsonschema.InvalidException;
import net.cnri.doregistrytools.registrar.jsonschema.Payload;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import org.json.JSONObject;
import net.cnri.repository.CreationException;
import net.cnri.repository.InternalException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrarAuthenticator {
    private static Logger logger = LoggerFactory.getLogger(RegistrarAuthenticator.class);
    private AdminPasswordCheckerInterface adminPasswordChecker = null;
    private SecureRandom random = null;
    private Repository repo;

    /**
     * For now just an admin password. Will add in users stored in the repo later. 
     */
    public RegistrarAuthenticator(AdminPasswordCheckerInterface adminPasswordChecker, Repository repo) {
        this.adminPasswordChecker = adminPasswordChecker;
        this.random = new SecureRandom();
        this.repo = repo;
    }

    // return true if the user is authenticated remotely; in which case, session is populated
    public boolean authenticateRemote(String username, HttpServletRequest req, HttpServletResponse resp) throws RepositoryException, IOException {

        // if user authentication is done remotely, we don't need to verify
        // any password, since there isn't one. What we do need to do, is 
        // create a corresponding user object for the remote user the first time
        // that the end user logs in
        DigitalObject user = getRemoteUserObject(username);

        if(user == null) {
            HttpSession session = req.getSession(true);

            try {
                user = createRemoteUserObject(username, session);
            } catch(CreationException e){
                if(e.getMessage() != null){
                    ServletErrorUtil.badRequest(resp, e.getMessage());
                    logger.error("CreationException in authenticateRemote", e);
                }
                return false;
            } catch (InternalException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("InternalException in authenticateRemote", e);
                return false;
            } catch (InvalidException invalidException) {
                ServletErrorUtil.badRequest(resp, invalidException.getMessage());
                logger.error("InvalidException in authenticateRemote", invalidException);
                return false;
            } catch (Exception e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doPost", e);
                return false;
            }
        }

        String userId = user.getHandle();
        setUpSessionForNewAuthentication(req, resp, username, userId);

        return true;
    }

    // return true if the user is authenticated; in which case, session is populated
    public boolean authenticate(HttpServletRequest req, HttpServletResponse resp) throws RepositoryException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null) {
            if (isCsrfFailure(req)) return false;
            HttpSession session = req.getSession(false);
            if (session == null) return false;
            return session.getAttribute("userId") != null;
        }
        authHeader = authHeader.trim();
        if (authHeader.toLowerCase(Locale.ENGLISH).startsWith("doip")) {
            return authenticateDoip(authHeader, req, resp);
        }
        
        // They have provided an Authorization header so we ignore any session
        Credentials c = new Credentials(authHeader);
        String username = c.getUsername();
        String password = c.getPassword();
        if ("admin".equals(username) && adminPasswordChecker.check(password)) {
            String userId = "admin";
            setUpSessionForNewAuthentication(req, resp, username, userId);
            return true;
        } else {
            DigitalObject user = getUserObject(username);
            if (user == null) {
//                System.out.println(FastDateFormat.getUtcFormat().formatNow() + " User is null for " + username);
                return false;
            } else {
                String hash = user.getAttribute("hash");
                String salt = user.getAttribute("salt");
                HashAndSalt hashAndSalt = new HashAndSalt(hash, salt);
                boolean isPasswordCorrect = hashAndSalt.verifyPassword(password);
                if (isPasswordCorrect) {
                    String userId = user.getHandle();
                    setUpSessionForNewAuthentication(req, resp, username, userId);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }
    
    private boolean authenticateDoip(String authHeader, HttpServletRequest req, HttpServletResponse resp) throws RepositoryException {
        try {
            // only over loopback
            if (!InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
                return false;
            }
            String key = authHeader.substring(5).trim();
            String userId = RegistrarDoipOperations.getAuthenticatedUser(key);
            if (userId == null) return false;
            if (repo.getHandle().equalsIgnoreCase(userId)) {
                setUpSessionForNewAuthentication(req, resp, "admin", "admin");
                return true;
            }
            DigitalObject user = repo.getDigitalObject(userId);
            if (user == null) {
                return false;
            } else {
                String username = user.getAttribute("username");
                if (username == null) return false;
                setUpSessionForNewAuthentication(req, resp, username, userId);
                return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // when true, don't trust info from the session object
    private boolean isCsrfFailure(HttpServletRequest req) {
        String csrfTokenFromRequest = getCsrfTokenFromRequest(req);
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        String csrfTokenFromSession = (String) session.getAttribute("csrfToken");
        if (csrfTokenFromRequest == null) {
            return true;
        }
        if (!csrfTokenFromRequest.equals(csrfTokenFromSession)) {
            return true;
        } 
        return false;
    }

    private String getCsrfTokenFromRequest(HttpServletRequest req) {
        String res = req.getHeader("X-Csrf-Token");
        if (res != null) return res;
        if ("POST".equals(req.getMethod()) && "application/x-www-form-urlencoded".equals(req.getContentType())) {
            return req.getParameter("csrfToken");
        } else {
            return null;
        }
    }
    
    private void setUpSessionForNewAuthentication(HttpServletRequest req, HttpServletResponse resp, String username, String userId) {
        HttpSession session = req.getSession(true);
        String csrfToken = (String) session.getAttribute("csrfToken");
        if (csrfToken == null) {
            csrfToken = generateSecureToken();
            session.setAttribute("csrfToken", csrfToken);
            Cookie csrfTokenCookie = new Cookie("Csrf-token", csrfToken);
            csrfTokenCookie.setPath("/");
            resp.addCookie(csrfTokenCookie);
        }
        session.setAttribute("username", username);
        session.setAttribute("userId", userId);
    }

    private DigitalObject createRemoteUserObject(String username, HttpSession session) throws Exception {

        DigitalObject dobj;

        try {
            RegistrarService registrar = RegistrarServiceFactory.getRegistrarService(
                    session.getServletContext());

            String objectType = "RemoteUser";

            String jsonData = new JSONObject()
                .put("identifier", "")
                .put("username", username)
                .put("authenticator", "B2ACCESS")
                .toString();

            List<Payload> payloads = new ArrayList<Payload>();
            dobj = registrar.writeJsonAndPayloadsIntoDigitalObjectIfValid(objectType, jsonData, payloads, /* handle = */ null, /* creatorId = */ null);
        } catch(Exception e){
            throw e;
        }

        return dobj;
    }
    
    private DigitalObject getUserObject(String username) throws RepositoryException {
        DigitalObject user = null;
        Query q = new RawQuery("username:\"" + username + "\"");
        ensureIndexUpToDate();
        CloseableIterator<DigitalObject> results = repo.search(q);
        try {
            if (results.hasNext()) {
                user = results.next();
            }
        } catch (UncheckedRepositoryException e) {
            e.throwCause();
        } finally {
            results.close();
        }
        return user;
    }

    private DigitalObject getRemoteUserObject(String username) throws RepositoryException {
        DigitalObject user = null;
        Query q = new RawQuery("username:\"" + username + "\"");
        ensureIndexUpToDate();
        CloseableIterator<DigitalObject> results = repo.search(q);
        try {
            if (results.hasNext()) {
                user = results.next();

                String type = user.getAttribute("type");

                // make sure that the object found is of the expected Type
                if (!"RemoteUser".equals(type)) {
                    user = null;
                }
            }
        } catch (UncheckedRepositoryException e) {
            e.throwCause();
        } finally {
            results.close();
        }
        return user;
    }

    
    public void ensureIndexUpToDate() throws RepositoryException {
        if (repo instanceof NetworkedRepository) {
            ((NetworkedRepository) repo).ensureIndexUpToDate();
        }
    }
    
    private String generateSecureToken() {
        return new BigInteger(130, random).toString(32);
    }
    
    public void setAdminPassword(String password) throws Exception {
        adminPasswordChecker.setPassword(password);
    }
}
