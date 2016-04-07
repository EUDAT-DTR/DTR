/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StringUtils;

@SuppressWarnings("serial")
public class RouterServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = pathOfReq(req);
//        System.out.println("Recieved request: " + path);
        try {
            Repository repository = ServletUtil.getRepository(getServletContext(), req);
            RequestPath requestPath = new RequestPath(path);
            RequestPath.Type type = requestPath.getType();
            RequestHandler requestHandler = null;
            if (type == RequestPath.Type.QUERY) {
                requestHandler = new QueryRequestHandler(requestPath, req, res, repository);
            } else if (type == RequestPath.Type.OBJECT) {
                requestHandler = new ObjectRequestHandler(requestPath, req, res, repository);
            } else if (type == RequestPath.Type.OBJECT_ATTRIBUTE) {
                requestHandler = new ObjectAttributeRequestHandler(requestPath, req, res, repository);
            } else if (type == RequestPath.Type.OBJECT_ELEMENT) {
                requestHandler = new ObjectElementRequestHandler(requestPath, req, res, repository);
            } else if (type == RequestPath.Type.OBJECT_ELEMENT_ATTRIBUTE) {
                requestHandler = new ObjectElementAttributeRequestHandler(requestPath, req, res, repository);
            } else {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            requestHandler.handleRequest();
        } catch (RepositoryException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    static String pathOfReq(HttpServletRequest req) {
        String requestURI = req.getRequestURI(); 
        String contextPath = req.getContextPath();
        return pathOfReq(requestURI,contextPath);
    }
    
    static String pathOfReq(String requestURI, String contextPath) {
        if(requestURI.startsWith(contextPath)) return requestURI.substring(contextPath.length());
        contextPath = urlDecode(contextPath);
        int lastIndex = -1;
        while(true) {
            int slashIndex = requestURI.indexOf('/',lastIndex+1);
            int twoEffIndex = requestURI.indexOf("%2F",lastIndex+1);
            int nextIndex = slashIndex;
            if(0 <= twoEffIndex && twoEffIndex < slashIndex) nextIndex = twoEffIndex;
            if(nextIndex < 0) return "";
            if(contextPath.length() == urlDecode(requestURI.substring(0,nextIndex)).length()) return requestURI.substring(nextIndex);
            lastIndex = nextIndex;
        }
    }
    
    static String urlDecode(String s) {
        return StringUtils.decodeURLIgnorePlus(s);
    }
}
