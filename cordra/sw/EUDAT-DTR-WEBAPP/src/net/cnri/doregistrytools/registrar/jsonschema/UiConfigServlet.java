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
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.handle.hdllib.Util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebServlet({"/uiConfig/*"})
public class UiConfigServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(UiConfigServlet.class);
                    
    private static RegistrarService registrar;
    
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
        Design design = registrar.getDesign();
        JsonNode uiConfig = design.uiConfig;
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(uiConfig);
        PrintWriter w = resp.getWriter();
        w.write(json);
        w.close();
    }
    
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uiConfigJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        try {
            JsonNode uiConfig = registrar.updateUiConfig(uiConfigJson);
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(uiConfig);
            PrintWriter w = resp.getWriter();
            w.write(json);
            w.close();
        } catch (Exception e) {
            logger.error("Exception in PUT /uiConfig", e);
            ServletErrorUtil.internalServerError(resp);
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
