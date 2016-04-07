/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;
import net.cnri.do_api.DataElement;
import net.cnri.do_api.DigitalObject;
import net.cnri.do_api.Repository;
import net.cnri.do_api.Repository.QueryResults;
import net.cnri.do_api.SearchResult;
import net.cnri.do_api.Repository.CloseableIterator;
import net.cnri.dobj.DOAuthentication;
import net.cnri.dobj.DOConstants;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.util.StringUtils;
import net.handle.hdllib.Util;

public class RESTServlet extends HttpServlet {
    static final Logger logger = LoggerFactory.getLogger(RESTServlet.class);

    public static final int BUFFER_SIZE = 1024;
    public static final String XML = "application/xml";
    private static final String PLAINTEXT = "text/plain";

    private final Main serverMain;
    private final ExecutorService execServ;
    private final HTTPConnectionHandler httpConnectionHandler;
    
    public RESTServlet(Main serverMain, ExecutorService execServ, HTTPConnectionHandler httpConnectionHandler) {
        this.serverMain = serverMain;
        this.execServ = execServ;
        this.httpConnectionHandler = httpConnectionHandler;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            super.service(request,response);
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
                    logger.error("REST server: Permission error after response committed",e);
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
                logger.error("REST server error",e);
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
    
    private static void writeDataElement(Writer w, String elementId, HeaderSet atts) throws IOException {
        w.write("        <el id=\"");
        w.write(xmlAttrEscape(elementId));
        if(atts==null) w.write("\"/>\n");
        else {
            w.write("\">\n");
            for(Iterator it=atts.iterator(); it.hasNext(); ) {
                HeaderItem attItem = (HeaderItem)it.next();
                w.write("            <att name=\"");
                w.write(xmlAttrEscape(attItem.getName()));
                w.write("\" value=\"");
                w.write(xmlAttrEscape(attItem.getValue()));
                w.write("\" />\n");
            }
            w.write("        </el>\n");
        }
    }
    
    private static void writeXML(HttpServletResponse response, DigitalObject dobj, DataElement element, boolean listAtt, boolean listElements) throws IOException {
        response.setContentType(XML);
        response.setCharacterEncoding("UTF-8");
        Writer w = response.getWriter();
        w.write("<dor id=\"");
        w.write(xmlAttrEscape(dobj.getRepository().getID()));
        w.write("\">\n");
        w.write("    <do id=\"");
        w.write(xmlAttrEscape(dobj.getID()));
        w.write("\">\n");
        if(!listElements && element==null) {
            HeaderSet atts = dobj.getAttributes();
            for(Iterator it=atts.iterator(); it.hasNext(); ) {
                HeaderItem attItem = (HeaderItem)it.next();
                w.write("        <att name=\"");
                w.write(xmlAttrEscape(attItem.getName()));
                w.write("\" value=\"");
                w.write(xmlAttrEscape(attItem.getValue()));
                w.write("\" />\n");
            }
        }
        if(element!=null || !listAtt) {
            if(element==null) {
                String elementIDs[] = dobj.listDataElements();
                for(int i=0; elementIDs!=null && i<elementIDs.length; i++) {
                    HeaderSet atts = null;
                    if(!listElements) {
                        atts = dobj.getDataElement(elementIDs[i]).getAttributes();
                    }
                    writeDataElement(w,elementIDs[i],atts);
                }
            }
            else {
                HeaderSet atts = element.getAttributes();
                writeDataElement(w,element.getDataElementID(),atts);
            }
        }
        w.write("    </do>\n");
        w.write("</dor>\n");
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
        if(auth==null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
        ParsedURL url = new ParsedURL(request);

        // LIST ELEMENTS OR SEARCH
        if(url.doId==null) {
            if(request.getQueryString()!=null && !request.getQueryString().isEmpty()) {
                handleRestSearch(request,response,repo);
                return;
            }
            
            // Return XML Document Listing all DOs accessible through this DOR; if none, return empty list
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(XML);
            response.setCharacterEncoding("UTF-8");
            Iterator allObj = repo.listObjects();
            Writer w = response.getWriter();
            w.write("<dor id=\"");
            w.write(xmlAttrEscape(repo.getID()));
            w.write("\">\n");
            while(allObj.hasNext()) {
                String objID = String.valueOf(allObj.next());
                w.write("    <do id=\"");
                w.write(xmlAttrEscape(objID));
                w.write("\" />\n");
            }
            w.write("</dor>\n");
            return;
        }
        else {
            if(request.getQueryString()!=null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if(!repo.verifyDigitalObject(url.doId)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            DigitalObject dobj = repo.getDigitalObject(url.doId);
            
            DataElement element = null;
            if(url.elId!=null) {
                if(!dobj.verifyDataElement(url.elId)) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                element = dobj.getDataElement(url.elId);
            }
            
            // RETRIEVE AN ATTRIBUTE
            if(url.attName!=null) {
                String value;
                if(element==null) value = dobj.getAttribute(url.attName,null);
                else value = element.getAttribute(url.attName,null);
                
                if(value==null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                else {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType(PLAINTEXT);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(value);
                }
            }
            // RETRIEVE AN ELEMENT'S DATA
            else if(element!=null && !url.endsAtt) {
                String mimetype = element.getAttribute(DOConstants.MIME_TYPE_ATTRIBUTE,null);
                if(mimetype!=null) response.setContentType(mimetype);
                String filename = element.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE, url.elId);
                response.setHeader("Content-Disposition", "attachment; filename=\"" + escapeFilename(filename) + "\"");
                response.setHeader("Content-Length", String.valueOf(element.getSize()));
                
                InputStream in = element.read();
                OutputStream out = response.getOutputStream();
                byte buf[] = new byte[BUFFER_SIZE];
                int r;
                while((r=in.read(buf))>=0) out.write(buf, 0, r);
                out.close();
                return;
            }
            // RETRIEVE (SOME PART OF) THE DO'S STRUCTURE
            else {
                response.setStatus(HttpServletResponse.SC_OK);
                writeXML(response,dobj,element,url.endsAtt,url.endsEl);
            }
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(request.getQueryString()!=null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        ParsedURL url = new ParsedURL(request);
        if(url.doId==null) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow","GET, HEAD, POST");
            return;
        }
        if(url.endsAtt) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow","GET, HEAD, PUT, POST");
            return;
        }
        if(url.endsEl) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", "GET, HEAD, PUT, POST");
            return;
        }

        DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
        if(auth==null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
        
        if(!repo.verifyDigitalObject(url.doId)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if(url.elId==null && url.attName==null) {
            repo.deleteDigitalObject(url.doId);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        DigitalObject dobj = repo.getDigitalObject(url.doId);

        if(url.elId!=null) {
            if(!dobj.verifyDataElement(url.elId)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if(url.attName==null) {
                dobj.deleteDataElement(url.elId);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            
            dobj.getDataElement(url.elId).deleteAttribute(url.attName);
        }
        else {
            dobj.deleteAttribute(url.attName);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
    
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request,response);
    }
    
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

        ParsedURL url = new ParsedURL(request);
        if(url.doId==null && request.getMethod().equals("PUT")) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow","GET, HEAD, POST");
            return;
        }

        DOAuthentication auth = httpConnectionHandler.getAuthentication(request,response);
        if(auth==null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
    
        // CREATE A NEW OBJECT OR MODIFY ATOMICALLY AN EXISTING OBJECT
        if((url.elId==null || url.endsAtt) && url.attName==null) {
            /* Creates a new DO with the speciﬁed handle. If created, the DOR will assign
             * allot the specified handle to the DO and return it in a response with 
             * HTTP 201 Created. 
             */
            Reader reader = request.getReader();
            boolean emptyReq = reader == null;
            if(!emptyReq) {
                reader.mark(1);
                emptyReq = reader.read()<0;
                reader.reset();
            }
            
            ParsedAttributes input = null;
            if(!emptyReq) {
                try {
                    input = ParsedAttributes.parse(reader);
                }
                catch(XMLStreamException e) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    e.printStackTrace(response.getWriter());
                    return;
                }

                // if do is named name must match
                if(input.dobj!=null && input.dobj.id!=null && !input.dobj.id.equals(url.doId)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // if no el in url, any el in input must be named
                if(url.elId==null && input.el!=null && (input.el.containsKey(null) || input.el.containsKey(""))) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // if el in url or listing els, no do-level atts in input
                if((url.elId!=null || url.endsEl) && !(input.dobj==null || input.dobj.atts==null || input.dobj.atts.isEmpty())) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // if listing els, no atts
                if(url.endsEl && input.el!=null) {
                    for(ParsedAttributesPart part : input.el.values()) {
                        if(part.atts!=null && !part.atts.isEmpty()) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            return;
                        }
                    }
                }
                // if el in url, must be only one el and name must match or be missing
                if(url.elId!=null && (input.el==null || input.el.size()!=1 || !(input.el.containsKey(null) || input.el.containsKey("") || input.el.containsKey(url.elId)))) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // if el in url, must not be trying to delete it
                if(url.elId!=null && input.el.values().iterator().next()==null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                // if listing do atts, no els
                if(url.elId==null && url.endsAtt && input.el!=null && !input.el.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
            }

            boolean exists = url.doId!=null && repo.verifyDigitalObject(url.doId);
            if(emptyReq) {
                if(url.elId!=null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }
                if(exists) {
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    return;
                }
            }

            DigitalObject dobj;
            if(!exists) {
                dobj = repo.createDigitalObject(url.doId);
                response.setStatus(HttpServletResponse.SC_CREATED);
                String uri = "/do/" + StringUtils.encodeURLComponent(dobj.getID());
                response.setHeader("Location",uri);
            }
            else {
                dobj = repo.getDigitalObject(url.doId);
                response.setStatus(HttpServletResponse.SC_OK);
            }

            DataElement element = null;
            if(url.elId!=null) element = dobj.getDataElement(url.elId);

            if(input!=null) {
                if(input.dobj!=null && input.dobj.atts!=null) {
                    for(Map.Entry<String,String> entry : input.dobj.atts.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if(value==null) dobj.deleteAttribute(key);
                        else dobj.setAttribute(key,value);
                    }
                }
                if(input.el!=null) {
                    for(Map.Entry<String,ParsedAttributesPart> entry : input.el.entrySet()) {
                        String id = entry.getKey();
                        if(id==null || id=="") id = url.elId;
                        ParsedAttributesPart part = entry.getValue();
                        if(part==null) dobj.deleteDataElement(id);
                        else {
                            boolean elementExists = dobj.verifyDataElement(id);
                            DataElement thisElement = element!=null ? element : dobj.getDataElement(id);
                            if(!elementExists) thisElement.write(new ByteArrayInputStream(new byte[0]));
                            for(Map.Entry<String,String> attEntry : part.atts.entrySet()) {
                                String key = attEntry.getKey();
                                String value = attEntry.getValue();
                                if(value==null) thisElement.deleteAttribute(key);
                                else thisElement.setAttribute(key,value);
                            }
                        }
                    }
                }
            }
            
            writeXML(response,dobj,element,url.endsAtt,url.endsEl);
            return;
        }
        else {
            if(!repo.verifyDigitalObject(url.doId)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            DigitalObject dobj = repo.getDigitalObject(url.doId);

            // PUTTING AN ATTRIBUTE
            if(url.attName!=null) {
                String value = streamToString(request.getInputStream(),request.getCharacterEncoding());

                DataElement element = null;
                if(url.elId!=null) {
                    boolean exists = dobj.verifyDataElement(url.elId);
                    element = dobj.getDataElement(url.elId);
                    if(!exists) {
                        element.write(new ByteArrayInputStream(new byte[0]));
                    }
                }

                boolean attributeExisted;
                if(element==null) {
                    attributeExisted = dobj.getAttribute(url.attName, null)!=null;
                    // Replaces the plaintext value of this attribute. If created,returns HTTP 201 Created. 
                    dobj.setAttribute(url.attName, value);
                }
                else {
                    attributeExisted = element.getAttribute(url.attName, null)!=null;
                    // Replaces the plaintext value of this attribute. If created,returns HTTP 201 Created. 
                    element.setAttribute(url.attName, value);
                }

                if(!attributeExisted) {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    String path = "/do/" + StringUtils.encodeURLComponent(url.doId);
                    if(element!=null) path += "/el/" + StringUtils.encodeURLComponent(url.elId);
                    path += "/att/" + StringUtils.encodeURLComponent(url.attName);
                    response.setHeader("Location",path);
                    return;
                }
                else {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }
            } 
            else {
                // PUTTING AN ELEMENT'S DATA
                
                boolean exists = dobj.verifyDataElement(url.elId);
                DataElement element = dobj.getDataElement(url.elId);
                InputStream in = request.getInputStream();
                if(in!=null) {
                    element.write(in);
                    String mimetype = request.getContentType();
                    if(mimetype!=null) element.setAttribute(DOConstants.MIME_TYPE_ATTRIBUTE,mimetype);
                }
                else if(!exists) {
                    element.write(new ByteArrayInputStream(new byte[0]));
                }
                if(!exists) {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    response.setHeader("Location","/do/" + StringUtils.encodeURLComponent(url.doId) + "/el/" + StringUtils.encodeURLComponent(url.elId));
                    return;
                }
                else {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }
            }
        }
        
    }
    
    private void handleRestSearch(HttpServletRequest request, HttpServletResponse response, Repository repo) throws IOException {
        String query = request.getParameter("query");
        boolean indexUpToDate = request.getParameter("indexUpToDate") != null;
        if(query==null) {
            if(indexUpToDate) {
                repo.performOperation(DOConstants.INDEX_UP_TO_DATE_ID,new HeaderSet());
                return;
            }
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String returnedFieldsStr = request.getParameter("returnedFields");
        List<String> returnedFields = null; 
        if (returnedFieldsStr != null) returnedFields = getFieldsFromString(returnedFieldsStr);
        String sortOrder = request.getParameter("sortOrder"); 
        String sortFieldsStr = request.getParameter("sortFields"); 
        List<String> sortFields = null;
        if (sortFieldsStr != null) sortFields = getFieldsFromString(sortFieldsStr); 
        String paramPageSize = request.getParameter("pageSize");
        int pageSize = 0; 
        if (paramPageSize != null) {
        	try {
        		pageSize = Integer.parseInt(paramPageSize);
        	} catch (NumberFormatException e) {}
        }
        String paramPageOffset = request.getParameter("pageOffset");
        int pageOffset = 0; 
        if (paramPageOffset != null) {
        	try {
        		pageOffset = Integer.parseInt(paramPageOffset);
        	} catch (NumberFormatException e) {}
        }        
        
        @SuppressWarnings("deprecation")
        QueryResults results = repo.search(query, returnedFields, sortFields, sortOrder, pageSize, pageOffset, indexUpToDate);
        CloseableIterator<HeaderSet> iter = results.getIterator();
        Writer w;
        try {
            response.setContentType(XML);
            response.setCharacterEncoding("UTF-8");
            w = response.getWriter();
            w.write("<dor id=\"");
            w.write(xmlAttrEscape(repo.getID()));
            w.write("\">\n");
        	w.write("    <query>\n");
        	w.write("        <queryString>"+StringUtils.cgiEscape(query)+"</queryString>\n");
        	if (returnedFields!=null)
        		w.write("        <returnedFields>"+StringUtils.cgiEscape(returnedFieldsStr)+"</returnedFields>\n"); 
        	if (sortOrder!=null)
        		w.write("        <sortOrder>"+StringUtils.cgiEscape(sortOrder)+"</sortOrder>\n");
        	if (sortFields!=null)
        		w.write("        <sortFields>"+StringUtils.cgiEscape(sortFieldsStr)+"</sortFields>\n");
            if (pageSize!=0) {
            	w.write("        <pageOffset>"+pageOffset+"</pageOffset>\n");
               	w.write("        <pageSize>"+pageSize+"</pageSize>\n"); 
            }
            w.write("    </query>\n");
            if (results.getTotalMatches()!=-1) 
            	w.write("    <totalMatches>"+results.getTotalMatches()+"</totalMatches>\n"); //omit if -1
            while(iter.hasNext()) {
                HeaderSet headerSet = iter.next();
                SearchResult searchResult = SearchResult.ofHeaderSet(headerSet);
                w.write("    <do");
                if(searchResult.getObjectID()!=null) {
                    w.write(" id=\"");
                    w.write(xmlAttrEscape(searchResult.getObjectID()));
                    w.write("\"");
                }
                if(!repo.getID().equals(searchResult.getRepoID())) {
                    w.write(" repo=\"");
                    w.write(xmlAttrEscape(searchResult.getObjectID()));
                    w.write("\"");
                }
                if(searchResult.getScore()!=null) {
                    w.write(" score=\"");
                    w.write(xmlAttrEscape(searchResult.getScore()));
                    w.write("\"");
                }
                w.write(">\n");

                if(searchResult.getFields()!=null) {
                    for(Map.Entry<String,String> entry : searchResult.getFields().entrySet()) {
                        w.write("        <field name=\"");
                        w.write(xmlAttrEscape(entry.getKey()));
                        w.write("\" value=\"");
                        w.write(xmlAttrEscape(entry.getValue()));
                        w.write("\" />\n");
                    }
                }
                if(searchResult.getAtts()!=null) {
                    for(Map.Entry<String,String> entry : searchResult.getAtts().entrySet()) {
                        w.write("        <att name=\"");
                        w.write(xmlAttrEscape(entry.getKey()));
                        w.write("\" value=\"");
                        w.write(xmlAttrEscape(entry.getValue()));
                        w.write("\" />\n");
                    }
                }
                
                if(searchResult.getElements()!=null) {
                    for(SearchResult.Element element : searchResult.getElements().values()) {
                        w.write("        <el id=\"");
                        w.write(xmlAttrEscape(element.getElementID()));
                        w.write("\">\n");
                     
                        if(element.getFields()!=null) {
                            for(Map.Entry<String,String> entry : element.getFields().entrySet()) {
                                w.write("            <field name=\"");
                                w.write(xmlAttrEscape(entry.getKey()));
                                w.write("\" value=\"");
                                w.write(xmlAttrEscape(entry.getValue()));
                                w.write("\" />\n");
                            }
                        }
                        if(element.getAtts()!=null) {
                            for(Map.Entry<String,String> entry : element.getAtts().entrySet()) {
                                w.write("            <att name=\"");
                                w.write(xmlAttrEscape(entry.getKey()));
                                w.write("\" value=\"");
                                w.write(xmlAttrEscape(entry.getValue()));
                                w.write("\" />\n");
                            }
                        }
                        if(element.getPAtts()!=null) {
                            for(Map.Entry<String,String> entry : element.getPAtts().entrySet()) {
                                w.write("            <patt name=\"");
                                w.write(xmlAttrEscape(entry.getKey()));
                                w.write("\" value=\"");
                                w.write(xmlAttrEscape(entry.getValue()));
                                w.write("\" />\n");
                            }
                        }
                        if(element.getMPAtts()!=null) {
                            for(Map.Entry<String,String> entry : element.getMPAtts().entrySet()) {
                                w.write("            <mpatt name=\"");
                                w.write(xmlAttrEscape(entry.getKey()));
                                w.write("\" value=\"");
                                w.write(xmlAttrEscape(entry.getValue()));
                                w.write("\" />\n");
                            }
                        }

                        if(element.getText()!=null) {
                            w.write("            <text>");
                            w.write(StringUtils.cgiEscape(element.getText()));
                            w.write("</text>\n");
                        }

                        w.write("        </el>\n");
                    }
                }
                
                w.write("    </do>\n");
            }
        }
        finally {
            iter.close();
        }
        w.write("</dor>\n");
    }
    
    private static List<String> getFieldsFromString(String s) {
    	return Arrays.asList(s.split(","));
    }
    
    static String urlDecode(String s) {
        return StringUtils.decodeURLIgnorePlus(s);
    }
    
    //Read from stream and return string
    static String streamToString(InputStream input, String encoding) throws IOException{
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[BUFFER_SIZE];
        int r;
        while((r=input.read(buf))>=0)
            bout.write(buf, 0, r);
        if(encoding==null) return Util.decodeString(bout.toByteArray());
        else {
            return new String(bout.toByteArray(), encoding);
        }
    }

    static String escapeFilename(String filename) {
      // This is gruesome, but as of mid-2010 is the only reasonably portable solution
      try {
        String res = new String(filename.getBytes("ISO-8859-1"),"ISO-8859-1");
        return res.replace("\n","?").replace("\\","\\\\").replace("\"","\\\"");
      } catch(UnsupportedEncodingException e) {
        throw new AssertionError(e);
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
          else if(ch>=32 && ch < 0xFFFE) // non-whitespace control characters and FFFE, FFFF illegal in XML; delete and hope for the best
            sb.append(ch);
        }
        return sb.toString();
      }

    
    static class ParsedURL {
        String doId;
        boolean endsEl;
        String elId;
        boolean endsAtt;
        String attName;
        
        ParsedURL(String path) {
            if(!path.endsWith("/")) path = path + "/";
            if(path.equals("/do/")) return;
            int elIndex = path.indexOf("/el/");
            int attIndex = path.indexOf("/att/");
            
            // avoid /do/.../att/.../el/...
            if(attIndex >= 0 && elIndex > attIndex) elIndex = -1;
            
            // look for terminal /el/ or /att/
            if(elIndex+4 == path.length()) {
                endsEl = true;
            }
            if(attIndex+5 == path.length()) {
                endsAtt = true;
            }
            
            if(elIndex >= 0) {
                doId = path.substring(4,elIndex);
                if(attIndex >= 0) {
                    elId = path.substring(elIndex + 4, attIndex);
                    if(!endsAtt) {
                        attName = path.substring(attIndex + 5, path.length()-1);
                    }
                }
                else {
                    if(!endsEl) {
                        elId = path.substring(elIndex + 4, path.length()-1);
                    }
                }
            }
            else {
                if(attIndex >= 0) {
                    doId = path.substring(4,attIndex);
                    if(!endsAtt) {
                        attName = path.substring(attIndex + 5, path.length()-1);
                    }
                }
                else {
                    doId = path.substring(4,path.length()-1);
                }
            }
            
            if(doId!=null) doId = urlDecode(doId);
            if(elId!=null) elId = urlDecode(elId);
            if(attName!=null) attName = urlDecode(attName);
        }
        
        ParsedURL(HttpServletRequest req) {
            this(pathOfReq(req));
        }

        static String pathOfReq(HttpServletRequest req) {
            String requestURI = req.getRequestURI(); 
            String contextPath = req.getContextPath();
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
            }
        }
    }
    
    private static class ParsedAttributesPart {
        String id;
        Map<String,String> atts; // map to null to delete
        
        @Override
        public String toString() {
            return "ParsedAttributesPart [id=" + id + ", atts=" + atts + "]";
        }

        static public ParsedAttributesPart parse(String id,XMLStreamReader reader) throws XMLStreamException {
            ParsedAttributesPart res = new ParsedAttributesPart();
            res.id = id;
            res.atts = new HashMap<String,String>();
            boolean readingAtt = false;
            reader.next(); // skip the start el
            while(reader.hasNext()) {
                if(reader.isStartElement()) {
                    if(!readingAtt && reader.getLocalName().equals("el")) break;
                    if(readingAtt || !reader.getLocalName().equals("att")) throw new XMLStreamException("Unexpected <" + reader.getLocalName() + ">");
                    readingAtt = true;
                    String name = reader.getAttributeValue("","name");
                    if(name==null) throw new XMLStreamException("Expecting att name");
                    String value = reader.getAttributeValue("","value");
                    String deleted = reader.getAttributeValue("","deleted");
                    if(deleted!=null && !"true".equals(deleted) && !"false".equals(deleted)) {
                        throw new XMLStreamException("deleted must be true or false");                        
                    }
                    if(value!=null && deleted!=null && !"false".equals(deleted)) {
                        throw new XMLStreamException("Cannot delete and specify a value");                        
                    }
                    if("true".equals(deleted)) res.atts.put(name,null);
                    else {
                        if(value==null) throw new XMLStreamException("Expecting value if deleted is not true");
                        else res.atts.put(name,value);
                    }
                }
                else if(reader.isEndElement()) {
                    if(!readingAtt) break;
                    readingAtt = false;
                }
                else if(reader.isCharacters()) {
                    if(!reader.isWhiteSpace()) throw new XMLStreamException("Unexpected characters " + reader.getText());
                }
                logger.debug("{}",res);
                reader.next();
            }
            logger.debug(reader.getLocalName());
            return res;
        }
    }
    
    private static class ParsedAttributes {
        ParsedAttributesPart dobj; // null if element only
        Map<String,ParsedAttributesPart> el; // map to null to delete
        
        @Override
        public String toString() {
            return "ParsedAttributes [dobj=" + dobj + ", el=" + el + "]";
        }

        static public ParsedAttributes parse(Reader xml) throws XMLStreamException {
            ParsedAttributes res = new ParsedAttributes();
            XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(xml);
            try {
                boolean inDor = false;
                boolean inDo = false;
                boolean inEl = false;
                boolean done = false;
                while(reader.hasNext()) {
                    if(reader.isStartElement()) {
                        boolean bad = false;
                        if(inEl || done) bad = true;
                        else if(inDo) bad = !reader.getLocalName().equals("el");
                        else if(inDor) bad = !reader.getLocalName().equals("do");
                        else bad = !reader.getLocalName().equals("dor") &&  !reader.getLocalName().equals("do") && !reader.getLocalName().equals("el");
                        if(bad) throw new XMLStreamException("Unexpected <" + reader.getLocalName() + ">");
                        
                        if(inDo) inEl = true;
                        else if(inDor) inDo = true;
                        else if(reader.getLocalName().equals("dor")) inDor = true;
                        else if(reader.getLocalName().equals("do")) inDo = true;
                        else if(reader.getLocalName().equals("el")) inEl = true;
                        
                        String id = reader.getAttributeValue("","id");
                        
                        if(inEl) {
                            if(res.el==null) res.el = new HashMap<String,RESTServlet.ParsedAttributesPart>();
                            if(res.el.containsKey(id)) throw new XMLStreamException("Duplicate element " + id);
                            String deleted = reader.getAttributeValue("","deleted");
                            if(deleted!=null && !"true".equals(deleted) && !"false".equals(deleted)) {
                                throw new XMLStreamException("deleted must be true or false");                        
                            }
                            ParsedAttributesPart el = ParsedAttributesPart.parse(id,reader);
                            if("true".equals(deleted)) {
                                if(el.atts!=null && !el.atts.isEmpty()) throw new XMLStreamException("Unexpected attributes for deleted element " + id);
                                res.el.put(id,null);
                            }
                            else res.el.put(id,el);
                            continue; // skip reader.next();
                        }
                        else if(inDo) {
                            res.dobj = ParsedAttributesPart.parse(id,reader);
                            continue; // skip reader.next();
                        }
                    }
                    else if(reader.isEndElement()) {
                        if(inEl) {
                            inEl = false;
                            if(!inDo) done = true;
                        }
                        else if(inDo) {
                            inDo = false;
                            done = true;
                        }
                        else if(inDor) {
                            inDor = false;
                            done = true;
                        }
                    }
                    else if(reader.isCharacters()) {
                        if(!reader.isWhiteSpace()) throw new XMLStreamException("Unexpected characters " + reader.getText());
                    }
                    reader.next();
                }
            }
            finally {
                reader.close();
            }
            return res;
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println(ParsedAttributes.parse(new StringReader("<dor><el id='content'><att name='elatt' value='elval'/></el></dor>")));
    }
}
