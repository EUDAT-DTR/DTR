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

import javax.servlet.ServletContext;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.cnri.util.JsonToStreamObjectConverter;
import net.cnri.util.StreamObject;
import net.cnri.util.StreamObjectToJsonConverter;
import net.cnri.util.StreamTable;
import net.handle.hdllib.Util;

@WebServlet({"/config/*"})
public class ServerConfigServlet extends HttpServlet {

    private static final Gson gson = new Gson();
    
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        ServletContext context = getServletContext();
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
        StreamTable config = serverMain.getConfig();
        JsonElement configJson = StreamObjectToJsonConverter.toJson(config);
        String configJsonString = gson.toJson(configJson);
        PrintWriter w;
        try {
            w = resp.getWriter();
            w.write(configJsonString);
            w.close();
        } catch (IOException e) {
            ServletErrorUtil.internalServerError(resp);
            e.printStackTrace();
        }
    }
    
    @Override 
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String configJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(configJson);
        
        
        StreamObject streamObject = JsonToStreamObjectConverter.toStreamObject(jsonElement);
        if (!streamObject.isStreamTable()) {
            ServletErrorUtil.badRequest(resp, "Config JSON must be an object.");
            return;
        }
        
        StreamTable streamTable = (StreamTable) streamObject;
        ServletContext context = getServletContext();
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
        
        
        PrintWriter w;
        try {
            serverMain.writeNewConfigToFile(streamTable);
            w = resp.getWriter();
            w.write(configJson);
            w.close();
        } catch (IOException e) {
            ServletErrorUtil.internalServerError(resp);
            e.printStackTrace();
        } catch (Exception e) {
            ServletErrorUtil.internalServerError(resp);
            e.printStackTrace();
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
