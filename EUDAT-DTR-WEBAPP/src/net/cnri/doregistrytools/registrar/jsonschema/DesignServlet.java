/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.Gson;

import net.cnri.repository.DataElement;
import net.cnri.repository.RepositoryException;

@WebServlet({"/design/*"})
public class DesignServlet extends HttpServlet {
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
            String path = req.getPathInfo();
            if (path == null || "".equals(path)) {
                String elementName = req.getParameter("elementName");
                if (elementName != null) {
                    DataElement element = registrar.getElementFromDesign(elementName);
                    if (element == null) {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        RegistrarErrorResponse errorResponse = new RegistrarErrorResponse("The element you requested is not on this design.");
                        PrintWriter writer = resp.getWriter();
                        gson.toJson(errorResponse, writer);
                        return;
                    }
                    String mimetype = element.getAttribute("mimetype");
                    String filename = element.getAttribute("filename");
                    if (mimetype == null) {
                        mimetype = "application/octet-stream";
                    }
                    resp.setContentType(mimetype);
                    resp.setHeader("Content-Disposition", ServletUtil.contentDispositionHeaderFor("inline", filename));
                    InputStream in = null;
                    try {
                        in = element.read();
                        OutputStream out = resp.getOutputStream();
                        IOUtils.copy(in, out);
                    } finally {
                        if (in != null) { 
                            try { in.close(); } catch (Exception e) { }
                        }
                    }
                    
                } else {
                    Design design = registrar.getDesign();
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectWriter writer = mapper.writerWithView(Views.Public.class);
                    String json =  writer.writeValueAsString(design);
                    //mapper.wrgetSerializationConfig().setSerializationView(Views.Public.class);
                    //String json = mapper.writeValueAsString(design);
                    PrintWriter w = resp.getWriter();
                    w.write(json);
                    w.close();
                }
            } else {
                String objectType = path;
                String schemaJson = registrar.getLocalSchemaAsJsonString(objectType);
                PrintWriter w = resp.getWriter();
                if (schemaJson != null) {
                    w.write(schemaJson);
                    w.close();
                } else {
                    //write an error response
                }
            }
        } catch (RepositoryException e) {
            logger.error("Unexpected error getting design", e);
            ServletErrorUtil.internalServerError(resp);
        }
    }
}
