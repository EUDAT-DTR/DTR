/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.web.*;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.util.StringUtils;
import net.handle.hdllib.*;
import net.handle.util.X509HSTrustManager;

import java.io.*;
import java.net.InetAddress;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.cert.X509Certificate;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.*;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.generic.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTTPConnectionHandler objects run in their own thread and are responsible for
 *  managing a single connection to the server.  Each connection may have
 *  numerous operation invocations which is why each connection handler has
 *  its own RequestHandlerPool to keep track of threads for operation invocation
 *  requests.
 */

public class HTTPConnectionHandler
extends HttpServlet
implements ServletContextListener
{
    static final Logger logger = LoggerFactory.getLogger(HTTPConnectionHandler.class);

    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    public static final String DEFAULT_GET_ELEMENT = "content";
    public static final String DEFAULT_ELEMENT_REF_ATT = "content_element";
    public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
    public static final String XML = "application/xml";
    public static final String HTML = "text/html";
    private static final String PLAINTEXT = "text/plain";
    public static final String DO_AUTHENTICATION_REQ_ATTRIBUTE = "net.cnri.apps.doserver.do_authentication";
    private static final String AUTHORIZATION_ATTRIBUTE = "net.cnri.apps.doserver.authorization";
    private static final String ALLOW_HTTP_SIGNUP = "allow_http_signup";
    
    public static final String STATIC_URI_PREFIX = "/static/";
    private static EscapeTool escapeTool = new EscapeTool();

    public static final String OBJECT_ID_PARAM = "id";
    public static final String TEMPLATE_PARAM = "template";
    public static final int BUFFER_SIZE = 1024;

    private Main serverMain;
    private File staticBase;
    VelocityEngine ve = null;
    private ExecutorService execServ;

    /** construct a new handler for all HTTP requests */
    HTTPConnectionHandler(Main main) {
    	ve = new VelocityEngine();
        execServ = Executors.newCachedThreadPool();
        this.serverMain = main;
        this.staticBase = new File(serverMain.getBaseFolder(), "html" + File.separator +
                                                             "templates" + File.separator +
                                                             "static" + File.separator);
    }
  

    /**
     * This should be called before starting the HTTP server in order to configure the 
     * servlet parameters.
     */
    void setupServletHolder(ServletContextHandler context) {
        context.addEventListener(this);
        ServletHolder servletHolder = new ServletHolder(this);
        try {
            ve.setProperty("resource.loader","file,class");
            ve.setProperty("class.resource.loader.class","org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
            ve.setProperty("file.resource.loader.path", new File(serverMain.getBaseFolder(), "html/templates").getAbsolutePath());
//            ve.setApplicationAttribute("javax.servlet.ServletContext",servletHolder.getServletHandler().getServletContext());
//            ve.setProperty("runtime.log.logsystem.class","org.apache.velocity.runtime.log.ServletLogChute");
//            ve.setProperty("runtime.log.logsystem.servlet.level","info");
            ve.setProperty("input.encoding","UTF-8");
            ve.setProperty("output.encoding","UTF-8");
            ve.setProperty("runtime.log.logsystem.class","org.apache.velocity.runtime.log.SystemLogChute");
            ve.init();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        context.addServlet(servletHolder, "/*");
        context.addServlet(new ServletHolder(new RESTServlet(serverMain, execServ, this)),"/do/*");
        context.addServlet(new ServletHolder(new BuilderServlet(serverMain, execServ, this)),"/set/*");
        context.addServlet(new ServletHolder(new PerformOpServlet(serverMain, execServ, this)), "/performop");
        context.addServlet(new ServletHolder(new HashServlet(serverMain, execServ, this)),"/hash/*");
        if(serverMain.getConfigVal(ALLOW_HTTP_SIGNUP, "false").trim().equalsIgnoreCase("true")) {
            context.addServlet(new ServletHolder(new UserAccountServlet(serverMain, execServ, this)), "/usermanagement/*");
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        execServ.shutdown();
    }
    
    // HTTP Handler
    public void service(HttpServletRequest request, HttpServletResponse response) 
    throws IOException
    {
        String method = request.getMethod();
        if(!method.equals("GET") && !method.equals("POST") && !method.equals("PUT") && !method.equals("DELETE") && 
                !method.equals("HEAD") && !method.equals("OPTIONS")) return;

        //request.setHandled(true);
        //parseGetParams(request, request.getRequestURI(), request.getQueryString());
        String identifier = request.getRequestURI();
      if(identifier.startsWith(STATIC_URI_PREFIX)) {
          handleStatic(request, response);
          return;
        }
        
        DOAuthentication auth = getAuthentication(request, response);
        if(auth==null) return;
        
        response.setStatus(HttpServletResponse.SC_OK);
        
//        if(identifier.startsWith("/do")) handleRest(request,response, auth);
        if(identifier.startsWith("/user")) handleUser(request,response,auth);
        else if(identifier.startsWith("/invoke")) handleInvoke(request,response);
        else if(identifier.startsWith("/auth/delegators")) handleDelegators(request,response);
        else handleWeb(request, response, auth);
    }

    private static Map/*<String, String>*/ convertToStringValueMap(Map/*<String, String[]>*/ parameterMap) {
        Map/*<String,String>*/ ret = new HashMap();
        Set set = parameterMap.entrySet();
        for (Iterator iter = set.iterator(); iter.hasNext();) {
            Map.Entry mapEntry = (Map.Entry) iter.next();
            String key = (String) mapEntry.getKey();
            String[] value = (String[]) mapEntry.getValue();
            ret.put(key, value[0]);
        }
        return ret;
    }


    void handleInvoke(HttpServletRequest req, HttpServletResponse response) {
        // TODO
    }

    private void handleUser(HttpServletRequest request, HttpServletResponse response, DOAuthentication auth) throws IOException {
        try {
            String method = request.getMethod();
            boolean isGet = method.equals("GET") || method.equals("HEAD");
            boolean isPut = method.equals("PUT");
            boolean isPost = method.equals("POST");
            boolean isDelete = method.equals("DELETE");

            String user = request.getRequestURI();
            if(!user.startsWith("/user")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            user = user.substring("/user".length());
            if(user.startsWith("/")) {
                user = user.substring(1);
            }
            user = StringUtils.decodeURLIgnorePlus(user);
            
            String password = request.getParameter("password");
            if(password!=null) password = StringUtils.decodeURL(password);
            if((isPut || isPost) && (password==null || password.isEmpty())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            
            String authOperation = null;
            if(isDelete || ((isGet || isPost || isPut) && !auth.getID().equals(user))) authOperation = "1037/user-admin";
            
            if(authOperation!=null) {
                Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
                boolean authChecks = repo.checkAuthorization(auth.getID(),null,authOperation,null);
                if(!authChecks) {
                    throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                }
            }

            HandleResolver resolver = DOClient.getResolver().getResolver();
            AuthenticationInfo authInfo = serverMain.getAuth().toHandleAuth();
            if(isGet) {
                ResolutionRequest resReq = new ResolutionRequest(Util.encodeString(user),
                        new byte[][] { Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE) },null,authInfo);
                AbstractResponse resp = resolver.processRequest(resReq);
                if(resp.responseCode==AbstractMessage.RC_SUCCESS) {
                    HandleValue[] values = ((ResolutionResponse)resp).getHandleValues();
                    if(values!=null && values.length>=1 && values[0].getTypeAsString().equals(DOConstants.OBJECT_SERVER_HDL_TYPE)
                            && values[0].getDataAsString().equals(serverMain.getServerID())) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                }
                else if(resp.responseCode==AbstractMessage.RC_HANDLE_NOT_FOUND){
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                else if(resp.responseCode==AbstractMessage.RC_INVALID_ADMIN) {
                    throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                }
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            else if(isPut || isPost) {
                ResolutionRequest resReq = new ResolutionRequest(Util.encodeString(user),
                        new byte[][] { Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), Common.SECRET_KEY_TYPE },null,authInfo);
                resReq.ignoreRestrictedValues = false;
                AbstractResponse resp = resolver.processRequest(resReq);
                if(resp.responseCode==AbstractMessage.RC_SUCCESS) {
                    HandleValue[] values = ((ResolutionResponse)resp).getHandleValues();
                    if(values!=null) {
                        boolean userIsHere = false;
                        int secretKeyIndex = 300;
                        for(HandleValue value : values) {
                            if(value.getTypeAsString().equals(DOConstants.OBJECT_SERVER_HDL_TYPE)
                                    && value.getDataAsString().equals(serverMain.getServerID())) {
                                userIsHere = true;
                            }
                            if(Util.equals(value.getType(),Common.SECRET_KEY_TYPE)) {
                                secretKeyIndex = value.getIndex();
                            }
                        }
                        if(!userIsHere) {
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            logger.error("Error modifiying password of " + user + ": " + resp);
                            return;
                        }
                        HandleValue value = new HandleValue(secretKeyIndex,Common.SECRET_KEY_TYPE,Util.encodeString(password));
                        value.setAnyoneCanRead(false);
                        ModifyValueRequest modReq = new ModifyValueRequest(Util.encodeString(user),value,authInfo);
                        AbstractResponse modResp = resolver.processRequest(modReq);
                        if(modResp.responseCode==AbstractMessage.RC_SUCCESS) {
                            response.setStatus(HttpServletResponse.SC_OK);
                            return;
                        }
                        else if(modResp.responseCode==AbstractMessage.RC_INVALID_ADMIN) {
                            throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                        }
                        else {
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            logger.error("Error modifiying password of " + user + ": " + modResp);
                            return;
                        }
                    }
                }
                else if(resp.responseCode==AbstractMessage.RC_HANDLE_NOT_FOUND){
                    HandleValue value1 = new HandleValue(300,Common.SECRET_KEY_TYPE,Util.encodeString(password));
                    value1.setAnyoneCanRead(false);
                    HandleValue value2 = new HandleValue(1,Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE),Util.encodeString(serverMain.getServerID()));
                    CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(user),new HandleValue[] { value1, value2 },authInfo);
                    AbstractResponse creResp = resolver.processRequest(req);
                    if(creResp.responseCode==AbstractMessage.RC_SUCCESS) {
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        return;
                    }
                    else if(creResp.responseCode==AbstractMessage.RC_INVALID_ADMIN) {
                        throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                    }
                    else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        logger.error("Error creating user " + user + ": " + creResp);
                        return;
                    }
                }
                else if(resp.responseCode==AbstractMessage.RC_INVALID_ADMIN) {
                    throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                }
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                logger.error("Error creating user " + user + ": " + resp);
                return;
            }
            else if(isDelete) {
                DeleteHandleRequest delReq = new DeleteHandleRequest(Util.encodeString(user),authInfo);
                AbstractResponse resp = resolver.processRequest(delReq);
                if(resp.responseCode==AbstractMessage.RC_SUCCESS) {
                    HandleValue[] values = ((ResolutionResponse)resp).getHandleValues();
                    if(values!=null && values.length>=1 && values[0].getTypeAsString().equals(DOConstants.OBJECT_SERVER_HDL_TYPE)
                            && values[0].getDataAsString().equals(serverMain.getServerID())) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                }
                else if(resp.responseCode==AbstractMessage.RC_HANDLE_NOT_FOUND){
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                else if(resp.responseCode==AbstractMessage.RC_INVALID_ADMIN) {
                    throw new DOException(DOException.PERMISSION_DENIED_ERROR,"operation not allowed");
                }
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        catch(DOException e) {
            if(e.getErrorCode()==DOException.PERMISSION_DENIED_ERROR) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"hdl:" + serverMain.getServerID() + "\"");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            else {
                if(!response.isCommitted()) {
                    response.reset();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("text/plain");
                    response.setCharacterEncoding("UTF-8");
                    e.printStackTrace(response.getWriter());
                }
                logger.error("Error in handleUser",e);
            }
        }
        catch(Exception e) {
            if(!response.isCommitted()) {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain");
                response.setCharacterEncoding("UTF-8");
                e.printStackTrace(response.getWriter());
            }
            logger.error("Error in handleUser",e);
        }
    }
    
    Template getTemplate(String template) throws Exception {
      try {
        String localLang = serverMain.getConfigVal("default_localisation", null);
        if(localLang!=null) {
          String localTemplate;
          int lastSlashIdx = template.lastIndexOf('/'); 
          if(lastSlashIdx>=0) {
            localTemplate = template.substring(0, lastSlashIdx+1) + localLang + template.substring(lastSlashIdx);
          } else {
            localTemplate = localLang + "/" + template;
          }
          logger.debug("checking localised file {}",localTemplate);
          if(ve.resourceExists(localTemplate)) {
            Template t = ve.getTemplate(localTemplate);
            if(t!=null) {
              logger.debug("  got it!");
              return t;
            }
          }
          logger.debug("  no localised file");
        }
        if(ve.resourceExists(template)) {
          Template t = ve.getTemplate(template);
          if(t!=null) return t;
        }
      } catch (Exception e) {}
      
      return ve.getTemplate("/net/cnri/apps/doserver/resources/pages/" + template);
    }
    
    
    private void handleStatic(HttpServletRequest request, HttpServletResponse response) 
    throws IOException
    {
      String path = request.getPathInfo();
      if(path.startsWith(STATIC_URI_PREFIX)) path = path.substring(STATIC_URI_PREFIX.length());
      
      File f = new File(staticBase, path);
      sendFile(request, response, f);
    }

    private static void sendFile(HttpServletRequest request, HttpServletResponse response, File f)
    throws IOException
    {
      if(!f.exists() || !f.canRead()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      
      FileInputStream fin = new FileInputStream(f);
      ServletOutputStream sout = response.getOutputStream();
      byte buf[] = new byte[BUFFER_SIZE];
      int r;
      while((r=fin.read(buf))>=0) sout.write(buf, 0, r);
      sout.close();
    }
    
    /**
     * Handle HTTP communication with a client (likely a web browser) to display dynamically rendered HTML.
     * Accepts browser requests for '/', '/show', '/list', '/search' and '/get'.
     * '/auth' requests are handled by the authentication method.
     * 
     * Uses template built into the classpath.
     * 
     * @param request
     * @param response
     * @throws IOException
     */
    private void handleWeb(HttpServletRequest request, HttpServletResponse response, DOAuthentication auth) throws IOException {
        response.setContentType(HTML);
        Repository repo =  new HTTPRepositoryInterface(serverMain, execServ, request, auth.getID());
        SearchableMap model = new SearchableMap(repo);
        String repoName = serverMain.getServerID();
        String repoLink = objectHref(serverMain.getServerID());
        model.put("request", request);
        model.put("response", response);
        model.put("repoName", repoName);
        model.put("repoLink", repoLink);
        model.put("baseurl", request.getRequestURL());
        model.put("csvEncoder", new VelocityUtil());
        model.put("escape", escapeTool);
        model.put("SortTool", new DoSortTool());
        model.put("DateTool", new DateTool());
        model.put("StringUtils", new org.apache.velocity.util.StringUtils());
        for(Enumeration<String> paramNames = request.getParameterNames(); paramNames.hasMoreElements();) {
          String paramName = paramNames.nextElement();
          model.put("param."+paramName, request.getParameter(paramName));
        }
        if(auth!=null) {
          model.put("auth", auth);
        }
        
        model.put("repo", new SearchableMap(repo));
        String objectID = request.getParameter(OBJECT_ID_PARAM);
        if(objectID!=null) model.put("object", model.getObject(objectID)); // add a specific object, if given
        
        String templateName = "index.html";
        String requestURI = request.getRequestURI();
        
        if (requestURI.equals("/") || requestURI.equals("/show")) {
          // display the default page
        } else if(requestURI.startsWith("/auth/basic")) {
          String redirectURL = request.getParameter("redirect");
          if(redirectURL==null || redirectURL.trim().length()<=0) redirectURL = "/";
          response.sendRedirect(redirectURL);
          return;
//        } else if (request.getRequestURI().equals("/list")) {
//          // TODO: get rid of the "big list".   all listings should be searches
//          listObjects(repo, request, response);
//          return;
//        } else if (requestURI.equals("/show")) {
//          String objectID = request.getParameter(OBJECT_ID_PARAM);
//          if(objectID==null) objectID = repo.getID();
//          model.put("object", model.getObject(objectID));
//          templateName = "showObject.html";
        } else if (requestURI.equals("/search")) {
          String queryStr = request.getParameter("query");
          if(queryStr==null) queryStr = request.getParameter("q");
          if(queryStr==null) queryStr = "";
          
          HeaderSet headers = new HeaderSet();
          headers.addHeader("query", queryStr);
          
          for(Enumeration<String> paramNames=request.getParameterNames(); paramNames.hasMoreElements(); ) {
            String paramName = paramNames.nextElement();
            String paramVals[] = request.getParameterValues(paramName);
            for(String paramVal : paramVals) headers.addHeader(paramName, paramVal);
          }
          
          //model.put("results", model.doSearch(queryStr));
          model.put("query", queryStr);
          model.put("results", model.doSearch(headers));
          templateName = "searchResults.html";
        } else if (requestURI.equals("/get")) { // an inline request for an element
          String objID = request.getParameter("id");
          if(objID==null) objID = objectID;
          try {
            DigitalObject digObj = repo.getDigitalObject(objID);
            String elementID = request.getParameter("el");
            if(elementID==null) {
              elementID = digObj.getAttribute(DEFAULT_ELEMENT_REF_ATT, DEFAULT_GET_ELEMENT);
            }
            DataElement element = digObj.getDataElement(elementID);
            String filename = element.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE, elementID);
            response.setContentType(element.getAttribute(DOConstants.MIME_TYPE_ATTRIBUTE, DEFAULT_MIME_TYPE));
            response.setHeader(CONTENT_DISPOSITION_HEADER, "inline; filename=\"" + safeHTML(filename) + "\";");
            
            long size = element.getSize();
            if (size >= 0) response.setContentLength((int) size);
            InputStream in = element.read();
            OutputStream out = response.getOutputStream();
            byte buf[] = new byte[BUFFER_SIZE];
            int r;
            while ((r = in.read(buf)) >= 0) out.write(buf, 0, r);
          } catch (DOException e) {
            logger.error("Error accessing object",e);
            if(!response.isCommitted()) {
              response.setStatus(HttpServletResponse.SC_NOT_FOUND);
              PrintWriter w = response.getWriter();
              w.println("<html>");
              w.println("<head><title>Object not found: " + safeHTML(objID) + "</title></head>");
              w.println("<body>");
              w.println("<h1>Object not found: " + safeHTML(objID) + "</h1>");
            }
          }
          return;
        } else if(requestURI.equals("/favicon.ico")) {
            byte[] favicon = getFavicon();
            if(favicon!=null) {
                response.setContentType("image/x-icon");
                response.getOutputStream().write(favicon);
                return;
            }
            else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } else {
            String resourcePath = request.getPathInfo();
            templateName = resourcePath;
        }
        
        String overrideTemplate = request.getParameter(TEMPLATE_PARAM);
        if(overrideTemplate!=null) {
          templateName = overrideTemplate;
        }
        
        try {
          while(templateName!=null && templateName.startsWith("/")) {
            templateName = templateName.substring(1);
          }
          Template template = null;
          if(templateName!=null) {
              try {
                  template = getTemplate(templateName);
              }
              catch(ResourceNotFoundException e) {
                  logger.error(e.getMessage());
              }
              catch(Exception e) {
                  logger.error("Exception in getTemplate",e);
              }
          }
          if(template==null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(HTML);
            response.getWriter().println("<html>");
            response.getWriter().println("<head><title>Requested resource not available</title></head>");
            response.getWriter().println("<body><p>You are trying to access a resource that is unavailable. Its likely that the URL is incorrect.</p></body></html>");
            return;
          }
          response.setCharacterEncoding("UTF-8");
          //System.err.println("showing template "+templateName);
          template.merge(new VelocityContext(model), response.getWriter());
        } catch (Exception e) {
            logger.error("Error in handleWeb",e);
        }
    }

    private byte[] getFavicon() {
        try {
            File faviconFile = new File(serverMain.getBaseFolder(), "html/favicon.ico");
            if(faviconFile.exists()) {
                byte[] res = new byte[(int)faviconFile.length()];
                InputStream in = new FileInputStream(faviconFile);
                int r, n = 0;
                while(n < res.length && (r = in.read(res,n,res.length - n)) > 0) n += r;
                if(n==res.length) {
                    return res;
                }
            }
            InputStream in = getClass().getResourceAsStream("/net/cnri/apps/doserver/resources/pages/favicon.ico");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int r;
            byte[] buf = new byte[4096];
            while((r = in.read(buf)) > 0) {
                out.write(buf,0,r);
            }
            return out.toByteArray();
        }
        catch(IOException e) {
            return null;
        }
    }
    
//    /**
//     * process a request to create an object, specifying the attributes as HTTP parameters
//     * @param request
//     * @param response
//     * @param auth
//     * @throws IOException
//     */
//    private void handleCreate(HttpServletRequest request, HttpServletResponse response, DOAuthentication auth) throws IOException {
//      // TODO: put generic object-factory here that creates an object and populates it with attributes and elements
//      Map<String,String> queryParams = MetadataUtil.parseQueryParameters(request.getQueryString());
//      ...;
//      DigitalObject dobj = repo.createDigitalObject(null);
//      DataElement content = dobj.getDataElement(DOConstants.CONTENT_ELEMENT_ID);
//      content.write(req.getInputStream());
//      for(Map.Entry<String,String> item : queryParams.entrySet()) {
//          if(item.getKey().equals(DOConstants.MIME_TYPE_ATTRIBUTE) || item.getKey().equals(DOConstants.FILE_NAME_ATTRIBUTE)) {
//              content.setAttribute(item.getKey(),item.getValue());
//          }
//          else {
//              dobj.setAttribute(item.getKey(),item.getValue());
//          }
//      }
//      // MainController.newObjects.add(dobj);
//      resp.getWriter().println(dobj.getID());
//      resp.getWriter().close();
//      return null;
//
//      
//      
//      
//    }
    
    // Two private classes encapsulate data for Velocity access
    public static class AttributePair {
        String key;
        String val;

        public AttributePair(String a, String b) {
            key = a;
            val = b;
        }

        public String getKey(){
            return key;
        }

        public String getVal(){
            return val;
        }
    }

    public static class ElementType {
        String elementLink;
        ArrayList<AttributePair> attributes = new ArrayList<AttributePair>();

        public ElementType(String link) {
            elementLink = link;
        }

        public String getElementLink(){
            return elementLink;
        }

        public ArrayList<AttributePair> getAttributes(){
            return attributes;
        }
    }
    
    private static void clearAuthentication(HttpSession session) {
        session.removeAttribute(AUTHORIZATION_ATTRIBUTE);
        session.removeAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
        session.removeAttribute(CLAIMED_DELEGATE_GROUPS_SESSION_ATT);
        session.removeAttribute(VERIFIED_DELEGATE_GROUPS_SESSION_ATT);
        session.removeAttribute(EXPLICITLY_SET_DELEGATORS_SESSION_ATT);
        session.removeAttribute(GROUP_VERIFICATION_EXPIRATION_SESSION_ATT);
        //session.invalidate();
    }
    
    private void handleDelegators(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        if("GET".equals(request.getMethod())) {
            List<String> explicitlySetDelegators = (List<String>)session.getAttribute(EXPLICITLY_SET_DELEGATORS_SESSION_ATT); 
            if(explicitlySetDelegators==null) return;
            else {
                for(String delegator : explicitlySetDelegators) {
                    response.getWriter().println(delegator);
                }
            }
        }
        else if("DELETE".equals(request.getMethod())) {
            session.removeAttribute(EXPLICITLY_SET_DELEGATORS_SESSION_ATT);
            session.removeAttribute(CLAIMED_DELEGATE_GROUPS_SESSION_ATT);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        else if("PUT".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
            List<String> explicitlySetDelegators = new ArrayList<String>();
            String[] params1 = request.getParameterValues("delegators");
            String[] params2 = request.getParameterValues("delegator");
            if((params1!=null && params1.length>0) || (params2!=null && params2.length>0)) {
                if(params1!=null) {
                    for(String param : params1) {
                        for(String line : param.split("\n")) {
                            if(line.trim().isEmpty()) continue;
                            explicitlySetDelegators.add(line);
                        }
                    }
                }
                if(params2!=null) {
                    for(String param : params2) {
                        for(String line : param.split("\n")) {
                            if(line.trim().isEmpty()) continue;
                            explicitlySetDelegators.add(line);
                        }
                    }
                }
            }
            else {
                BufferedReader reader = request.getReader();
                String line;
                while((line = reader.readLine())!=null) {
                    if(line.trim().isEmpty()) continue;
                    explicitlySetDelegators.add(line);
                }
            }
            session.setAttribute(EXPLICITLY_SET_DELEGATORS_SESSION_ATT,explicitlySetDelegators);
            session.removeAttribute(CLAIMED_DELEGATE_GROUPS_SESSION_ATT);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
    
    /** Get the client's authentication, if any.  If the user has not authenticated 
     * this will return an anonymous auth object.  If the user is being authenticated
     * then this will provide an authentication method to the client (usually a 
     * basic http not-authorized response) and return null.  Callers should interpret
     * the null response as an indication that the request should not continue. */
    DOAuthentication getAuthentication(HttpServletRequest request, HttpServletResponse response) {
      DOAuthentication auth = (DOAuthentication)request.getAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
      HttpSession session = request.getSession(true);
      String sessionAuthHeader = (String)session.getAttribute(AUTHORIZATION_ATTRIBUTE);
      if(auth==null) {
        auth = (DOAuthentication)session.getAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
        if (auth != null) request.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
      }

      String method = request.getMethod();
      String path = request.getRequestURI();
      String baseURL = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort();

      String authHeader = request.getHeader("Authorization");
      
      X509Certificate certChain[] = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
      if(certChain!=null) {
          X509Certificate userCert = null;
          for (int i = 0; i < certChain.length; i++) {
            if(certChain[i]!=null) {
              userCert = certChain[i];
              break;
            }
          }
          if(userCert!=null) {
            // we can trust that this certificate is valid because the SSL layer trust manager verifies the
            // user certificate based on their handle
            DOAuthentication newAuth = new HTTPPKAuthentication(X509HSTrustManager.parseIdentityHandle(userCert));
            if(auth==null || !auth.getID().equals(newAuth.getID())) {
              clearAuthentication(session);
            }
            auth = newAuth;
            request.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
            session.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
            return auth;
          }
      }	else if(method.equals("GET") && (path.equals("/auth/logout") || path.equals("/logout/"))) {
        auth = null;
        request.removeAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
        clearAuthentication(session);
        try {
          //FIXME:  This can't be the best redirect destination
          response.sendRedirect(request.getContextPath());
        } catch (IOException e) {
          logger.error("Error sending redirect during logout",e);
        }
        return null;
      } else if(authHeader!=null && !authHeader.equals(sessionAuthHeader)) {
        // if authentication provided in the http headers, use it
        request.removeAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
        clearAuthentication(session);
        if(StringUtils.fieldIndex(authHeader, ' ', 0).equalsIgnoreCase("Basic")) {
          boolean verifiedAuth = false;

          byte[] userpassBytes = base64Decode(StringUtils.fieldIndex(authHeader, ' ', 1));
          int commaIndex = Util.indexOf(userpassBytes,(byte)':');
          if(commaIndex >= 0) {
            String user = Util.decodeString(Util.substring(userpassBytes,0,commaIndex));
            byte[] pass = Util.substring(userpassBytes,commaIndex+1);
            auth = new SecretKeyAuthentication(user,pass);

            String storedPassword = serverMain.getStoredPasswordForUser(user);
            if(storedPassword!=null) {
              verifiedAuth = storedPassword.equals(Util.decodeString(pass));
            }
            else {
              try {
                verifiedAuth = DOClient.getResolver().checkAuthentication(auth.toHandleAuth());
              } catch (Exception e) {
                logger.error("Error verifying authentication",e);
                verifiedAuth = false;
              }
            }
          }
          if(verifiedAuth) {
            request.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
            session.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
            session.setAttribute(AUTHORIZATION_ATTRIBUTE,authHeader);
            return auth;
          } else {
            // unable to verify authentication... try again
            response.setHeader("WWW-Authenticate", "Basic realm=\"hdl:" + serverMain.getServerID() + "\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try {
              response.getWriter().write("Authentication failed");
              response.getWriter().close();
            } catch (Exception e2) {}
            return null;
          }
        }
      } else if(path.equals("/auth/basic")) {
        request.removeAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE);
        clearAuthentication(session);
        response.setHeader("WWW-Authenticate", "Basic realm=\"hdl:" + serverMain.getServerID() + "\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        try {
          response.getWriter().write("Authentication failed");
          response.getWriter().close();
        } catch (Exception e) {}
        return null;
      }
      
      if(auth==null) {
        clearAuthentication(session);
        auth = PKAuthentication.getAnonymousAuth();
        request.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
        session.setAttribute(DO_AUTHENTICATION_REQ_ATTRIBUTE, auth);
      }
      return auth;
    }
    
    public static final String safeHTML(String str) {
        return StringUtils.cgiEscape(str);
    }

    private static final String objectHref(String objID) {
        String href = "/show?id=" + StringUtils.encodeURLComponent(objID);
        return "<a class=\"objref\" href=\"" + safeHTML(href) + "\" >"+safeHTML(objID)+"</a>";
    }

    private static final String elementHref(String objID, String elementID) {
        String href = "/get?id="+StringUtils.encodeURLComponent(objID) + (elementID==null ? "" : ("&el="+StringUtils.encodeURLComponent(elementID)));
        return "<a class=\"getref\" href=\"" + StringUtils.cgiEscape(href) +
        "\" >"+safeHTML(elementID)+"</a>";
    }

    private static final byte[] base64Decode(String s) {
        ArrayList<Byte> res = new ArrayList<Byte>(s.length()*3/4);
        int len = s.length();
        int pos = 0;
        int i = 0; 
        int acc = -1;
        while(true) {
            int b = -1;
            while (b<0) {
                if(i>=len) break;
                char ch = s.charAt(i);
                i++;
                if(ch>='A' && ch<='Z') b = ch - 'A';
                else if(ch>='a' && ch<='z') b = ch - 'a' + 26;
                else if(ch>='0' && ch<='9') b = ch - '0' + 52;
                else if(ch=='+') b = 62;
                else if(ch=='/') b = 63;
                else if(ch=='=') break;
            }
            if(b<0) {
                // if(acc>=0) res.add((byte)acc);
                break;
            }
            if(pos==0) {
                acc = b << 2;
                pos = 1;
            }
            else if(pos==1) {
                acc |= b >>> 4;
                res.add((byte)acc);
                acc = (b & 0xF) << 4;
                pos = 2;
            }
            else if(pos==2) {
                acc |= b >>> 2;
                res.add((byte)acc);
                acc = (b & 0x3) << 6;
                pos = 3;
            }
            else {
                acc |= b;
                res.add((byte)acc);
                acc = -1;
                pos = 0;
            }
        }
        int size = res.size();
        byte[] resArray = new byte[size];
        for(i = 0; i<size; i++) resArray[i] = res.get(i);
        return resArray;
    }

  
  private static class HTTPPKAuthentication
    extends PKAuthentication
  {
    
    HTTPPKAuthentication(String id) {
      super(id, null);
      super.setAutoRetrieveCredentials(false);
    }

    
    
  }
  
    public static class HTTPRepositoryInterface
    extends InternalRepository
    {
      private HttpServletRequest request;
      private String callerID;
      private Main serverMain;
      
      public HTTPRepositoryInterface(Main serverMain, ExecutorService execServ, HttpServletRequest request, String callerID) 
        throws DOException
      {
        super(serverMain, execServ);
        this.serverMain = serverMain;
        this.request = request;
        this.callerID = callerID;
      }
      
      protected DOServerOperationContext getOpContext(String objectID, 
                                                      String operationID, 
                                                      HeaderSet params) {
        HTTPOperationContext context =  new HTTPOperationContext(serverMain, request, 
                                                                 callerID, objectID, 
                                                                 operationID, params);
        boolean scanForDelegates = 
          serverMain.getConfigVal("auto_discover_delegation", "false").equalsIgnoreCase("true");
        context.setAutoDiscoverDelegates(scanForDelegates);
        return context;
      }
    }
    
    private static final String CLAIMED_DELEGATE_GROUPS_SESSION_ATT = "dop.claimed_delegate_groups";
    private static final String VERIFIED_DELEGATE_GROUPS_SESSION_ATT = "dop.verified_delegate_groups";
    private static final String EXPLICITLY_SET_DELEGATORS_SESSION_ATT = "dop.delegators";
    private static final String GROUP_VERIFICATION_EXPIRATION_SESSION_ATT = "dop.group_verification_expiration";

    private static class HTTPOperationContext
      extends DOServerOperationContext
    {
        private static DOClient anonymousDOClient = new DOClient(PKAuthentication.getAnonymousAuth());
        private static final String[] EMPTY_STRING_LIST = {};

        private final Main serverMain;
        private final HttpServletRequest httpRequest;
        private final HttpSession httpSession;

        private final String callerID;
        private final String operationID;
        private final String objectID;
        private final HeaderSet params;
        private boolean autoDiscoverDelegates = true;
      
      
        private StorageProxy storage;

        private HTTPOperationContext(Main serverMain, HttpServletRequest httpRequest, String callerID,
                String objectID, String operationID, HeaderSet params) {
            this.serverMain = serverMain;
            this.httpRequest = httpRequest;
            this.httpSession = httpRequest.getSession(true);
            this.callerID = callerID;
            this.operationID = operationID;
            this.objectID = objectID;
            this.params = params==null ? new HeaderSet() : params;
        }
      
        public void setAutoDiscoverDelegates(boolean discoverThem) {
          this.autoDiscoverDelegates = discoverThem;
        }

        public InetAddress getClientAddress() {
            try {
                return InetAddress.getByName(httpRequest.getRemoteAddr());
            } catch (Exception e) {
                return null;
            }
        }

        /** If the client authenticated with a "forwardable" authentication then this will be
         * used to generate a DOClient using that authentication.  For the time being, we will
         * only return an client with anonymous identity. */
        public DOClient getDOClientWithForwardedAuthentication(String clientID) {
            return anonymousDOClient;
        }

        // the caller's authentication is established before the operation is invoked
        public boolean authenticateCaller() { return true; }
      
      
        /** Get the list of groups in which the client claims membership */
        public String[] getCredentialIDs() {
          String[] claimedGroups = (String[])getConnectionMapping(CLAIMED_DELEGATE_GROUPS_SESSION_ATT);
          Long groupVerificationExpirationObj = ((Long)getConnectionMapping(GROUP_VERIFICATION_EXPIRATION_SESSION_ATT));
          long groupVerificationExpiration = groupVerificationExpirationObj==null ? Long.MIN_VALUE : groupVerificationExpirationObj.longValue();
          if(claimedGroups!=null && groupVerificationExpiration > System.currentTimeMillis()) {
            return claimedGroups;
          }
          List<String> explicitlySetDelegators = (List<String>)getConnectionMapping(EXPLICITLY_SET_DELEGATORS_SESSION_ATT);
          if(!autoDiscoverDelegates) {
            if(explicitlySetDelegators==null || explicitlySetDelegators.isEmpty()) {
              // no delegates have been explicitly set.  If we're not going to auto-discover delegates then
              // we can return right here
              return EMPTY_STRING_LIST;
            }
            // uncomment the following to prevent further resolution/discovery of more delegators
//            else {
//              return explicitlySetDelegators;
//            }
          }
          
          // if there are no specified delegators, start with ourselves
          if(explicitlySetDelegators==null) {
            explicitlySetDelegators = Collections.singletonList(callerID);
          }
          
          // let's resolve some delicious delegates
          logger.debug("Getting claimed groups for caller {}, expanding on {}",callerID,explicitlySetDelegators);
          try {
            List<String> groups = serverMain.getDelegationClient().allImplicitDelegators(callerID, explicitlySetDelegators);
            if(groups!=null) {
              claimedGroups = groups.toArray(new String[groups.size()]);
              if(logger.isDebugEnabled()) logger.debug(" found claimed groups: "+ Arrays.toString(claimedGroups));
            } else {
              logger.debug(" no groups found for {}",callerID);
            }
          } catch(Exception e) {
            logger.error("Error listing delegators: caller:"+callerID,e);
            claimedGroups = new String[0]; // cache a lack of claimed groups to avoid thrashing
          }
          
          setConnectionMapping(VERIFIED_DELEGATE_GROUPS_SESSION_ATT, null); // remove any verified group memberships
          setConnectionMapping(CLAIMED_DELEGATE_GROUPS_SESSION_ATT, claimedGroups);
          setConnectionMapping(GROUP_VERIFICATION_EXPIRATION_SESSION_ATT,Long.valueOf(System.currentTimeMillis() + 1000 * 60 * 20)); // re-verify all group memberships every 20 minutes
          return claimedGroups;
        }


        private static final String VERIFIED_KEY_VALUE = "verified";
        private static final String UNVERIFIED_KEY_VALUE = "UNverified";
        
        /** Return whether or not the caller can be verified (currently only through delegated authority, not x509) as
         *  a member of the given group. */
        public boolean authenticateCredential(String credentialID) {
          // check for a cached group verification flag
          HashMap<String,String> verifiedGroups = (HashMap<String,String>)httpSession.getAttribute(VERIFIED_DELEGATE_GROUPS_SESSION_ATT);
          if(verifiedGroups!=null && verifiedGroups.containsKey(credentialID)) {
            return VERIFIED_KEY_VALUE.equals(verifiedGroups.get(credentialID));
          }
          if(verifiedGroups==null) {
            verifiedGroups = new HashMap<String, String>();
            httpSession.setAttribute(VERIFIED_DELEGATE_GROUPS_SESSION_ATT, verifiedGroups);
          }
          
          logger.debug("Verifying group membership in {} for caller {}",credentialID,callerID);
          try {
            boolean isMember = serverMain.getDelegationClient().checkImplicitDelegation(callerID, Collections.singletonList(callerID), credentialID);
            logger.debug("  user IS a member of group {}",credentialID);
            verifiedGroups.put(credentialID, isMember ? VERIFIED_KEY_VALUE : UNVERIFIED_KEY_VALUE);
            return isMember;
          } catch (Exception e) {
            logger.error("Error verifying group membership, caller:"+callerID+", credentialID:"+credentialID,e);
            verifiedGroups.put(credentialID, UNVERIFIED_KEY_VALUE); // cache the failure so that we don't spin out of control if there is a network error
            return false;
          }
        }

        public String getCallerID() { return callerID; }

        public String getOperationID() { return operationID; }

        public String getTargetObjectID() { return objectID; }

        public String getServerID() { return serverMain.getServerID(); }

        public HeaderSet getOperationHeaders() { return params; }

        public synchronized StorageProxy getStorage() {
          if(storage!=null) return storage;
          HeaderSet txnMetadata = new HeaderSet();
          txnMetadata.addHeader("callerid", callerID);
          txnMetadata.addHeader("operationid", operationID);
          txnMetadata.addHeader("params", params);
          storage = new ConcreteStorageProxy(serverMain.getStorage(),
                                             serverMain.getServerID(),
                                             objectID, txnMetadata);
          return storage;
        }


        public void setConnectionMapping(Object mappingKey, Object mappingData) {
            // NOTE: HTTP mappings use the string value of the mappingKey, so be careful!
            if(mappingData==null) {
                httpSession.removeAttribute(String.valueOf(mappingKey));
            } else {
                httpSession.setAttribute(String.valueOf(mappingKey), mappingData);
            }
        }


        public Object getConnectionMapping(Object mappingKey) {
            // NOTE: HTTP mappings use the string value of the mappingKey, so be careful!
            return httpSession.getAttribute(String.valueOf(mappingKey));
        }


        /**
         * Performs the specified operation with the identity of the caller, or as 
         * the container repository if the forwarding operations are configured to use
         * the repository's own identity for forwarded operations.
         * If the serverID is null then the DO client that performs this request
         * will resolve the objectID to find the server.
         */
        public void performOperation(String serverID, String objectID,
                String operationID, HeaderSet params,
                java.io.InputStream input, java.io.OutputStream output)
        throws DOException 
        {
            HTTPOperationContext derivedCtx = new HTTPOperationContext(serverMain, httpRequest, callerID, 
                    objectID, operationID,
                    params);
            derivedCtx.setAutoDiscoverDelegates(autoDiscoverDelegates);
            try {
                serverMain.performOperation(derivedCtx, input, output);
            } catch (Exception e) {
                logger.error("Exception in HTTPOperationContext.performOperation",e);
                if(e instanceof DOException) throw (DOException)e;
                throw new DOException(DOException.SERVER_ERROR,
                        "Error invoking derived operation: "+e);
            }

        }

    }
}
