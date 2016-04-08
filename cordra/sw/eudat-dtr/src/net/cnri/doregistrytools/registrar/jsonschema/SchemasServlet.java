/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.handle.hdllib.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

@WebServlet({"/schemas/*"})
public class SchemasServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(SchemasServlet.class);
    
    private static RegistrarService registrar;
    private static Gson gson = new Gson();
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String pathInfo = req.getPathInfo();
        String type = getTypeFromPathInfo(pathInfo);
        if (type == null || type.isEmpty()) {
            String schemasJson;
            try {
                schemasJson = registrar.getAllLocalSchemasAsJsonString();
                resp.getWriter().println(schemasJson);
            } catch (RepositoryException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Erroring getting local schemas", e);
            }
        } else {
            try {
                String schemaJson = registrar.getLocalSchemaAsJsonString(type);
                if (schemaJson == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                resp.getWriter().println(schemaJson);
            } catch (RepositoryException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Erroring getting local schemas", e);
            }
        }
    }
    
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session == null) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error updating schema, no session object");
            return;
        } 
        String userId = (String) session.getAttribute("userId");
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String pathInfo = req.getPathInfo();
        String type = getTypeFromPathInfo(pathInfo);
        
        if ("Schema".equals(type)) {
            ServletErrorUtil.badRequest(resp, "You may not change the Schema schema.");
            return;
        }
        
        String schemaJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        try {
            String objectId = registrar.idFromType(type);
            DigitalObject dobj = null;
            List<Payload> payloads = new ArrayList<Payload>();
            SchemaInstance schemaInstance = new SchemaInstance();
            schemaInstance.identifier = "";
            schemaInstance.name = type;
            schemaInstance.schema = new JsonParser().parse(schemaJson);
            String json = gson.toJson(schemaInstance);
            if (objectId == null) {
                dobj = registrar.writeJsonAndPayloadsIntoDigitalObjectIfValid("Schema", json, payloads, null, userId);
            } else {
                dobj = registrar.writeJsonAndPayloadsIntoDigitalObjectIfValidAsUpdate(objectId, json, payloads, userId, null);
            }
            resp.getWriter().println("{\"msg\": \"success\"}");
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error updating schema", e);
        } catch (InvalidException e) {
            ServletErrorUtil.badRequest(resp, "InvalidException");
            logger.info("Error updating schema", e);
        } 
    }
    
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        String type = getTypeFromPathInfo(pathInfo);
        
        if ("Schema".equals(type)) {
            ServletErrorUtil.badRequest(resp, "You may not delete the Schema schema. It would be bad.");
            return;
        }
        try {
            String id = registrar.idFromType(type);
            if (id != null) {
                registrar.delete(id);
                resp.getWriter().println("{\"msg\": \"success\"}");
            } else {
                ServletErrorUtil.badRequest(resp, "Schema " + type + " does not exist.");
            }
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Error deleting schema", e);
        } 
    }
    
    private String getTypeFromPathInfo(String pathInfo) {
        if (pathInfo == null) return null;
        String[] tokens = pathInfo.split("/");
        if (tokens.length > 0) {
            return tokens[tokens.length-1];
        } else {
            return null;
        }
    }
    
    protected static String streamToString(InputStream input, String encoding) throws IOException{
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[4096];
        int r;
        while((r = input.read(buf)) >= 0) {
            bout.write(buf, 0, r);
        }
        if(encoding == null) return Util.decodeString(bout.toByteArray());
        else {
            return new String(bout.toByteArray(), encoding);
        }
    }

}
