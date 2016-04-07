/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.replication;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.auth.Credentials;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.util.RepositoryJsonSerializer;

import com.google.gson.stream.JsonWriter;

@WebServlet({"/replicate/*"})
public class ReplicateServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ReplicateServlet.class);
                    
    private RegistrarService registrar;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAuthorizedReplicationRequest(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String path = req.getPathInfo();
        if (path != null && path.startsWith("/schemas")) {
            getSchemas(req, resp);
        } else {
            getModifiedObjects(req, resp);
        }
    }

    private void getSchemas(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            JsonWriter jsonWriter = new JsonWriter(resp.getWriter());
            CloseableIterator<DigitalObject> iter = null;
            try {
                iter = registrar.getLocalSchemas();
                jsonWriter.beginArray();
                while (iter.hasNext()) {
                    DigitalObject dobj = iter.next();
                    RepositoryJsonSerializer.writeJson(jsonWriter, dobj);
                }
                jsonWriter.endArray();
            } catch (UncheckedRepositoryException e) {
                throw e.getCause();
            } finally {
                if (iter != null) iter.close();
                jsonWriter.close();
            }
        } catch (Exception e) {
            if (e instanceof NullPointerException || e instanceof NumberFormatException) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                logger.error("Exception replicating", e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            JsonWriter jsonWriter = new JsonWriter(resp.getWriter());
            jsonWriter.beginObject();
            jsonWriter.name("details");
            jsonWriter.value(e.getMessage());
            jsonWriter.endObject();
            jsonWriter.close();
        }
    }

    private void getModifiedObjects(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String sinceTimestamp = req.getParameter("since");
            String type = req.getParameter("type");
            String[] includes = req.getParameterValues("include");
            String[] excludes = req.getParameterValues("exclude");
            long since = Long.parseLong(sinceTimestamp);
            JsonWriter jsonWriter = new JsonWriter(resp.getWriter());
            CloseableIterator<DigitalObject> iter = null;
            try {
                iter = registrar.getModifiedObjectsSince(since, type, includes, excludes);
                jsonWriter.beginArray();
                while (iter.hasNext()) {
                    DigitalObject dobj = iter.next();
                    RepositoryJsonSerializer.writeJson(jsonWriter, dobj);
                }
                jsonWriter.endArray();
            } catch (UncheckedRepositoryException e) {
                throw e.getCause();
            } finally {
                if (iter != null) iter.close();
                jsonWriter.close();
            }
        } catch (Exception e) {
            if (e instanceof NullPointerException || e instanceof NumberFormatException) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                logger.error("Exception replicating", e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            JsonWriter jsonWriter = new JsonWriter(resp.getWriter());
            jsonWriter.beginObject();
            jsonWriter.name("details");
            jsonWriter.value(e.getMessage());
            jsonWriter.endObject();
            jsonWriter.close();
        }
    }
    
    private boolean isAuthorizedReplicationRequest(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null) {
            return false;
        } 
        Credentials c = new Credentials(authHeader);
        List<ReplicationCredentials> replicationCredentials = registrar.getDesign().replicationCredentials;
        for (ReplicationCredentials repCred : replicationCredentials) {
            if (c.getUsername().equals(repCred.username)) {
                if (c.getPassword().equals(repCred.password)) {
                    return true;
                }
            }
        }
        return false;
    }
}
