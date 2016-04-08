/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.PrintWriter;
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

import com.google.gson.Gson;

@WebServlet({"/versions/*"})
public class VersionsServlet extends HttpServlet {

    private RegistrarService registrar;
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
    
    /**
     * Lists all versions of the specified object
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getParameter("objectId");
        HttpSession session = req.getSession(false);
        String userId = null;
        if (session != null) {
            userId = (String) session.getAttribute("userId"); 
        }
        try {
            List<DigitalObject> versions = registrar.getVersionsFor(objectId, userId);
            List<VersionInfo> versionInfos = getVersionInfoListFor(versions);
            String json = gson.toJson(versionInfos);
            PrintWriter w = resp.getWriter();
            w.write(json);
            w.close();
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            e.printStackTrace();
        }
    }
    
    private List<VersionInfo> getVersionInfoListFor(List<DigitalObject> versions) throws RepositoryException {
        List<VersionInfo> result = new ArrayList<VersionInfo>();
        for (DigitalObject version : versions) {
            VersionInfo versionInfo = getVersionInfoFor(version);
            result.add(versionInfo);
        }
        return result;
    }
    
    private VersionInfo getVersionInfoFor(DigitalObject dobj) throws RepositoryException {
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.id = dobj.getHandle();
        versionInfo.versionOf = dobj.getAttribute(VersionManager.VERSION_OF);
        versionInfo.type = dobj.getAttribute("type");
        if (versionInfo.versionOf == null) {
            versionInfo.isTip = true;
            versionInfo.modifiedOn = Long.valueOf(dobj.getAttribute("internal.modified"));
        } else {
            versionInfo.publishedBy = dobj.getAttribute(VersionManager.PUBLISHED_BY);
            versionInfo.publishedOn = Long.valueOf(dobj.getAttribute(VersionManager.PUBLISHED_ON));
        }
        return versionInfo;
    }
    
    /**
     * Creates a new locked copy of the specified object and returns the new Id.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getParameter("objectId");
        HttpSession session = req.getSession(false);
        String userId = null;
        if (session != null) {
            userId = (String) session.getAttribute("userId"); 
        }
        if (objectId != null) {
            try {
                DigitalObject versionObject = registrar.publishVersion(objectId, userId);
                VersionInfo versionInfo = getVersionInfoFor(versionObject);
                String json = gson.toJson(versionInfo);
                PrintWriter w = resp.getWriter();
                w.write(json);
                w.close();
            } catch (RepositoryException e) {
                ServletErrorUtil.internalServerError(resp);
                e.printStackTrace();
            } catch (VersionException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
