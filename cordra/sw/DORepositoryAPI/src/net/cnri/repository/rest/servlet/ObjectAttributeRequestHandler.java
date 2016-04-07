/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.IOException;
import java.io.Writer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StringUtils;

public class ObjectAttributeRequestHandler extends RequestHandler {

    public ObjectAttributeRequestHandler(RequestPath path, HttpServletRequest req, HttpServletResponse res, Repository repository) {
        super(path, req, res, repository);
    }

    @Override
    protected void handleGet() throws RepositoryException, IOException {
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            String attributeValue = dobj.getAttribute(path.getAttributeName());
            if (attributeValue != null) {
                res.setStatus(HttpServletResponse.SC_OK);
                res.setContentType(PLAINTEXT);
                res.setCharacterEncoding("UTF-8");
                Writer w = res.getWriter();
                w.write(attributeValue);
            } else {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    @Override
    protected void handlePut() throws RepositoryException, IOException {
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            String attributeValue = streamToString(req.getInputStream(),req.getCharacterEncoding());
            if (dobj.getAttribute(path.getAttributeName()) == null) {
                res.setStatus(HttpServletResponse.SC_CREATED);
                res.setHeader("Location", req.getContextPath() + "/" + StringUtils.encodeURLComponent(path.getHandle()) + "/att/" + StringUtils.encodeURLComponent(path.getAttributeName()));
            } else {
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            dobj.setAttribute(path.getAttributeName(), attributeValue);
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
            if (dobj.getAttribute(path.getAttributeName()) == null) {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                dobj.deleteAttribute(path.getAttributeName());
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        }
    }
}
