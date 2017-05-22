/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.handle.hdllib.Util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

@WebServlet({"/schematemplates/*"})
public class SchemaTemplatesServlet extends HttpServlet {
    
    private Gson gson;
    Map<String, JsonElement> templates;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            templates = new HashMap<String, JsonElement>();
            JsonParser parser = new JsonParser();
            ServletContext context = getServletContext();
            Set<String> schemaTemplateFiles = context.getResourcePaths("/schematemplates");
            for (String schemaTemplateFile : schemaTemplateFiles) {
                InputStream is = context.getResourceAsStream(schemaTemplateFile);
                String schemaTemplate = streamToString(is, "UTF-8");
                is.close();
                JsonElement template = parser.parse(schemaTemplate);
                String name = nameFromPath(schemaTemplateFile);
                templates.put(name, template);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    private String nameFromPath(String path) {
        File f = new File(path);
        String name = f.getName();
        int lastDot = name.lastIndexOf(".");
        String result = name.substring(0, lastDot);
        return result;
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
    
    static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
    
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String json = gson.toJson(templates);
        PrintWriter w = resp.getWriter();
        w.write(json);
        w.close();
    }
}
