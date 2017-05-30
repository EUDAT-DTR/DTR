/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StreamUtil;

@WebServlet("/acls/*")
public class AclServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(AclServlet.class);

    private static RegistrarService registrar;
    
    private Gson gson;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        try {
            AccessControlList acl = registrar.getAclFor(objectId);
            AccessControlList.SerializedAcl sAcl = acl.serialize();
            gson.toJson(sAcl, resp.getWriter());
        } catch (NoSuchDigitalObjectException e) {
            ServletErrorUtil.notFound(resp, "No such object " + objectId);
        } catch (RepositoryException e) {
            logger.error("Exception getting acls", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPut(req, resp);
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        try {
            String json = StreamUtil.readFully(req.getReader());
            AccessControlList.SerializedAcl sAcl = gson.fromJson(json, AccessControlList.SerializedAcl.class);
            DigitalObject dobj = registrar.getDigitalObject(objectId);
            AccessControlList acl = new AccessControlList(dobj);
            acl.replaceRead(sAcl.read);
            acl.replaceWrite(sAcl.write);
            gson.toJson(sAcl, resp.getWriter());
        } catch (NoSuchDigitalObjectException e) {
            ServletErrorUtil.notFound(resp, "No such object " + objectId);
        } catch (RepositoryException e) {
            logger.error("Exception getting acls", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
