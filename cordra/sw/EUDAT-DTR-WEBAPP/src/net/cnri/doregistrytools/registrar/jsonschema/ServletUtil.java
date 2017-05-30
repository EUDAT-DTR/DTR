/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import net.cnri.apps.doserver.Main;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.internal.InternalRepository;
import net.cnri.repository.internal.SimpleAuthenticatedCaller;
import net.cnri.util.StringUtils;
import net.handle.hdllib.AuthenticationInfo;

import javax.servlet.ServletContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class ServletUtil {

    static Repository getRepository(HttpServlet servlet) throws RepositoryException {
        return getRepository(servlet.getServletContext());
    }

    public static synchronized Repository getRepository(ServletContext ctx) throws RepositoryException {
        Repository repo = (Repository) ctx.getAttribute(Repository.class.getName());
        if (repo != null) return repo;
        Main serverMain = (Main) ctx.getAttribute("net.cnri.apps.doserver.Main");
        repo = new InternalRepository(serverMain, new SimpleAuthenticatedCaller(serverMain.getServerID()));
        ctx.setAttribute(Repository.class.getName(), repo);
        return repo;
    }

    public static AuthenticationInfo getHandleAuth(ServletContext ctx) {
        Main serverMain = (Main) ctx.getAttribute("net.cnri.apps.doserver.Main");
        if (serverMain == null) return null;
        return serverMain.getAuth().toHandleAuth();
    }
    
    static void setNoCaching(ServletResponse servletResponse) {
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setDateHeader("Expires", 0);
    }

    static File getBaseDir(ServletContext ctx) {
        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) ctx.getAttribute("net.cnri.apps.doserver.Main");
        if (serverMain == null) return null;
        File baseDir = serverMain.getBaseFolder();
        return baseDir;
    }
    
    static String getServerPrefix(ServletContext ctx) {
        Main serverMain = (Main) ctx.getAttribute("net.cnri.apps.doserver.Main");
        if (serverMain != null) {
            return serverMain.getServerID().substring(0, serverMain.getServerID().indexOf("/"));
        } else {
            return null;
        }
    }
    
    public static String contentDispositionHeaderFor(String disposition, String filename) {
        if (filename == null) return disposition;
        String latin1Version;
        try {
            latin1Version = new String(filename.getBytes("ISO-8859-1"), "ISO-8859-1");
        } catch(UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        String latin1VersionEscaped = latin1Version.replace("\n","?").replace("\\","\\\\").replace("\"","\\\"");
        if (filename.equals(latin1Version)) {
            return disposition + ";filename=\"" + latin1VersionEscaped + "\"";
        } else {
            return disposition + ";filename=\"" + latin1VersionEscaped + "\";filename*=UTF-8''" + StringUtils.encodeURLComponent(filename);
        }
    }

    public static boolean getBooleanParameter(HttpServletRequest req, String param) {
        String value = req.getParameter(param);
        if (value == null) return false;
        if (value.isEmpty()) return true;
        return Boolean.parseBoolean(value);
    }
}
