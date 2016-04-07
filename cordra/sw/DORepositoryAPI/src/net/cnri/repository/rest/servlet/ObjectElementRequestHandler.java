/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.dobj.DOConstants;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StringUtils;

public class ObjectElementRequestHandler extends RequestHandler {

    public ObjectElementRequestHandler(RequestPath path, HttpServletRequest req, HttpServletResponse res, Repository repository) {
        super(path, req, res, repository);
    }

    @Override
    protected void handleGet() throws RepositoryException, IOException {
        String getSizeString = req.getParameter("getSize");
        boolean getSize = ("true".equals(getSizeString));
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            DataElement dataElement = dobj.getDataElement(path.getElementName());
            if (dataElement == null) {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                res.setStatus(HttpServletResponse.SC_OK);
                if (getSize) {
                    respondWithSize(dataElement);
                } else {
                    respondWithContent(dataElement);
                }
            }
        }
    }

    private void respondWithContent(DataElement dataElement) throws RepositoryException, IOException {
        String mimetype = dataElement.getAttribute(DOConstants.MIME_TYPE_ATTRIBUTE);
        if(mimetype != null) res.setContentType(mimetype);
        String filename = dataElement.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE);
        if (filename == null) {
            filename = path.getElementName();
        }
        res.setHeader("Content-Disposition", "attachment; filename=\"" + escapeFilename(filename) + "\"");
        res.setHeader("Content-Length", String.valueOf(dataElement.getSize()));
        InputStream in = dataElement.read();
        try {
            OutputStream out = res.getOutputStream();
            byte buf[] = new byte[BUFFER_SIZE];
            int r;
            while((r=in.read(buf))>=0) out.write(buf, 0, r);
            out.close();
        } finally {
            in.close();
        }
    }

    private void respondWithSize(DataElement dataElement) throws RepositoryException, IOException {
        long size = dataElement.getSize();
        String sizeString = String.valueOf(size);
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType(PLAINTEXT);
        res.setCharacterEncoding("UTF-8");
        Writer w = res.getWriter();
        w.write(sizeString);
    }

    @Override
    protected void handlePut() throws RepositoryException, IOException {
        String appendString = req.getParameter("append");
        boolean append = ("true".equals(appendString));
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            DataElement dataElement = dobj.getDataElement(path.getElementName());
            if (dataElement == null) {
                dataElement = dobj.createDataElement(path.getElementName());
                res.setStatus(HttpServletResponse.SC_CREATED);
                res.setHeader("Location", req.getContextPath() + "/" + StringUtils.encodeURLComponent(path.getHandle()) + "/el/" + StringUtils.encodeURLComponent(path.getElementName()));
            } 
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            InputStream in = req.getInputStream();
            if(in != null) {
                dataElement.write(in, append);
                String mimetype = req.getContentType();
                if(mimetype != null) dataElement.setAttribute(DOConstants.MIME_TYPE_ATTRIBUTE, mimetype);
            }
        }
    }

    @Override
    protected void handlePost() throws RepositoryException, IOException {
        handlePut();
    }

    @Override
    protected void handleDelete() throws RepositoryException, IOException {
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            DataElement dataElement = dobj.getDataElement(path.getElementName());
            if (dataElement == null) {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                dataElement.delete();
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        }
    }
    
    private static String escapeFilename(String filename) {
        try {
            String res = new String(filename.getBytes("ISO-8859-1"),"ISO-8859-1");
            return res.replace("\n","?").replace("\\","\\\\").replace("\"","\\\"");
        } catch(UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
