/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.knowbots.standardops;

import net.cnri.knowbots.lib.*;
import net.cnri.dobj.*;
import net.cnri.simplexml.*;
import net.handle.hdllib.Util;
import java.io.*;
import java.util.*;
import java.security.cert.*;

import java.util.zip.*;

/**
 * Operator that provides simple digital object operations, like saving and 
 * retrieving object data elements, certificates, etc.
 */
public class StandardOperations
  extends Knowbot
  implements DOOperation,
             DOConstants
{
  public static final String GRANTED_KEY_PREFIX = "do.granted_key";
  
  public static final String PARAM_ELEMENT_ID = "elementid";
  
  public static final String DATA_ELEMENT_PREFIX = "";
  public static final String OBJ_TYPES_ELEMENT = "obj_types";
  public static final String OBJ_CREDENTIALS_ELEMENT = "obj_credentials";
  
  private static final String DEFAULT_ELEMENT_ID = "DO_canonical";  
  private transient boolean keepRunning = false; 
  private transient CertificateFactory certFactory = null;
  private Hashtable myOperations = new Hashtable();
  private String operationKeys[] = {
    GET_DATA_OP_ID,
    STORE_DATA_OP_ID,
    CREATE_OBJ_OP_ID,
    DELETE_OBJ_OP_ID,
    DELETE_DATA_OP_ID,
    LIST_DATA_OP_ID,
    ADD_TYPE_OP_ID,
    REMOVE_TYPE_OP_ID,
    HAS_TYPE_OP_ID,
    LIST_OBJECTS_OP_ID,
    GET_CREDENTIALS_OP_ID,
    STORE_CREDENTIAL_OP_ID,
    DOES_OBJ_EXIST_OP_ID,
    GET_SERIALIZED_FORM_OP_ID,
    SET_ATTRIBUTES_OP_ID,
    GET_ATTRIBUTES_OP_ID,
    DEL_ATTRIBUTES_OP_ID,
    GRANT_KEY_OP_ID
  };
  private XParser xmlParser = new XParser();
  
  public StandardOperations() {
    for(int i=0; i<operationKeys.length; i++)
      myOperations.put(operationKeys[i], "");
  }

  private synchronized CertificateFactory getCertFactory()
    throws CertificateException
  {
    if(certFactory!=null) return certFactory;
    certFactory = CertificateFactory.getInstance("x509");
    return certFactory;
  }

  public boolean canHandleOperation(DOOperationContext context) {
    return myOperations.containsKey(context.getOperationID().toLowerCase());
  }

  /**
   * Returns a list of operations that this operator can perform
   * on the object identified by the DOOperationContext parameter.
   */
  public String[] listOperations(DOOperationContext context) {
    // since whether a user can perform a certain operation or not
    // is determined at run-time, we return all operations as possibilities
    if(context.getServerID().equals(context.getTargetObjectID())) {
      return new String[] {
        GET_DATA_OP_ID, STORE_DATA_OP_ID, DELETE_DATA_OP_ID, LIST_DATA_OP_ID,
        ADD_TYPE_OP_ID, REMOVE_TYPE_OP_ID, HAS_TYPE_OP_ID, CREATE_OBJ_OP_ID,
        LIST_OBJECTS_OP_ID, GET_CREDENTIALS_OP_ID,
        STORE_CREDENTIAL_OP_ID, GET_SERIALIZED_FORM_OP_ID, DOES_OBJ_EXIST_OP_ID,
        SET_ATTRIBUTES_OP_ID, GET_ATTRIBUTES_OP_ID, DEL_ATTRIBUTES_OP_ID,
        GRANT_KEY_OP_ID
      };
    } else {
      return new String[] {
        GET_DATA_OP_ID, STORE_DATA_OP_ID, DELETE_DATA_OP_ID, LIST_DATA_OP_ID,
        ADD_TYPE_OP_ID, REMOVE_TYPE_OP_ID, HAS_TYPE_OP_ID,
        DELETE_OBJ_OP_ID, GET_CREDENTIALS_OP_ID,
        STORE_CREDENTIAL_OP_ID, DOES_OBJ_EXIST_OP_ID, GET_SERIALIZED_FORM_OP_ID,
        SET_ATTRIBUTES_OP_ID, GET_ATTRIBUTES_OP_ID, DEL_ATTRIBUTES_OP_ID,
        GRANT_KEY_OP_ID
      };
    }
  }
  
  
  public void executeInContext() {
    keepRunning = true;

    System.err.println("Loading StandardOperations service agent v300");
    
    getContext().registerService("Standard DO Operations", null, this);
    
    while(keepRunning) {
      try { Thread.sleep(2000); } catch (Exception e) {}
    }
  }

  /**
   * Performs the given operation (which this object has advertised that it
   * can handle) which consists of reading input (if any is expected) from the
   * given InputStream and writing the output of the operation (if any) to the
   * OutputStream.  This method should *always* close the input and output streams
   * when finished with them.  If there are any errors in the input, the error
   * message must be communicated on the OutputStream since all errors must be
   * at the application level.  Any exceptions thrown by this method will *not*
   * be communicated to the caller and are therefore not acceptable.
   */
  public void performOperation(DOOperationContext context, InputStream in, OutputStream out) {
    try {
      String operation = context.getOperationID();
      
      if(operation.equalsIgnoreCase(DOES_OBJ_EXIST_OP_ID)) {
        verifyObject(context, in, out);
        return;
      }
      
      if(!context.getStorage().doesObjectExist()) {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "error");
        response.addHeader("message", "Object '"+context.getTargetObjectID()+"' does not exist");
        response.addHeader("code", DOException.NO_SUCH_OBJECT_ERROR);
        response.writeHeaders(out);
        out.close();
        return;
      }
      
      if(operation.equalsIgnoreCase(GET_DATA_OP_ID)) {
        retrieveElement(context, in, out);
      } else if(operation.equalsIgnoreCase(STORE_DATA_OP_ID)) {
        storeElement(context, in, out);
      } else if(operation.equalsIgnoreCase(GET_SERIALIZED_FORM_OP_ID)) {
        getSerializedForm(context, in, out);
      } else if(operation.equalsIgnoreCase(CREATE_OBJ_OP_ID)) {
        createObject(context, in, out);
      } else if(operation.equalsIgnoreCase(DELETE_OBJ_OP_ID)) {
        deleteObject(context, in, out);
      } else if(operation.equalsIgnoreCase(ADD_TYPE_OP_ID)) {
        addType(context, in, out);
      } else if(operation.equalsIgnoreCase(REMOVE_TYPE_OP_ID)) {
        removeType(context, in, out);
      } else if(operation.equalsIgnoreCase(HAS_TYPE_OP_ID)) {
        checkHasType(context, in, out);
      } else if(operation.equalsIgnoreCase(DELETE_DATA_OP_ID)) {
        deleteElement(context, in, out);
      } else if(operation.equalsIgnoreCase(LIST_DATA_OP_ID)) {
        listElements(context, in, out);
      } else if(operation.equalsIgnoreCase(LIST_OBJECTS_OP_ID)) {
        listObjects(context, in, out);
      } else if(operation.equalsIgnoreCase(GET_CREDENTIALS_OP_ID)) {
        getCredentials(context, in, out);
      } else if(operation.equalsIgnoreCase(STORE_CREDENTIAL_OP_ID)) {
        storeCredential(context, in, out);
      } else if(operation.equalsIgnoreCase(LIST_TYPES_OP_ID)) {
        listTypes(context, in, out);
      } else if(operation.equalsIgnoreCase(SET_ATTRIBUTES_OP_ID)) {
        setAttributes(context, in, out);
      } else if(operation.equalsIgnoreCase(GET_ATTRIBUTES_OP_ID)) {
        getAttributes(context, in, out);
      } else if(operation.equalsIgnoreCase(DEL_ATTRIBUTES_OP_ID)) {
        delAttributes(context, in, out);
      } else if(operation.equalsIgnoreCase(GRANT_KEY_OP_ID)) {
        grantKey(context, in, out);
      } else {
        sendErrorResponse(out, "Operation '"+operation+"' not implemented!",
                          DOException.OPERATION_NOT_AVAILABLE);
        return;
      }
      
    } catch (Exception e) {
      e.printStackTrace(System.err);
    } finally {
      try { in.close(); } catch (Exception e) {}
      try { out.close(); } catch (Exception e) {}
    }
  }
  
  
  /** Set an attribute for either an object or a data element.  If forData is true
    * then the attribute applies to a specific data element. */
  private void setAttributes(DOOperationContext context,
                             InputStream in, OutputStream out) 
    throws Exception
  {
    try {
      HeaderSet params = context.getOperationHeaders();
      StorageProxy storage = context.getStorage();
      String elementID = params.getStringHeader(PARAM_ELEMENT_ID, null);
      if(elementID!=null) {
        storage.setElementAttributes(elementID, 
                                     params.getHeaderSubset(PARAM_ATTRIBUTES));
      } else {
        storage.setAttributes(params.getHeaderSubset(PARAM_ATTRIBUTES));
      }
      sendSuccessResponse(out);
    } catch (DOException e) {
      sendErrorResponse(out, e.getMessage(), e.getErrorCode());
      return;
    } catch (Exception e) {
      sendErrorResponse(out, String.valueOf(e), DOException.SERVER_ERROR);
      return;
    }
  }
  
  
  /** Remove attributes from the object or a data element thereof.  */
  private void delAttributes(DOOperationContext context,
                             InputStream in, OutputStream out) 
    throws Exception
  {
    try {
      HeaderSet params = context.getOperationHeaders();
      StorageProxy storage = context.getStorage();
      String elementID = params.getStringHeader(PARAM_ELEMENT_ID, null);
      String keys[] = params.getStringArrayHeader(PARAM_ATTRIBUTES, null);
      if(keys==null || keys.length<=0) {
        sendErrorResponse(out, "no keys included in delete-attributes operation",
                          DOException.APPLICATION_ERROR);
        return;
      }
      if(elementID!=null) {
        storage.deleteElementAttributes(elementID, keys);
      } else {
        storage.deleteAttributes(keys);
      }
      sendSuccessResponse(out);
    } catch (DOException e) {
      sendErrorResponse(out, e.getMessage(), e.getErrorCode());
      return;
    } catch (Exception e) {
      sendErrorResponse(out, String.valueOf(e), DOException.SERVER_ERROR);
      return;
    }
  }
  
  
  /** List the available attribute keys for either an object or a data element. 
    * If forData is true then the attribute keys apply to a specific data element. */
  private void getAttributes(DOOperationContext context, 
                             InputStream in, OutputStream out) 
    throws Exception
  {
    String attkeys[] = null;
    HeaderSet params = context.getOperationHeaders();
    StorageProxy storage = context.getStorage();
    String elementID = params.getStringHeader(PARAM_ELEMENT_ID, null);

    if(elementID!=null) {
      // we are returning attributes for a specific element...
      try {
        HeaderSet atts = new HeaderSet(DOConstants.ELEMENT_ATTS_MSGTYPE);
        atts.addHeader(PARAM_ELEMENT_ID, elementID);
        atts.addHeader(PARAM_ATTRIBUTES, 
                       storage.getElementAttributes(elementID, null));
        sendSuccessResponse(out);
        atts.writeHeaders(out);
      } catch (DOException e) {
        sendErrorResponse(out, e.getMessage(), e.getErrorCode());
      } catch (Exception e) {
        sendErrorResponse(out, String.valueOf(e), DOException.SERVER_ERROR);
      }
    } else {
      // we are returning attributes for the entire object
      try {
        HeaderSet objAtts = storage.getAttributes(null);
        sendSuccessResponse(out);
        
        // write the response message with the attributes for the object
        HeaderSet objMsg = new HeaderSet(OBJECT_ATTS_MSGTYPE);
        objMsg.addHeader(PARAM_ATTRIBUTES, objAtts);
        objMsg.writeHeaders(out);
        
        // write the attributes for each element
        HeaderSet elMsg = new HeaderSet(ELEMENT_ATTS_MSGTYPE);
        HeaderSet elAtts = new HeaderSet();
        for(Enumeration en=storage.listDataElements(); en.hasMoreElements(); ) {
          elMsg.removeAllHeaders();
          elAtts.removeAllHeaders();
          String elID = (String)en.nextElement();
          if(storage.doesDataElementExist(elID)) {
            elMsg.addHeader(PARAM_ELEMENT_ID, elID);
            elMsg.addHeader(PARAM_ATTRIBUTES, storage.getElementAttributes(elID, elAtts));
            elMsg.writeHeaders(out);
          }
        }
      } catch (DOException e) {
        e.printStackTrace(System.err);
        sendErrorResponse(out, e.getMessage(), e.getErrorCode());
      } catch (Exception e) {
        e.printStackTrace(System.err);
        sendErrorResponse(out, String.valueOf(e), DOException.SERVER_ERROR);
      }
    }
    out.close();
  }
  
  
  private String[] listTypes(DOOperationContext context) {
    InputStream in = null;
    try {
      StorageProxy storage = context.getStorage();
      if(!storage.doesDataElementExist(OBJ_TYPES_ELEMENT))
        return null;
      in = storage.getDataElement(OBJ_TYPES_ELEMENT);
      if(in==null) return null;
      BufferedReader rdr = new BufferedReader(new InputStreamReader(in, "UTF8"));
      Vector lines = new Vector();
      String line;
      while((line=rdr.readLine())!=null) {
        line = line.trim();
        if(line.length()>0)
          lines.addElement(line.trim());
      }
      String types[] = new String[lines.size()];
      lines.copyInto(types);
      return types;
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return null;
    } finally {
      if(in!=null) try { in.close(); } catch (Throwable t) {} 
    }
  }
  
  private void setTypes(DOOperationContext context, String newTypes[])
    throws Exception
  {
    InputStream in = null;
    try {
      StorageProxy storage = context.getStorage();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      for(int i=0; newTypes!=null && i<newTypes.length; i++) {
        if(newTypes[i]==null) continue;
        bout.write(newTypes[i].getBytes("UTF8"));
        bout.write((byte)'\n');
      }
      storage.storeDataElement(OBJ_TYPES_ELEMENT, new ByteArrayInputStream(bout.toByteArray()));
    } finally {
      if(in!=null) try { in.close(); } catch (Throwable t) {} 
    }
  }
  
  /**
   * Sends a response header to the client with result=true if the given object
   * has the type specified in the "type" request parameter.  Otherwise sends
   * a response header with result=false.
   */
  private void checkHasType(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    String typeToCheck = context.getOperationHeaders().getStringHeader("type", null);

    String types[] = listTypes(context);
    sendSuccessResponse(out);
    
    for(int i=0; types!=null && i<types.length; i++) {
      if(types[i].equals(typeToCheck)) {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("result", "true");
        response.writeHeaders(out);
        return;
      }
    }
    
    HeaderSet response = new HeaderSet("response");
    response.addHeader("result", "false");
    response.writeHeaders(out);
    return;
  }
  
  /**
   * Sends a newline delimited list of types that apply to the object
   */
  private void listTypes(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    String types[] = null;
    try {
      types = listTypes(context);
    } catch (Exception e) {
      sendErrorResponse(out, "Error listing types: "+e, DOException.STORAGE_ERROR);
      return;
    }
    sendSuccessResponse(out);
      
    for(int i=0; types!=null && i<types.length; i++) {
      if(types[i]!=null) {
        out.write(DOConnection.encodeUTF8(types[i]));
        out.write((byte)'\n');
      }
    }
  }
  
  /**
   * Removes the type given in the "type" request parameter.
   * Sends a response header to the client with status=success if the given
   * type was removed from (or never applied to) the object.
   */
  private void removeType(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    String typeToCheck = context.getOperationHeaders().getStringHeader("type", null);

    String types[] = listTypes(context);
    for(int i=0; types!=null && i<types.length; i++) {
      if(types[i].equals(typeToCheck)) {
        types[i]=null;
      }
    }

    try {
      setTypes(context, types);
    } catch (DOException e) { 
      e.printStackTrace(System.err);
      sendErrorResponse(out, "Error saving type set: "+e, DOException.STORAGE_ERROR);
      return;
    }
    
    sendSuccessResponse(out);
  }

  private void addType(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    String typeToAdd = context.getOperationHeaders().getStringHeader("type", null);

    String types[] = listTypes(context);
    boolean alreadyHaveIt = false;
    for(int i=0; types!=null && i<types.length; i++) {
      if(types[i].equals(typeToAdd)) {
        alreadyHaveIt = true;
      }
    }
    if(!alreadyHaveIt) {
      try {
        String newTypes[] = new String[types==null ? 1 : types.length+1];
        if(types!=null)
          System.arraycopy(types, 0, newTypes, 1, types.length);
        newTypes[0] = typeToAdd;
        
        setTypes(context, newTypes);
      } catch (DOException e) {
        e.printStackTrace(System.err);
        sendErrorResponse(out, "Error saving type set: "+e, DOException.STORAGE_ERROR);
        return;
      }
    }
    
    sendSuccessResponse(out);
  }
  
  /** Store the entries in the given zip file as elements in the object accessible
    * from the given storage proxy */
  private void loadObjectFromZip(StorageProxy storage, InputStream in)
    throws Exception
  {
    ZipInputStream zin = new ZipInputStream(in);
    ZipEntry entry = null;
    byte buf[] = new byte[1000];
    while((entry=zin.getNextEntry())!=null) {
      storage.storeDataElement(entry.getName(), zin);
    }
  }
  
  
  private void getSerializedForm(DOOperationContext context, InputStream in, OutputStream out) 
    throws Exception
  {
    HeaderSet request = context.getOperationHeaders();
    String encoding = request.getStringHeader("encoding", "");
    
    sendSuccessResponse(out);
    context.getStorage().serializeObject(encoding, out);
  }
  
  
  private void createObject(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    HeaderSet response = new HeaderSet("response");
    HeaderSet request = context.getOperationHeaders();
    String newObjectID = request.getStringHeader("objectid", null);
    String newObjectMethod = request.getStringHeader("initencoding", "empty");
    
    // just store the object
    DOException error = null;
    try {
      newObjectID = context.getStorage().createObject(newObjectID, 
                                                      request.getStringHeader("objectname", null));
    } catch (DOException e) {
      error = e;
    }
    
    if(error==null) {
      sendSuccessResponse(out);
      
      // fill the object from the output stream
      if(newObjectMethod.equalsIgnoreCase("empty")) {
        // the new object should be empty - nothing to do
      } else if(newObjectMethod.equalsIgnoreCase("basiczip")) {
        try {
          loadObjectFromZip(context.getStorage().getObjectAccessor(newObjectID), in);
          response.addHeader("fromzipstatus", true);
        } catch (Exception e) {
          response.addHeader("status", "error");
          response.addHeader("message", String.valueOf(e));
        }
      }
      response.addHeader("objectid", newObjectID);
      response.writeHeaders(out);
    } else {
      sendErrorResponse(out, String.valueOf(error), DOException.STORAGE_ERROR);
      
      // this part is probably unnecessary and should be removed
      response.addHeader("status", "error");
      response.addHeader("message", String.valueOf(error));
      response.writeHeaders(out);
    }
  }
  
  private void deleteObject(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    if(context.getTargetObjectID().equals(context.getServerID())) {
      sendErrorResponse(out, "Cannot delete server object",
                        DOException.APPLICATION_ERROR);
      return;
    }
    
    HeaderSet response = new HeaderSet("response");
    HeaderSet request = context.getOperationHeaders();
    try {
      context.getStorage().deleteObject();
      sendSuccessResponse(out);
    } catch (DOException e) {
      sendErrorResponse(out, String.valueOf(e), DOException.STORAGE_ERROR);
    }
  }
  
  private void verifyObject(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    HeaderSet request = context.getOperationHeaders();
    boolean answer = false;
    DOException error = null;
    try {
      answer = context.getStorage().doesObjectExist();
    } catch (DOException e) {
      error = e;
    }
    
    if(error==null) {
      sendSuccessResponse(out);
      HeaderSet response = new HeaderSet("response");
      response.addHeader("result", answer);
      response.writeHeaders(out);
    } else {
      sendErrorResponse(out, String.valueOf(error), DOException.STORAGE_ERROR);
    }
  }
  
  private void storeElement(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    HeaderSet response = new HeaderSet("response");
    response.addHeader("status", "pending");
    response.writeHeaders(out);
    out.flush();
    
    // just store the object
    HeaderSet params = context.getOperationHeaders();
    StorageProxy storage = context.getStorage();
    Exception error = null;
    try {
      boolean append = params.getBooleanHeader("append", false);
      String elementID = params.getStringHeader(PARAM_ELEMENT_ID, DEFAULT_ELEMENT_ID);
      if(append) 
        storage.appendDataElement(DATA_ELEMENT_PREFIX + elementID, in);
      else
        storage.storeDataElement(DATA_ELEMENT_PREFIX + elementID, in);
    } catch (Exception e) {
      error = e;
    }
    
    if(error==null) {
      sendSuccessResponse(out);
    } else {
      sendErrorResponse(out, String.valueOf(error), DOException.STORAGE_ERROR);
    }
  }
  
  private void deleteElement(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    Exception error = null;
    try {
      String elementID = context.getOperationHeaders().getStringHeader(PARAM_ELEMENT_ID, null);
      if(elementID!=null) {
        context.getStorage().deleteDataElement(DATA_ELEMENT_PREFIX + elementID);
      } else {
        error = new Exception("Request was missing an elementid parameter");
      }
    } catch (Exception e) {
      error = e;
    }
    
    if(error==null) {
      sendSuccessResponse(out);
    } else {
      sendErrorResponse(out, String.valueOf(error), DOException.STORAGE_ERROR);
    }
  }
  
  private void retrieveElement(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    // just get the resources and send it
    InputStream objStream = null;
    DOException errorMsg = null;
    try {
      String elementID = context.getOperationHeaders().
        getStringHeader(PARAM_ELEMENT_ID, DEFAULT_ELEMENT_ID);
      
      objStream = context.getStorage().getDataElement(DATA_ELEMENT_PREFIX + elementID);
    } catch (DOException e) {
      errorMsg = e;
    }
    
    if(errorMsg!=null ) {
      sendErrorResponse(out, String.valueOf(errorMsg), DOException.STORAGE_ERROR);
    } else if(objStream==null) {
      sendErrorResponse(out, "Data element not found", DOException.STORAGE_ERROR);
      out.close();
    } else {
      // the object was found, write a success response header
      sendSuccessResponse(out);
      
      byte buf[] = new byte[2048];
      int r;
      while((r=objStream.read(buf, 0, buf.length))>=0) {
        out.write(buf, 0, r);
      }
      out.flush();
      out.close();
    }
  }

  
  private void listElements(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    in.close();
    
    sendSuccessResponse(out);
    
    Enumeration elementEnum = context.getStorage().listDataElements();
    HeaderSet elementInfo = new HeaderSet();
    for(; elementEnum!=null && elementEnum.hasMoreElements(); ) {
      elementInfo.removeAllHeaders();
      elementInfo.addHeader(PARAM_ELEMENT_ID, String.valueOf(elementEnum.nextElement()));
      elementInfo.writeHeaders(out);
    }
    out.flush();
    out.close();
  }
  
  
  private void listObjects(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    in.close();
    
    sendSuccessResponse(out);
    HeaderSet info = new HeaderSet();
    Enumeration elementEnum = context.getStorage().listObjects();
    for(; elementEnum!=null && elementEnum.hasMoreElements(); ) {
      info.removeAllHeaders();
      info.addHeader("objectid", String.valueOf(elementEnum.nextElement()));
      info.writeHeaders(out);
    }
    out.flush();
    out.close();
  }

  private void getCredentials(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    in.close();
    
    StorageProxy storage = context.getStorage();
    
    //storage.lockObject(); // TODO:  uncomment this when object locking is implemented
    
    // read the credentials
    Vector certs = new Vector();
    try {
      InputStream credIn = storage.getDataElement(OBJ_CREDENTIALS_ELEMENT);
      if(credIn!=null) {
        XTag xmlCertList = xmlParser.parse(new InputStreamReader(credIn, "UTF8"),
                                           false);
        for(int i=0; i<xmlCertList.getSubTagCount(); i++) {
          XTag subtag = xmlCertList.getSubTag(i);
          if(subtag.getName().equalsIgnoreCase("certificate")) {
            X509Certificate cert = null;            
            try {
              InputStream certIn =
                new ByteArrayInputStream(Util.encodeHexString(subtag.getStrSubTag("x509", "")));
              cert = (X509Certificate)getCertFactory().generateCertificate(certIn);
              cert.checkValidity();
              certs.addElement(cert);
            } catch (Exception e) {
              e.printStackTrace(System.err);
              continue;
            }
          }
        }
      }      
    } catch (Exception e) {
      sendErrorResponse(out, "Unable to read certificates", DOException.STORAGE_ERROR);
      return;
    } finally {
      // unlock the object or the credential element
    }
    sendSuccessResponse(out);

    XTag pubXMLCertList = new XTag("credentials");
    for(int i=0; i<certs.size(); i++) {
      Certificate cert = (Certificate)certs.elementAt(i);
      pubXMLCertList.addSubTag(new XTag("certificate",
                                        Util.decodeHexString(cert.getEncoded(), false)));
    }
    out.write(Util.encodeString(pubXMLCertList.toString()));
    out.flush();
  }

  private void storeCredential(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    sendSuccessResponse(out);
    
    // read the credential from the input stream
    X509Certificate newCert = null;
    try {
      newCert = (X509Certificate)getCertFactory().generateCertificate(in);
    } catch (Exception e) {
      sendErrorResponse(out, "Error reading input credential", DOException.CRYPTO_ERROR);
      return;
    }

    // make sure the credential is valie
    try {
      newCert.checkValidity();
    } catch (Exception e) {
      sendErrorResponse(out, "Credential is not valid", DOException.CRYPTO_ERROR);
      return;
    }

    StorageProxy storage = context.getStorage();
    try {
      //storage.lockObject(); // TODO:  uncomment this when object locking is implemented
      
      // load the current certificate list
      XTag xmlCertList = null;
      InputStream credIn = storage.getDataElement(OBJ_CREDENTIALS_ELEMENT);
      if(credIn!=null) {
        xmlCertList = xmlParser.parse(new InputStreamReader(credIn, "UTF8"), false);
        
        for(int i=xmlCertList.getSubTagCount()-1; i>=0; i--) {
          XTag subtag = xmlCertList.getSubTag(i);
          if(subtag.getName().equalsIgnoreCase("certificate")) {
            X509Certificate cert = null;            
            try {
              InputStream certIn =
                new ByteArrayInputStream(Util.encodeHexString(subtag.getStrSubTag("x509", "")));
              cert = (X509Certificate)getCertFactory().generateCertificate(certIn);
              cert.checkValidity();
            } catch (Exception e) {
              System.err.println("problem with certificate: "+cert+"; error="+e+"; removing from list");
              xmlCertList.removeSubTag(i);
            }
          }
        }
      }

      if(xmlCertList==null) {
        xmlCertList = new XTag("docerts");
      }
        
      XTag newCertTag = new XTag("certificate");
      newCertTag.addSubTag(new XTag("x509",
                                    Util.decodeHexString(newCert.getEncoded(), false)));
      
      xmlCertList.addSubTag(newCertTag);
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      xmlCertList.write(bout);
      
      byte certBytes[] = bout.toByteArray();
      
      storage.storeDataElement(OBJ_CREDENTIALS_ELEMENT,
                               new ByteArrayInputStream(certBytes));
    } catch (Exception e) {
      sendErrorResponse(out, "Unable to update cert list: "+e,
                        DOException.STORAGE_ERROR);
    }

    try {
      sendSuccessResponse(out);
    } catch (Exception e) {}
  }

  
  private void grantKey(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
  {
    try {
      sendSuccessResponse(out);
    } catch (Exception e) {}
    
    // generate a new, unique element for the new keys
    String newElementName = null;
    StorageProxy storage = context.getStorage();
    do {
      newElementName = GRANTED_KEY_PREFIX + "."+System.currentTimeMillis();
    } while(storage.doesDataElementExist(newElementName));
    storage.storeDataElement(newElementName, in);
    
    try {
      sendSuccessResponse(out);
    } catch (Exception e) {}
  }
  
  
  private void sendErrorResponse(OutputStream out, String msg, int code)
    throws IOException
  {
    HeaderSet response = new HeaderSet("response");
    response.addHeader("status", "error");
    if(msg!=null)
      response.addHeader("message", msg);
    if(code>=0)
      response.addHeader("code", code);
    response.writeHeaders(out);
    out.flush();
  }
  
  private void sendSuccessResponse(OutputStream out)
    throws IOException
  {
    HeaderSet response = new HeaderSet("response");
    response.addHeader("status", "success");
    response.writeHeaders(out);
    out.flush();
  }
  
}


