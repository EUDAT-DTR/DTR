/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

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

import com.google.gson.Gson;

@WebServlet({"/relationships/*"})
public class RelationshipsServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(DesignServlet.class);

    private static RelationshipsService relationshipsService;
    
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            relationshipsService = RelationshipsServiceFactory.getRelationshipsService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String path = req.getPathInfo();
            if (path == null || "".equals(path)) {
                ServletErrorUtil.badRequest(resp, "Missing objectId");
            } else {
                String objectId = path.substring(1);
                String outboundOnlyParam = req.getParameter("outboundOnly");
                boolean outboundOnly = false;
                if ("true".equals(outboundOnlyParam)) {
                    outboundOnly = true;
                }
                String userId = null;
                HttpSession session = req.getSession(false);
                if (session != null) {
                    userId = (String) session.getAttribute("userId");
                }
                Relationships relationships = relationshipsService.getRelationshipsFor(objectId, outboundOnly, userId);
//                Gson gson = GsonUtility.getGson();
                PrintWriter writer = resp.getWriter();
                gson.toJson(relationships, writer);
            }
        } catch (Exception e) {
            logger.error("Unexpected error getting schemas", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
