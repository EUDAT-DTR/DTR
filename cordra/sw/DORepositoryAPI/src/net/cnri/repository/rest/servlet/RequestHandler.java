/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.handle.hdllib.Util;

public abstract class RequestHandler {
    
    public static final int BUFFER_SIZE = 1024;
    protected static final String JSON = "application/json";
    protected static final String PLAINTEXT = "text/plain";
    protected String requestMethod;
    protected RequestPath path;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    protected Repository repository;
    
    public RequestHandler(RequestPath path, HttpServletRequest req, HttpServletResponse res, Repository repository) {
        this.requestMethod = req.getMethod();
        this.path = path;
        this.req = req;
        this.res = res;
        this.repository = repository;
    }
    
    public void handleRequest()  throws RepositoryException, IOException {
        if ("GET".equals(requestMethod)) {
            handleGet();
        } else if ("PUT".equals(requestMethod)) {
            handlePut();
        } else if ("POST".equals(requestMethod)) {
            handlePost();
        } else if ("DELETE".equals(requestMethod)) {
            handleDelete();
        }
    }
    
    protected abstract void handleGet() throws RepositoryException, IOException;
    protected abstract void handlePut() throws RepositoryException, IOException;
    protected abstract void handlePost() throws RepositoryException, IOException;
    protected abstract void handleDelete() throws RepositoryException, IOException;
    
    protected static String streamToString(InputStream input, String encoding) throws IOException{
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[BUFFER_SIZE];
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
