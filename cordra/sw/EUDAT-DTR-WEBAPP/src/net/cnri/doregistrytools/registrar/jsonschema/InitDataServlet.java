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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@WebServlet({"/initData/*"})
public class InitDataServlet extends HttpServlet {
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
        HttpSession session = req.getSession(false);
        
        InitDataResponse initDataResponse = new InitDataResponse();
        if (session != null) {
            initDataResponse.isActiveSession = true;
            String username = (String) session.getAttribute("username");
            initDataResponse.username = username;
            initDataResponse.userId = (String) session.getAttribute("userId");
        }
        Design design = registrar.getDesign();
        initDataResponse.design = design;
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = null;
        if ("admin".equals(initDataResponse.username)) {
            writer = mapper.writerWithView(Views.Internal.class);
        } else {
            writer = mapper.writerWithView(Views.Public.class);
        }
        String json =  writer.writeValueAsString(initDataResponse);
        PrintWriter w = resp.getWriter();
        w.write(json);
        w.close();
    }
    
    public static class InitDataResponse {
        public Design design;
        public boolean isActiveSession = false;
        public String username;
        public String userId;
    }
    
}
