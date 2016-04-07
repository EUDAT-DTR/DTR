/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;
import net.cnri.apps.doserver.web.VelocityUtil;
import net.cnri.do_api.*;
import net.cnri.dobj.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.util.Streams;

import javax.servlet.http.*;
import javax.servlet.*;

public class BuilderServlet extends HttpServlet {
  public static final int BUFFER_SIZE = 1024;
  public static final String XML = "application/xml";
  private static final String PLAINTEXT = "text/plain";

  private final Main serverMain;
  private final ExecutorService execServ;
  private final HTTPConnectionHandler httpConnectionHandler;
  private final File tmpFolder;
  
  public BuilderServlet(Main serverMain, ExecutorService execServ, HTTPConnectionHandler httpConnectionHandler) {
    this.serverMain = serverMain;
    this.execServ = execServ;
    this.httpConnectionHandler = httpConnectionHandler;
    this.tmpFolder = new File(serverMain.getBaseFolder(), "temp");
    this.tmpFolder.mkdirs();
  }

  /**
   * Set the attributes, element attributes and element data using an HTTP POST
   * Use the /set/ path with a POST request to invoke this servlet.
   * An example html form for setting (and getting via velocity fields) attributes and elements is below:
   *
   * <form method="POST" action="/set/" enctype="multipart/form-data">
   *   <input type="hidden" name="template" value="template_to_show_next.html" />
   *   Object ID: <input type="text" name="id" value="$object.id" /> (set type="hidden" for most forms)
   *   attribute1: <input type="text" size="40" name="att.attribute1" value="$!object.get("att:attribute1")" />
   *   attribute2: <input type="text" size="40" name="att.attribute2" value="$!object.get("att:attribute2")" />
   *   attribute3: <input type="text" size="40" name="att.attribute3" value="$!object.get("att:attribute3")" />
   *   element att1: <input type="text" size="40" name="elatt.upload_element$mimetype" value="image/png" />
   *   upload_element: <input type="file" name="upload_element" size="40" />
   * </form>
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if(request.getQueryString()!=null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if(request.getHeader("Content-Encoding")!=null || request.getHeader("Content-Range")!=null) {
      response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
      return;
    }
    
    if(!request.getMethod().equals("POST")) { // should never be called from within doPost()... duh
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      response.setHeader("Allow","POST");
      return;
    }
    
    String requestURI = request.getRequestURI();
    DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
    if(auth==null) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
    }
    Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());

    if(requestURI.startsWith("/set/")) requestURI = requestURI.substring("/set/".length());

    String objectID = requestURI.trim();
    if(objectID.length()<=0) {
      objectID = null;
    }
    
    String redirect = null;
    String template = null;
    
    if(ServletFileUpload.isMultipartContent(request)) {
      // Process a multipart (ie upload) request
      ServletFileUpload upload = new ServletFileUpload();
      HashMap<String, File> elementFiles = new HashMap<String,File>();
      try {
        FileItemIterator iter = upload.getItemIterator(request);
        HeaderSet atts = new HeaderSet();
        HeaderSet extras = new HeaderSet();
        HashMap<String, HeaderSet> elAtts = new HashMap<String,HeaderSet>();
        HashMap<String, String> elementStrings = new HashMap<String,String>();
        
        while (iter.hasNext()) {
          FileItemStream item = iter.next();
          String name = item.getFieldName();
          InputStream stream = item.openStream();
          if(item.isFormField()) {
            // set attributes
            if(name.startsWith("att.")) {
              atts.addHeader(name.substring("att.".length()), Streams.asString(stream));
            } else if(name.startsWith("elatt.")) {
              String elementID = name.substring("elatt.".length());
              int dollarIdx = elementID.indexOf('$');
              if(dollarIdx>=0) {
                name = elementID.substring(dollarIdx+1);
                elementID = elementID.substring(0, dollarIdx);
                if(!elAtts.containsKey(elementID)) elAtts.put(elementID, new HeaderSet());
                elAtts.get(elementID).addHeader(name, Streams.asString(stream));
              } else {
                // invalid format for setting element attribute
              }
            } else if(name.startsWith("eldata.")) {
              String elementID = name.substring("eldata.".length());
              elementStrings.put(elementID, Streams.asString(stream));
            } else {
              // ignoring unrecognized field
              extras.addHeader(name, Streams.asString(stream));
            }
          } else {
            // Process a file upload into an element
            String elementID = name;
            File tmpFile = File.createTempFile("el_", "", tmpFolder);
            FileOutputStream tmpOut = new FileOutputStream(tmpFile);
            net.cnri.util.IOForwarder.forwardStream(stream, tmpOut);
            tmpOut.flush();
            tmpOut.close();
            //tmpOut.getFD().sync();
            
            if(!elAtts.containsKey(elementID)) elAtts.put(elementID, new HeaderSet());
            elAtts.get(elementID).addHeader(DOConstants.FILE_NAME_ATTRIBUTE, item.getName());
            elAtts.get(elementID).addHeader(DOConstants.MIME_TYPE_ATTRIBUTE, item.getContentType());

            elementFiles.put(elementID, tmpFile);
          }
        }

        objectID = extras.getStringHeader(HTTPConnectionHandler.OBJECT_ID_PARAM, objectID);
        if(objectID!=null) {
          objectID = objectID.trim();
          if(objectID.length()<=0) objectID = null;
        }

        DigitalObject obj = null;
        if(objectID!=null && repo.verifyDigitalObject(objectID)) {
          obj = repo.getDigitalObject(objectID);
        } else {
          obj = repo.createDigitalObject(objectID);
        }
        objectID = obj.getObjectID();

        
        
        // write the queued up elements...
        for(String elementID : elementStrings.keySet()) {
          obj.getDataElement(elementID).write(new ByteArrayInputStream(elementStrings.get(elementID).getBytes("UTF8")));
        }
        for(String elementID : elementFiles.keySet()) {
          obj.getDataElement(elementID).write(new FileInputStream(elementFiles.get(elementID)));
        }
        
        // set the queued up object attributes...
        if(atts.size()>0) obj.setAttributes(atts);

        // set the queued up element attributes...
        for(String elementID : elAtts.keySet()) {
          obj.getDataElement(elementID).setAttributes(elAtts.get(elementID));
        }
        
        redirect = extras.getStringHeader("redirect", null);
        template = extras.getStringHeader("template", null);
      } catch (FileUploadException e) {
        throw new IOException("Error handling file upload: "+e);
      } finally {
        for(String elementID : elementFiles.keySet()) {
          try {
            File f = elementFiles.get(elementID);
            if(f.exists()) f.delete();
          } catch (Throwable t) {}
        }
      }
      
    } else {
      redirect = request.getParameter("redirect");
      template = request.getParameter("template");
      
      // this is a normal POST form
      HeaderSet atts = new HeaderSet();
      HeaderSet extras = new HeaderSet();
      HashMap<String, HeaderSet> elAtts = new HashMap<String,HeaderSet>();
      HashMap<String, String> elementStrings = new HashMap<String,String>();
      for(Enumeration en = request.getParameterNames(); en.hasMoreElements(); ) {
        String name = (String)en.nextElement();
        String values[] = request.getParameterValues(name);
        StringBuilder sb = new StringBuilder();
        for(String val : values) {
          if(sb.length()!=0) sb.append(' ');
          sb.append(val);
        }
        String value = sb.toString();
        if(name.startsWith("att.")) {
          atts.addHeader(name.substring("att.".length()), value);
        } else if(name.startsWith("elatt.")) {
          String elementID = name.substring("elatt.".length());
          int dollarIdx = elementID.indexOf('$');
          if(dollarIdx>=0) {
            name = elementID.substring(dollarIdx+1);
            elementID = elementID.substring(0, dollarIdx);
            if(!elAtts.containsKey(elementID)) elAtts.put(elementID, new HeaderSet());
            elAtts.get(elementID).addHeader(name, value);
          } else {
            // invalid format for setting element attribute
          }
        } else if(name.startsWith("eldata.")) {
          String elementID = name.substring("eldata.".length());
          elementStrings.put(elementID, value);
        } else {
          // ignoring unrecognized field
          extras.addHeader(name, value);
        }
      }

      objectID = extras.getStringHeader(HTTPConnectionHandler.OBJECT_ID_PARAM, objectID);
      
      DigitalObject obj = null;
      if(objectID!=null && repo.verifyDigitalObject(objectID)) {
        obj = repo.getDigitalObject(objectID);
      } else {
        obj = repo.createDigitalObject(objectID);
      }
      objectID = obj.getObjectID();

      // set the queued up elements
      for(String elementID : elementStrings.keySet()) {
        obj.getDataElement(elementID).write(new ByteArrayInputStream(elementStrings.get(elementID).getBytes("UTF8")));
      }
      
      // set the queued up attributes...
      if(atts.size()>0) obj.setAttributes(atts);
      for(String elementID : elAtts.keySet()) {
        obj.getDataElement(elementID).setAttributes(elAtts.get(elementID));
      }
    }

    if(redirect!=null) {
      response.sendRedirect(redirect);
    } else if(template!=null) {
      response.sendRedirect("/?"+HTTPConnectionHandler.OBJECT_ID_PARAM+"="+VelocityUtil.encodeURLComponent(objectID)+
                            "&"+HTTPConnectionHandler.TEMPLATE_PARAM+"="+VelocityUtil.encodeURLComponent(template));
    } else {
      response.sendRedirect("/?"+HTTPConnectionHandler.OBJECT_ID_PARAM+"="+VelocityUtil.encodeURLComponent(objectID));
    }
  }


}
