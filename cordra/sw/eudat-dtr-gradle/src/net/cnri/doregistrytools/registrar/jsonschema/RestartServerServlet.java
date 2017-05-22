/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

@WebServlet({"/restart/*"})
public class RestartServerServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletContext context = getServletContext();
        final net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
        Object restartingResponse = new Object();
        Gson gson = new Gson();
        String json = gson.toJson(restartingResponse);
        Writer w = response.getWriter();
        w.write(json);
        w.flush();
        w.close();

        if (!serverMain.isRestarting()) {
            new Thread() {
                public void run() {

                    try {
                        serverMain.shutdownAndRestart();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletContext context = getServletContext();
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
        RestartingResponse restartingResponse = new RestartingResponse(serverMain.isRestarting());
        Gson gson = new Gson();
        String json = gson.toJson(restartingResponse);
        Writer w = response.getWriter();
        w.write(json);
        w.flush();
        w.close();
    }
    
    public class RestartingResponse {
        public boolean restarting = false;
        
        public RestartingResponse(boolean restarting) {
            this.restarting = restarting;
        }
    }
}
