/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.web.VelocityUtil;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

import net.handle.hdllib.*;
import org.apache.velocity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.Session;
import javax.mail.internet.*;
import javax.servlet.http.*;
import javax.servlet.*;

public class UserAccountServlet extends HttpServlet {
  static final Logger logger = LoggerFactory.getLogger(UserAccountServlet.class);
  
  public static final String USER_MANAGEMENT_PATH_PREFIX = "/usermanagement/";
  public static final String SIGNUP_ATT_PREFIX = "signup_att_";
  
  public static final String NEW_USER_ACTION = "newuser";
  
  private File mailPropertiesFile;
  private Main serverMain;
  private final ExecutorService execServ;
  private HTTPConnectionHandler httpConnectionHandler;
  
  public UserAccountServlet(Main main, ExecutorService execServ, HTTPConnectionHandler httpConnectionHandler) {
    super();
    this.serverMain = main;
    this.execServ = execServ;
    this.httpConnectionHandler = httpConnectionHandler;
    this.mailPropertiesFile = new File(main.getBaseFolder(), "mail.properties");
  }

  /**
   * Create and help manage user account objects.  User account objects are special because some operations
   * (reset password, create-user-object) cannot be performed with the identity of the client requesting the
   * operation.
   *
   * 
   * 
   * To create a new user account:
   * <form method="POST" action="/usermanagement/" enctype="multipart/form-data">
   *   <input type="hidden" name="template" value="template_to_show_next.html" />
   *   <input type="hidden" size="40" name="action"  value="newuser" />
   *   Email: <input type="text" size="40" name="email" />
   *   Name: <input type="text" size="40" name="name" />
   *   (Currently disabled.. new password will be emailed: Initial Password: <input type="password" size="40" name="password" />)
   *   (Currently disabled.. new password will be emailed: Confirm Password: <input type="password" size="40" name="passwordconfirm" />)
   * </form>
   * Note that the request cannot specify any object attributes or elements directly.  This is to prevent
   * anonymous clients from creating accounts and setting the "hdl.HS_SECKEY" or one of the 'owner_attributes'
   * attributes to refer to another user.  These accounts should not be 'owned' by anyone else and should only
   * be self-editable once the user has confirmed the registration by receiving their initial password via email.
   * 
   * The server's configuration should include the following values which map signup fields to DO attributes
   * signup_att_email = "<insert-prefix-here>/att/email"
   * signup_att_ssn = "<insert-prefix-here>/att/ssn"
   * 
   * 
   * To reset a password by sending an email with a unique reset-password link:
   * <form method="POST" action="/usermanagement/" enctype="multipart/form-data">
   *   <input type="hidden" name="template" value="template_to_show_next.html" />
   *   Object ID: <input type="text" name="id" value="$object.id" /> (set type="hidden" for most forms)
   *   attribute1: <input type="text" size="40" name="att.attribute1" value="$!object.get("att:attribute1")" />
   *   attribute2: <input type="text" size="40" name="att.attribute2" value="$!object.get("att:attribute2")" />
   *   attribute3: <input type="text" size="40" name="att.attribute3" value="$!object.get("att:attribute3")" />
   *   element att1: <input type="text" size="40" name="elatt.upload_element$mimetype" value="image/png" />
   *   upload_element: <input type="file" name="upload_element" size="40" />
   * </form>
   * 
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String requestURI = request.getRequestURI();
    logger.debug("got URI in usermanagement servlet: "+requestURI);
    

    //    if(request.getQueryString()!=null) {
    //      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    //      return;
    //    }
//    if(request.getHeader("Content-Encoding")!=null || request.getHeader("Content-Range")!=null) {
//      response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
//      return;
//    }
//    
    if(!request.getMethod().equals("POST")) { // should never be called from within doPost()... duh
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      response.setHeader("Allow", "POST");
      return;
    }
    
    String action = request.getParameter("action");
    if(action==null || action.trim().length()<=0) action = NEW_USER_ACTION;
    else action = action.trim();
    
    String redirect = null;
    String template = null;
    
    if(action.equals(NEW_USER_ACTION)) {
      String emailValue = request.getParameter("email");
      String nameValue = request.getParameter("name");
      template = request.getParameter("template");
      redirect = request.getParameter("redirect");
      
      // first, we search for any object with the same key attributes
      
      HeaderSet objAtts = new HeaderSet();
      for(Iterator keyIterator = serverMain.getConfigKeys(); keyIterator.hasNext(); ) {
        String configKey = (String)keyIterator.next();
        if(!configKey.startsWith(SIGNUP_ATT_PREFIX)) continue;
        
        String configVal = serverMain.getConfigVal(configKey);
        configKey = configKey.substring(SIGNUP_ATT_PREFIX.length());
        
        String paramVal = request.getParameter(configKey);
        if(paramVal!=null) {
          objAtts.addHeader(configVal, paramVal);
        }
      }
      
      Repository repo = new HTTPRepositoryInterface(serverMain, execServ, request, serverMain.getServerID());
      // make sure all of the submitted object attributes are unique
//      for(HeaderItem att : objAtts) {
//        System.err.println("checking uniqueness of attribute "+att);
//        // search for existing objects with the same attributes... which must be unique
//        Repository.QueryResults results = repo.search("objatt_"+att.getName()+":"+att.getValue().replaceAll(" ","+"),
//                                                      null /* returnedFields */,
//                                                      null /* sortFields */,
//                                                      null /* sortOrder */, 5, 0);
//        for(Repository.CloseableIterator<HeaderSet> resultIter = results.getIterator(); resultIter.hasNext(); ) {
//          // if there are any results at all then the object already exists
//          results.getIterator().close();
//          response.setStatus(HttpServletResponse.SC_OK);
//          response.getWriter().write("<html><body>Error: account with attribute "+att.getName()+": "+
//                                     att.getValue()+" already exists</body></html>");
//          return;
//        }
//      }
      
