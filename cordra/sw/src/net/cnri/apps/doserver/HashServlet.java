/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.do_api.DataElement;
import net.cnri.do_api.DigitalObject;
import net.cnri.do_api.Repository;
import net.cnri.dobj.*;
import net.handle.hdllib.Util;
import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

/**
 * Created by IntelliJ IDEA.
 * User: sreilly
 * Date: 4/26/11
 * Time: 2:47 PM
 */
public class HashServlet extends HttpServlet {
  static final Logger logger = LoggerFactory.getLogger(HashServlet.class);
    
  public static final int BUFFER_SIZE = 1024;
  public static final String XML = "application/xml";
  private static final String PLAINTEXT = "text/plain";
  private static final String HASH_CONTENT_TYPE = PLAINTEXT;
  
  private final Main serverMain;
  private final ExecutorService execServ;
  private final HTTPConnectionHandler httpConnectionHandler;

  public HashServlet(Main serverMain, ExecutorService execServ, HTTPConnectionHandler httpConnectionHandler) {
    this.serverMain = serverMain;
    this.execServ = execServ;
    this.httpConnectionHandler = httpConnectionHandler;
  }

  private static void appendXMLAttributes(StringBuilder sb, HeaderSet atts) {
    for(Iterator it=atts.iterator(); it.hasNext(); ) {
      HeaderItem attItem = (HeaderItem)it.next();
      sb.append(" <att name=\"").append(xmlAttrEscape(attItem.getName())).append("\" value=\"").append(xmlAttrEscape(attItem.getValue())).append("\" />\n");
    }
  }

  private static String digest(String alg, InputStream in) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance(alg);
      digest.reset();

      byte buf[] = new byte[2048];
      int r;
      while((r=in.read(buf))>=0) digest.update(buf, 0, r);
      return Util.decodeHexString(digest.digest(), false);
    } catch (NoSuchAlgorithmException nsae) {
      throw new IOException("message digest algorithm \""+alg+"\" not available");
    }
  }
  
  private static void writeObjectDigest(HttpServletResponse response, DigitalObject dobj) throws IOException {
    response.setContentType(XML);
    response.setCharacterEncoding("UTF-8");
    StringBuilder sb = new StringBuilder();
    sb.append("<do id=\"").append(xmlAttrEscape(dobj.getObjectID())).append("\" >\n");
    appendXMLAttributes(sb, dobj.getAttributes());
    for(String elementID : dobj.listDataElements()) {
      DataElement el = dobj.getDataElement(elementID);
      sb.append("<doel id=\"").append(xmlAttrEscape(elementID)).append("\"").
        append(" sha1=\"").append(digest("SHA1", el.read())).append("\"").
        append(" md5=\"").append(digest("MD5", el.read())).append("\"").
        append(">\n");
      appendXMLAttributes(sb, el.getAttributes());
      sb.append("</doel>\n");
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(HASH_CONTENT_TYPE);
    response.getWriter().write(sb.toString());
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
    Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
    String objectID = request.getParameter(HTTPConnectionHandler.OBJECT_ID_PARAM);

    logger.debug("finding hash for {}",objectID);
    
    if(objectID==null) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    } else {
      if(!repo.verifyDigitalObject(objectID)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      
      DigitalObject dobj = repo.getDigitalObject(objectID);
      writeObjectDigest(response, dobj);
    }
  }


  static String xmlAttrEscape(String str) {
    if(str==null) return "null";
    StringBuilder sb = new StringBuilder("");
    for(int i=0;i<str.length();i++) {
      char ch = str.charAt(i);
      if(ch=='<')
        sb.append("&lt;");
      else if(ch=='>')
        sb.append("&gt;");
      else if(ch=='"')
        sb.append("&quot;");
      else if(ch=='&')
        sb.append("&amp;");
      else if(ch=='\'')
        sb.append("&#39;");
      else if(ch=='\t')
        sb.append("&#x9;");
      else if(ch=='\n')
        sb.append("&#xA;");
      else if(ch=='\r')
        sb.append("&#xD;");
      else
        sb.append(ch);
    }
    return sb.toString();
  }


}
