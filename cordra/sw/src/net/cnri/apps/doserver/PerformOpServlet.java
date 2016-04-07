/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet that provides a DO operation invoker in which requests provide the object ID (id query parameter), 
 * operation ID (op query parameter), and a set of paramters (all query parameters starting with "param.").
 * HTTP input and output streams are connected directly to the operation handlers
 */
public class PerformOpServlet extends HttpServlet {
    static final Logger logger = LoggerFactory.getLogger(PerformOpServlet.class);

    public static final int BUFFER_SIZE = 1024;

    private final Main serverMain;
    private final ExecutorService execServ;
    private final HTTPConnectionHandler httpConnectionHandler;
    private static final String OPERATION_ID_PARAM = "op";
    private static final String OBJECT_ID_PARAM = "id";
    
    public PerformOpServlet(Main serverMain, ExecutorService execServ, HTTPConnectionHandler httpConnectionHandler) {
        this.serverMain = serverMain;
        this.execServ = execServ;
        this.httpConnectionHandler = httpConnectionHandler;
    }
    
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.err.println("/performop servlet processing request "+request);
        try {
            performOperationFromHTTPRequest(request, response);
        }
        catch(DOException e) {
            if(e.getErrorCode()==DOException.PERMISSION_DENIED_ERROR) {
                if(!response.isCommitted()) {
                    response.reset();
                    if(request.getAttribute(HTTPConnectionHandler.DO_AUTHENTICATION_REQ_ATTRIBUTE)==null) {
                        response.setHeader("WWW-Authenticate", "Basic realm=\"hdl:" + serverMain.getServerID() + "\"");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    }
                    else {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    }
                }
                else {
                    logger.error("performop server: Permission error after response committed",e);
                }
            }
            else {
                if(!response.isCommitted()) {
                    response.reset();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("text/plain");
                    response.setCharacterEncoding("UTF-8");
                    if(!"HEAD".equals(request.getMethod())) e.printStackTrace(response.getWriter());
                }
                logger.error("performop server error",e);
            }
        }
        catch(Exception e) {
            if(!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.setCharacterEncoding("UTF-8");
                if(!"HEAD".equals(request.getMethod())) e.printStackTrace(response.getWriter());
            }
            logger.error("REST server error",e);
        }
    }
    
    
    private void performOperationFromHTTPRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
        if(auth==null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        String objectID = request.getParameter(OBJECT_ID_PARAM);
        String operationID = request.getParameter(OPERATION_ID_PARAM);
        if(objectID==null || operationID==null) {
            response.reset();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("Request missing operation or target object ID");
            logger.info("object or operation ID missing from perform-operation request");
            return;
        }
        
        Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
        operationID = operationID.trim();
        objectID = objectID.trim();
        
        // extra the operation parameters
        HeaderSet params = new HeaderSet();
        Map<String,String[]> httpParams = request.getParameterMap();
        for(String paramName : httpParams.keySet()) {
            if(paramName.startsWith("param.")) {
                String key = paramName.substring("param.".length());
                String vals[] = httpParams.get(paramName);
                for(String val : vals) {
                    params.addHeader(key, val);
                }
            }
        }
        
        // invoke the operation
        StreamPair io = repo.performOperation(objectID, operationID, params);
        InputStream servIn = null;
        OutputStream servOut = null;
        
        long inputBytes = 0;
        long outputBytes = 0;
        try {
            // pipe any servlet input into the operation
            if(request.getContentLength()>0) {
                servIn = new LimitedInputStream(request.getInputStream(), 0, request.getContentLength());
                OutputStream out = io.getOutputStream();
                byte buf[] = new byte[BUFFER_SIZE];
                int r;
                while((r=servIn.read(buf))>=0) {
                    out.write(buf, 0, r);
                    inputBytes += r;
                }
                out.close();
            } else {
                io.getOutputStream().close();
            }
            
            // pipe any output from the operation back to the client
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/octet-stream");
            
            InputStream in = io.getInputStream();
            servOut = response.getOutputStream();
            byte buf[] = new byte[BUFFER_SIZE];
            int r;
            while((r=in.read(buf))>=0) {
                servOut.write(buf, 0, r);
                outputBytes += r;
            }
            servOut.close();
        } finally {
            try { servOut.close(); } catch (Throwable ignored) {}
            try { servIn.close(); } catch (Throwable ignored) {}
            try { io.close(); } catch (Throwable ignored) {}
        }
    }
    
}