      byte passwd[] = new byte[13];
      new SecureRandom().nextBytes(passwd);
      String initialPassword = Util.decodeHexString(passwd, false);
      // add a new unique password
      objAtts.addHeader(DOStorageResolver.HANDLE_VALUE_ATTRIBUTE_PREFIX + Util.decodeString(Common.STD_TYPE_HSSECKEY),
                        initialPassword);
      
      DigitalObject newUserObject = repo.createDigitalObject(null);
      newUserObject.setAttributes(objAtts);
      
      HashMap emailInfo = new HashMap();
      emailInfo.put("initial_password", initialPassword);
      emailInfo.put("signin_link", new URL(new URL(request.getRequestURL().toString()), "/login.html").toString());
      emailInfo.put("user_id", newUserObject.getID());
      emailInfo.put("repo_id", serverMain.getServerID());
      
      
      
      try {
        sendTemplateMessage(emailValue, "Account Registration", "signup_email.txt", emailInfo);
      } catch (Exception e) {
        logger.error("Error sending email",e);
        newUserObject.deleteObject();
        
        Template t = null;
        try {
          t = httpConnectionHandler.getTemplate("error.html");
        } catch (Exception e2) { }
        
        if(t==null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          response.setContentType(HTTPConnectionHandler.HTML);
          response.getWriter().println("<html>");
          response.getWriter().println("<head><title>Signup Error</title></head>");
          response.getWriter().println("<body><p>There was an error signing up: no template was found "+
                                       "(server configuration issue)</p></body></html>");
          return;
        }        
        
        response.setCharacterEncoding("UTF-8");
        HashMap errorInfo = new HashMap();
        errorInfo.put("message", "Error sending email: "+e);
        t.merge(new VelocityContext(errorInfo), response.getWriter());
        return;
      }
      
      if(template==null) template = "thanks.html";
    }
    
    // send them to a thank-you-for-signing-up page
    if(redirect!=null) {
      response.sendRedirect(redirect);
    } else if(template!=null) {
      response.sendRedirect("/?"+HTTPConnectionHandler.TEMPLATE_PARAM+"="+VelocityUtil.encodeURLComponent(template));
    } else {
      response.sendRedirect("/");
    }
  }
  
  
  private void sendTemplateMessage(String toAddress, 
                                   String subject,
                                   String templateName, 
                                   Map templateInfo) throws Exception {
    Template template = httpConnectionHandler.getTemplate(templateName);
    String msgString;
    if(template!=null) {
      StringWriter msgOut = new StringWriter();
      template.merge(new VelocityContext(templateInfo), msgOut);
      msgString = msgOut.toString();
    } else {
      msgString = "Unable to locate template " + templateName +
                  "\nHere is the raw information:\n "+templateInfo;
    }
    
    MimeMultipart multipart = new MimeMultipart();
    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setContent(msgString, "text/plain");
    multipart.addBodyPart(textPart);
    
    // Setup properties and session
    Properties props = System.getProperties();//new Properties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.host", "localhost");
    props.put("mail.smtp.host.port", "25");
    
    if(mailPropertiesFile.exists()) {
      props.load(new InputStreamReader(new FileInputStream(mailPropertiesFile),"UTF-8"));
    }
    boolean debug = props.getProperty("mail.debug", "true").equals("true");
    Session session = Session.getDefaultInstance(props);
    
    //session.setDebug(true);
    Transport transport = session.getTransport();
    
    // -- Create a new message --
    Message msg = new MimeMessage(session);
    msg.setContent(multipart); 
    msg.setSubject(subject);
    msg.setFrom(new InternetAddress((String)props.get("mail.from_user")));
    msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress, false));
    msg.setHeader("X-Mailer", "DORepository");
    msg.setSentDate(new Date());
    
    
    if(debug && logger.isDebugEnabled()) {
      logger.debug("mail properties: (set mail.debug=false in mail.properties to hide this)");
      props.list(System.err);
      logger.debug("\nsending message to "+toAddress+" with subject "+subject);
      msg.writeTo(System.err);
      logger.debug("\nend of message");
    }
    
    transport.connect();
//    transport.connect(props.getProperty("mail.smtps.host"), 
//                      Integer.parseInt((String) props.get("mail.smtps.host.port")), 
//                      (String) props.get("mail.smtps.auth.user"), 
//                      (String) props.get("mail.smtps.auth.pwd"));
    
    transport.sendMessage(msg, msg.getRecipients(Message.RecipientType.TO));
    transport.close();
  }

}
  
