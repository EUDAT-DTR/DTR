/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.RepositoryJsonSerializerV2;
import net.cnri.util.StringUtils;

public class ObjectRequestHandler extends RequestHandler {

    public ObjectRequestHandler(RequestPath path, HttpServletRequest req, HttpServletResponse res, Repository repository) {
        super(path, req, res, repository);
    }

    @Override
    protected void handleGet() throws RepositoryException, IOException {
        DigitalObject dobj = repository.getDigitalObject(path.getHandle());
        if (dobj == null) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            String json = RepositoryJsonSerializerV2.toJSON(dobj, Collections.<String>emptyList());
            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType(JSON);
            res.setCharacterEncoding("UTF-8");
            Writer w = res.getWriter();
            w.write(json);
        }
    }

    // TODO return object just put ?
    @Override
    protected void handlePut() throws RepositoryException, IOException {
        if (repository.verifyDigitalObject(path.getHandle())) {
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            res.setStatus(HttpServletResponse.SC_CREATED);
            String uri = req.getContextPath() + "/" + StringUtils.encodeURLComponent(path.getHandle());
            res.setHeader("Location",uri);
        }
        String json = streamToString(req.getInputStream(), req.getCharacterEncoding());
        DigitalObject dobj = repository.getOrCreateDigitalObject(path.getHandle());
        if(json!=null && json.length()>0) RepositoryJsonSerializerV2.loadJsonIntoDigitalObject(json, dobj);
    }

    @Override
    protected void handlePost() throws RepositoryException, IOException {
        handlePut();
    }

    @Override
    protected void handleDelete() throws RepositoryException, IOException {
        if (repository.verifyDigitalObject(path.getHandle())) {
            repository.deleteDigitalObject(path.getHandle());
            res.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

}
