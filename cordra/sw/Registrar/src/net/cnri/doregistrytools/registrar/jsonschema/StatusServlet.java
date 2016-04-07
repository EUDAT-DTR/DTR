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

import net.cnri.repository.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@WebServlet({"/status/*"})
public class StatusServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(DesignServlet.class);

    private Gson gson;
    private static RegistrarService registrar;
    
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
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            Status status = registrar.getStatus();
            String statusJson = gson.toJson(status);
            PrintWriter w = resp.getWriter();
            w.write(statusJson);
            w.close();
        } catch (RepositoryException e) {
            logger.error("Unexpected error getting design", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
