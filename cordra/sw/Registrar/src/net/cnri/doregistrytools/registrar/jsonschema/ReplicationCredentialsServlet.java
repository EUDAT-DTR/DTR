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
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.replication.ReplicationCredentials;
import net.handle.hdllib.Util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@WebServlet({"/replicationCredentials/*"})
public class ReplicationCredentialsServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ReplicationCredentialsServlet.class);
                    
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
    
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String credentialsUpdateJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        try {
            Type type = new TypeToken<Collection<ReplicationCredentials>>(){}.getType();
            List<ReplicationCredentials> credentialsUpdate = gson.fromJson(credentialsUpdateJson, type);
            registrar.updateReplicationCredentials(credentialsUpdate);
            UpdateResponse success = new UpdateResponse(true);
            PrintWriter writer = resp.getWriter();
            gson.toJson(success, writer);
            writer.close();
        } catch (Exception e) {
            logger.error("Exception in PUT /authConfig", e);
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
    
    private static class UpdateResponse {
        @SuppressWarnings("unused")
        boolean success = false;
        
        public UpdateResponse(boolean success) {
            this.success = success;
        }
    }
}
