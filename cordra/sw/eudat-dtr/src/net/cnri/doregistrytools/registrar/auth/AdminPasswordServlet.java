/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.doregistrytools.registrar.jsonschema.HandleMintingConfig;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.handle.hdllib.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@WebServlet({"/adminPassword/*"})
public class AdminPasswordServlet extends HttpServlet {
    private Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

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

    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String adminPasswordJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        AdminPassword adminPassword = gson.fromJson(adminPasswordJson, AdminPassword.class);
        String password = adminPassword.password;
        if (password == null) {
            ServletErrorUtil.badRequest(resp, "Password missing.");
        } else {
            try {
                registrar.setAdminPassword(password);
                PrintWriter w = resp.getWriter();
                w.write("{\"success\" : true}");
                w.close();
            } catch (Exception e) {
                logger.error("Exception in PUT /adminPassword", e);
                ServletErrorUtil.internalServerError(resp);
            } 
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
    
    public static class AdminPassword {
        public String password;
    }
    
}
