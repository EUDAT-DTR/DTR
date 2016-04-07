/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorizer objects are used to determine which operations are allowed
 * based on attributes of the request (such as caller ID, object ID,
 * operation ID, etc).
 *
 * Each object in the repository can have a data element called "internal.rights"
 * which contains a set of rules that determine who can access which operations.
 */
public class Authorizer {
  static final Logger logger = LoggerFactory.getLogger(Authorizer.class);
    
  public static final String RIGHTS_ELEMENT = "internal.rights";
  public static final String FORWARDING_ELEMENT = "internal.forwarding";
  public static final String REPO_DEFAULT_RIGHTS_ELEMENT = "internal.default_rights";
  public static final String DEFINED_RIGHTS_ELEMENT = "internal.defined_rights"; 
  public static final String USE_IDENTITY_KEY = "proxy_id";
  public static final String USE_REPOSITORY_ID = "repo";
    
  private static final Pattern ruleTypePattern = Pattern.compile("^([^:]*+):" + "\\s*+");
  private static final Pattern componentPattern = Pattern.compile("\\G([^\"]\\S*+|\"(?>[^\\\\\"]|\\\\.)*+\")" + "\\s*+");
  
  private static Pattern unquotePattern = Pattern.compile("\\\\(.)");
  
  private static String unquote(String s) {
      if(s.startsWith("\"")) {
          String unquoted;
          if(s.endsWith("\"")) unquoted = s.substring(1,s.length()-1);
          else unquoted = s.substring(1);
          return unquotePattern.matcher(unquoted).replaceAll("$1");
      }
      else return s.trim();
  }
  
  Map<String,List<String>> definedRights;
  long lastCheckDate;
  
  /** Given an input stream of the defined rights, set up our hash table.
   */
  void parseDefinedRights(InputStream rightsIn) {
      definedRights = new HashMap<String,List<String>>();
      try {
          BufferedReader rdr = new BufferedReader(new InputStreamReader(rightsIn,"UTF-8"));
          String key, handle;
          List<String> handles;
          while(true) {
              key = rdr.readLine();
              if(key==null) break;
              key = key.trim();
              if(key.length()<=0 || key.startsWith("#")) continue;
              
              handles = definedRights.get(key);
              if (handles==null) handles = new ArrayList<String>();
              while (true) {
                  handle = rdr.readLine();
                  if(handle==null) break;
                  handle = handle.trim();
                  if (handle.length()<=0) break;
                  if (handle.startsWith("#")) continue;
                  
                  handles.add(handle);
              }
              definedRights.put(key,handles);
          }
      }
      catch (IOException e) {
          logger.error("Defined rights specification error",e);     
      }
  }

  /** Set up the hash table of defined rights before an operation is checked for permissions.
   *  Only parse the defined_rights element if it is newer than the last time we checked.
   */
  void updateDefinedRights(StorageProxy storage) {
      StorageProxy serverStorage = storage.getObjectAccessor(serverMain.getServerID());
      try{
          if (serverStorage.doesDataElementExist(DEFINED_RIGHTS_ELEMENT)) {
              HeaderSet attributes = serverStorage.getElementAttributes(DEFINED_RIGHTS_ELEMENT,null);
              long lastModDate = attributes==null ? 0 : attributes.getLongHeader(DOConstants.DATE_MODIFIED_ATTRIBUTE, 0);
              if (lastModDate > 0 && lastModDate <= lastCheckDate) return;
              lastCheckDate = lastModDate;
              InputStream rightsIn = serverStorage.getDataElement(DEFINED_RIGHTS_ELEMENT);
              if (rightsIn != null) {
                  try {
                      parseDefinedRights(rightsIn);
                  } finally {
                      try { rightsIn.close(); } catch (Exception e) { }
                  }
              } else {
                  lastCheckDate = 0;
                  definedRights = null;
              }
          }
          else {
              lastCheckDate = 0;
              definedRights = null;
          }
      }
      catch (DOException e) {
          logger.error("Error retrieving element '"+DEFINED_RIGHTS_ELEMENT+
                  "' from '"+serverMain.getServerID(),e);          
      }
  }
  
  private Main serverMain;
  
  public Authorizer(Main serverMain) {
    this.serverMain = serverMain;
  }
  
  
  /**
   * This is called to determine if the given operation can be
   * performed in the given context (ie if the caller has permission
   * to perform the operation).
   */
  public boolean operationIsAllowed(DOOperationContext opContext) {
    return operationIsAllowed(opContext,
                              opContext.getTargetObjectID(), 
                              opContext.getOperationID());
  }
  
  /**
   * This is called to determine if the given operation can be performed in the given 
   * context.  The operation and target object are given as parameters rather than 
   * being determined from the DOOperationContext.
   */
  public boolean operationIsAllowed(DOOperationContext opContext, String objectID, String operationID) {
    if(!opContext.authenticateCaller()) {
      return false;
    }
    String callerID = opContext.getCallerID();
    if(callerID.equals(serverMain.getServerID())) {  // the repository itself can do anything
      // because of replication, we need to be able to perform any function on ourselves
      return true;
    }
    
    if(!DOConstants.ANONYMOUS_ID.equalsIgnoreCase(callerID) && callerID.equals(objectID)) {
      return true; // the client is authenticated as this object---*not* as a delegate
    }
    
    String serverAdmin = serverMain.getServerAdmin();
    if(serverAdmin!=null && callerID.equals(serverAdmin)) {
      return true;
    }
    
    StorageProxy storage = opContext.getStorage();
    if(!objectID.equals(opContext.getTargetObjectID())) {
      storage = storage.getObjectAccessor(objectID);
    }
    
    // check if the caller has rights to the object because it refers to them using one of the
    // 'owner_attributes' defined in the server configuration
    String ownerAtts[] = this.serverMain.getOwnerAttributes();
    if(ownerAtts!=null) {
      try {
        HeaderSet objAtts = storage.getAttributes(null);
        for(String ownerAttName : ownerAtts) {
          if(ownerAttName==null) continue;
          String ownerAttVal = objAtts.getStringHeader(ownerAttName, null);
          if(ownerAttVal==null) continue;
          if(ownerAttVal.equals(callerID)) return true; // the client was an "owner"
        }
      } catch (DOException e) {
        logger.error("Error getting object attributes for "+objectID+" during authorization: ",e);
      }
    }
    
    // get the rights for this specific object
    InputStream rightsIn = null;
    try {
        rightsIn = getRightsStream(storage);

        updateDefinedRights(storage);

        // if there are no object-level rights and no repo-level rights then the
        // operation is allowed iff the operation is being performed by the repository
        // itself.
        if(rightsIn!=null) {
            // there are rights for this object (or at least repository defaults) within this repository

            ScanResult scanResult = this.scanAccessRules(opContext, storage, rightsIn);
            if(scanResult == ScanResult.ACCEPT) return true;
            else if(scanResult == ScanResult.REJECT) return false;
        }
    } catch (Exception t) {
        // if there are any errors reading the object/system rights
        // then deny all access, except for the repository owner
        logger.error("Rights specification error",t);
    } finally {
        if(rightsIn!=null) try { rightsIn.close(); } catch (Throwable t) {}
    }
    
    // no access was granted using the object- or repository-level rights.  See if there are any authorization delegates
    String authDelAtts[] = this.serverMain.getAuthorizationDelegateAttributes();
    if(authDelAtts!=null) {
      try {
        HeaderSet objAtts = storage.getAttributes(null);
        for(String authDelAttName : authDelAtts) {
          if(authDelAttName==null) continue;
          String authDelAttVal = objAtts.getStringHeader(authDelAttName, null);
          if(authDelAttVal==null) continue;
          if(authDelAttVal.equals(callerID)) return true; // the client was an "owner"
          
          InputStream authDelRights = null;
          try {
            authDelRights = getRightsStream(storage.getObjectAccessor(authDelAttVal));
            ScanResult scanResult = this.scanAccessRules(opContext, storage, authDelRights);
            if(scanResult == ScanResult.ACCEPT) return true;
            else if(scanResult == ScanResult.REJECT) return false;
          } catch (Exception t) {
            // if there are any errors reading the object/system rights
            // then deny all access, except for the repository owner
            logger.error("Delegate rights specification error",t);
          } finally {
            if(authDelRights!=null) try { authDelRights.close(); } catch (Throwable t) {}
          }

        }
      } catch (DOException e) {
        logger.error("Error getting object attributes for "+objectID+" during authorization",e);
      }
    }
  
    //System.err.println("  access to "+objectID+" from operation "+describe(opContext)+" failed");
    return false;
  }
  
  public Set<String> getPermittedCallers(StorageProxy storage, String operationID) {
      Set<String> accepted = new HashSet<String>();
      Set<String> rejected = new HashSet<String>();
      accepted.add(serverMain.getServerID().toLowerCase(Locale.ENGLISH));
      String objectID = storage.getObjectID();
      if (!DOConstants.ANONYMOUS_ID.equalsIgnoreCase(objectID)) accepted.add(objectID.toLowerCase(Locale.ENGLISH));
      String serverAdmin = serverMain.getServerAdmin();
      if (serverAdmin != null) accepted.add(serverAdmin.toLowerCase(Locale.ENGLISH));
      String ownerAtts[] = this.serverMain.getOwnerAttributes();
      if (ownerAtts!=null) {
          try {
              HeaderSet objAtts = storage.getAttributes(null);
              for(String ownerAttName : ownerAtts) {
                  if(ownerAttName==null) continue;
                  String ownerAttVal = objAtts.getStringHeader(ownerAttName, null);
                  if(ownerAttVal==null) continue;
                  accepted.add(ownerAttVal.toLowerCase(Locale.ENGLISH));
              }
          } catch (DOException e) {
              logger.error("Error getting object attributes for "+objectID+" during indexing: ",e);
          }
      }
      // get the rights for this specific object
      InputStream rightsIn = null;
      try {
          rightsIn = getRightsStream(storage);

          updateDefinedRights(storage);

          // if there are no object-level rights and no repo-level rights then the
          // operation is allowed iff the operation is being performed by the repository
          // itself.
          if(rightsIn!=null) {
              // there are rights for this object (or at least repository defaults) within this repository
              scanAccessRules(rightsIn, objectID, operationID, accepted, rejected);
          }
      } catch (Exception t) {
          // if there are any errors reading the object/system rights
          // then deny all access, except for the repository owner
          logger.error("Rights specification error",t);
      } finally {
          if(rightsIn!=null) try { rightsIn.close(); } catch (Throwable t) {}
      }
      
      // no access was granted using the object- or repository-level rights.  See if there are any authorization delegates
      String authDelAtts[] = this.serverMain.getAuthorizationDelegateAttributes();
      if(authDelAtts!=null) {
        try {
          HeaderSet objAtts = storage.getAttributes(null);
          for(String authDelAttName : authDelAtts) {
            if(authDelAttName==null) continue;
            String authDelAttVal = objAtts.getStringHeader(authDelAttName, null);
            if(authDelAttVal==null) continue;
            authDelAttVal = authDelAttVal.toLowerCase(Locale.ENGLISH);
            if (!rejected.contains(authDelAttVal) && !rejected.contains("*")) accepted.add(authDelAttVal); // the client was an "owner"
            
            InputStream authDelRights = null;
            try {
              authDelRights = getRightsStream(storage.getObjectAccessor(authDelAttVal));
              scanAccessRules(authDelRights, authDelAttVal, operationID, accepted, rejected);
            } catch (Exception t) {
              // if there are any errors reading the object/system rights
              // then deny all access, except for the repository owner
              logger.error("Delegate rights specification error",t);
            } finally {
              if(authDelRights!=null) try { authDelRights.close(); } catch (Throwable t) {}
            }

          }
        } catch (DOException e) {
          logger.error("Error getting object attributes for "+objectID+" during authorization",e);
        }
      }
      return accepted;
  }
  
  private String describe(DOOperationContext operationContext) {
    StringBuilder sb = new StringBuilder();
    sb.append(operationContext);
    sb.append(" claimedgroups:");
    String groups[] = operationContext.getCredentialIDs();
    sb.append(groups==null ? "none" : String.valueOf(Arrays.asList(operationContext.getCredentialIDs())));
    return sb.toString();
  }
  
  private enum ScanResult { NO_MATCH, REJECT, ACCEPT }
  
  private ScanResult scanAccessRules(DOOperationContext opContext, StorageProxy storage, InputStream rightsIn)
    throws Exception
  {
    if(rightsIn==null) return ScanResult.NO_MATCH; // no rights stream given!
    
    String objectID = opContext.getTargetObjectID();
    String operationID = opContext.getOperationID();
    BufferedReader rdr = new BufferedReader(new InputStreamReader(rightsIn, "UTF8"));
    String caller = opContext.getCallerID();
    String certs[] = opContext.getCredentialIDs();

    HeaderSet headers = opContext.getOperationHeaders();
        
    String line;
    String paramRule;
    String opRule;
    String callerRule;
    boolean isAcceptRule;
    int idx1;
    int idx2;
    while(true) {
      line = rdr.readLine();
      if(line==null) break;
      line = line.trim();

      // assess the given line to see if it applies to this request
      if(line.length()<=0 || line.startsWith("#")) continue; // blank or comment line

      Matcher matcher = ruleTypePattern.matcher(line);
      if(!matcher.find()) {
        throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
      }
      String ruleType = matcher.group(1).trim();

      // the default behavior is to deny all operations unless specifically allowed
      // in the ruleset.  If no ruleset is provided, then all operations are allowed
      if(ruleType.equals("accept")) {
        isAcceptRule = true;
      } else if(ruleType.equals("reject")) {
        isAcceptRule = false;
      } else {
        throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
      }

      // if the operation ID for this rule doesn't match, skip the rest of this rule
      matcher.usePattern(componentPattern);
      if(!matcher.find()) {
        throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
      }
      opRule = unquote(matcher.group(1));
      if(opRule.length()>0 && !idMatches(operationID, opRule, true)) {
        continue;
      }

      // if the caller ID for this rule doesn't match, skip the rest of this rule
      if(!matcher.find()) {
        throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
      }
      callerRule = unquote(matcher.group(1));
      if(callerRule.length()>0) {
        if(!idMatches(caller, callerRule, true) && !certMatches(certs, opContext, callerRule))
          continue;
      }

      // if the parameters for this rule don't match, skip the rest of this rule
      paramRule = unquote(line.substring(matcher.end()));
      if(paramRule.length()>0 && !paramRule.equals("*") && !paramMatches(headers, paramRule)) {
          continue;
      }
      //if(isAcceptRule) System.err.println("  access to "+objectID+" from operation "+opContext+
      //                                    " granted by line "+line+" in "+objectID);
      if(isAcceptRule) return ScanResult.ACCEPT;
      else return ScanResult.REJECT;
    }
    return ScanResult.NO_MATCH;
  }

  private void scanAccessRules(InputStream rightsIn, String objectID, String operationID, Set<String> accepted, Set<String> rejected) throws Exception {
      if(rightsIn==null) return; // no rights stream given!

      BufferedReader rdr = new BufferedReader(new InputStreamReader(rightsIn, "UTF8"));
      String line;
      String paramRule;
      String opRule;
      String callerRule;
      boolean isAcceptRule;
      int idx1;
      int idx2;
      while(true) {
          line = rdr.readLine();
          if(line==null) break;
          line = line.trim();

          // assess the given line to see if it applies to this request
          if(line.length()<=0 || line.startsWith("#")) continue; // blank or comment line

          Matcher matcher = ruleTypePattern.matcher(line);
          if(!matcher.find()) {
              throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
          }
          String ruleType = matcher.group(1).trim();

          // the default behavior is to deny all operations unless specifically allowed
          // in the ruleset.  If no ruleset is provided, then all operations are allowed
          if(ruleType.equals("accept")) {
              isAcceptRule = true;
          } else if(ruleType.equals("reject")) {
              isAcceptRule = false;
          } else {
              throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
          }

          // if the operation ID for this rule doesn't match, skip the rest of this rule
          matcher.usePattern(componentPattern);
          if(!matcher.find()) {
              throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
          }
          opRule = unquote(matcher.group(1));
          if(opRule.length()>0 && !idMatches(operationID, opRule, true)) {
              continue;
          }

          // if the caller ID for this rule doesn't match, skip the rest of this rule
          if(!matcher.find()) {
              throw new Exception("Invalid line in access rights for object '"+objectID+"': "+line);
          }
          callerRule = unquote(matcher.group(1));
          callerRule = callerRule.toLowerCase(Locale.ENGLISH);
          if (isAcceptRule) {
              if (!rejected.contains(callerRule) && !rejected.contains("*")) accepted.add(callerRule);
          } else {
              rejected.add(callerRule);
          }
      }
  }
  
  private static InputStream returnStreamIfNonEmptyOtherwiseNull(InputStream stream) throws IOException {
      if (stream == null) return null;
      try {
          PushbackInputStream res = new PushbackInputStream(stream);
          int first = res.read();
          if(first>=0) {
              res.unread(first);
              return res;
          }
          else {
              res.close();
              return null;
          }
      } catch (IOException e) {
          stream.close();
          throw e;
      } catch (RuntimeException e) {
          stream.close();
          throw e;
      }
  }
  
  private InputStream getRightsStream(StorageProxy storage) throws IOException {
    try {
      if(storage.doesDataElementExist(RIGHTS_ELEMENT)) {
          InputStream res = returnStreamIfNonEmptyOtherwiseNull(storage.getDataElement(RIGHTS_ELEMENT));
          if(res!=null) return res;
      } 
    } catch (DOException e) {
      logger.error("Error retrieving element '"+RIGHTS_ELEMENT+"' from "+storage,e);
      return null;
    }
    
    // if there are no rights specifically for this object, see if there is
    // a repository-level set of rights
    try {
      StorageProxy serverStorage = storage.getObjectAccessor(serverMain.getServerID());
      InputStream res = returnStreamIfNonEmptyOtherwiseNull(serverStorage.getDataElement(REPO_DEFAULT_RIGHTS_ELEMENT));
      return res; 
    } catch (DOException e) {
      logger.error("Error retrieving element '"+REPO_DEFAULT_RIGHTS_ELEMENT+
                         "' from '"+serverMain.getServerID(),e);
      return null;
    }
  }
  
  /** Return a list of entities who are explicitly given permission to perform the
    * given operation on the object.  Keep in mind that this doesn't take into account 
    * any entities that are explicitly denied access in an earlier rule. */
  public String[] getExplicitAccepts(StorageProxy storage, String operationID) 
  throws IOException
  {
    InputStream rightsIn = getRightsStream(storage);
    if(rightsIn==null) return new String[] { serverMain.getServerID() };
    try {
        ArrayList accepts = new ArrayList();
        BufferedReader rdr = new BufferedReader(new InputStreamReader(rightsIn, "UTF8"));
        String op = operationID;

        String line;
        String paramRule;
        String opRule;
        String callerRule;
        boolean isAcceptRule;
        int idx1;
        int idx2;
        while(true) {
            line = rdr.readLine();
            if(line==null) break;
            line = line.trim();

            // assess the given line to see if it applies to this request
            if(line.length()<=0 || line.startsWith("#")) continue; // blank or comment line

            Matcher matcher = ruleTypePattern.matcher(line);
            if(!matcher.find()) {
                // ignore invalid lines
                continue;
            }
            String ruleType = matcher.group(1).trim();

            // the default behavior is to deny all operations unless specifically allowed
            // in the ruleset.  If no ruleset is provided, then all operations are allowed
            if(ruleType.equals("accept")) {
                isAcceptRule = true;
            } else if(ruleType.equals("reject")) {
                continue;
            } else {
                // ignore invalid lines
                continue;
            }

            // if the operation ID for this rule doesn't match, skip the rest of this rule
            matcher.usePattern(componentPattern);
            if(!matcher.find()) {
                continue;
            }
            opRule = unquote(matcher.group(1));
            if(opRule.length()>0 && !idMatches(op, opRule, true)) {
                continue;
            }

            // if the caller ID for this rule doesn't match, skip the rest of this rule
            if(!matcher.find()) {
                continue;
            }
            callerRule = unquote(matcher.group(1));
            if(callerRule.length()>0) {
                accepts.add(callerRule);
                // TODO:  possibly follow dereferenced "definedRights" values here
            }
        }

        return (String[])accepts.toArray(new String[accepts.size()]);
    } finally {
        rightsIn.close();
    }
  }
  
  
  
  public class AuthorizationInfo {
    private DOOperationContext operation;
    private String endpointRepoID = null;
    private String ruleType = "";
    private HeaderSet params = new HeaderSet();
    
    void reset(DOOperationContext op) {
      params.removeAllHeaders();
      this.ruleType = "";
      this.endpointRepoID = null;
      this.operation = op;
    }
    
    /** Set the authorization type and parameters based on the rule that matched the invocation */
    void setInfo(String matchingRuleType, String paramString) {
      if(matchingRuleType==null) matchingRuleType = "";
      this.ruleType = matchingRuleType;
      
      if(paramString==null) paramString = "";
      params.readHeadersFromString(paramString);
    }
    
    /** Get the endpoint repository ID.  The endpoint repository ID is the identifier for
      * the repository ID to which the target object ID resolves. */
    synchronized String getEndpointRepositoryID() {
      if(operation==null) return null;
      if(endpointRepoID!=null) return endpointRepoID;
      try {
        endpointRepoID = DOClient.resolveRepositoryID(operation.getTargetObjectID());
      } catch (Exception e) {
        logger.error("Error resolving repository endpoint for '"+operation.getTargetObjectID(),e);
      }
      return endpointRepoID;
    }
    
    /** Return true if the authorization rule specifies that this operation can be forwarded */
    public boolean canForwardOperation() {
      return ruleType.equals("accept");
    }
    
    /** Return true if the authorization rule specifies that the operation should be forwarded 
      * using the repository's credentials.  The default is to use the repository's credentials
      * unless the rule parameters indicate otherwise. */
    public boolean useRepoID() {
      return params.getStringHeader(USE_IDENTITY_KEY, USE_REPOSITORY_ID).equals(USE_REPOSITORY_ID);
    }
    
    public String toString() {
      return "authinfo: dest="+endpointRepoID+
      "; ruletype="+ruleType+
      "; params="+params;
    }
    
  }
  
  
  
  /**
   * This is called to determine if and how the given operation can be forwarded to a
   * remote repository.  If the given operation can be forwarded, this will return
   * the identity handle for the workgroup or other entity that the server should use to
   * authenticate the connection.
   */
  public AuthorizationInfo forwardingIsAllowed(DOOperationContext opContext,
                                               AuthorizationInfo authInfo) {
    if(authInfo==null) {
      authInfo = new AuthorizationInfo();
    }
    authInfo.reset(opContext);
    
    if(!opContext.authenticateCaller()) {
      return authInfo;
    }
    
    StorageProxy storage = opContext.getStorage();
    if(!opContext.getTargetObjectID().equals(serverMain.getServerID())) {
      storage = storage.getObjectAccessor(serverMain.getServerID());
    }
    
    // get the rights for this specific object
    InputStream rightsIn = null;
    try {
      try {
          if(storage.doesDataElementExist(FORWARDING_ELEMENT)) {
              rightsIn = storage.getDataElement(FORWARDING_ELEMENT);
          }
      } catch (DOException e) {
          logger.error("Error retrieving element '"+FORWARDING_ELEMENT+"' from '"+
                  serverMain.getServerID(),e);
          return authInfo;
      }

      if(rightsIn==null) return authInfo;

      updateDefinedRights(storage);

      BufferedReader rdr = new BufferedReader(new InputStreamReader(rightsIn, "UTF8"));
      String caller = opContext.getCallerID();
      String certs[] = opContext.getCredentialIDs();
      String op = opContext.getOperationID();
      String obj = opContext.getTargetObjectID();
      
      String line;
      String params;
      String opRule;
      String callerRule;
      String rulePrefix;
      String targetRepoRule;
      boolean isAcceptRule;
      int idx1;
      int idx2;
      while(true) {
        line = rdr.readLine();
        if(line==null) break;
        line = line.trim();
        
        // assess the given line to see if it applies to this request
        if(line.length()<=0 || line.startsWith("#")) continue; // blank or comment line
        
        Matcher matcher = ruleTypePattern.matcher(line);
        if(!matcher.find()) {
            throw new Exception("Invalid line in forwarding rights for object '"+obj+"': "+line);
        }
        rulePrefix = matcher.group(1).trim();
                
        // if the operation ID for this rule doesn't match, skip the rest of this rule
        matcher.usePattern(componentPattern);
        if(!matcher.find()) {
            throw new Exception("Invalid line in forwarding rights for object '"+obj+"': "+line);
        }
        opRule = unquote(matcher.group(1));
        if(opRule.length()>0 && !idMatches(op, opRule, true)) {
          continue;
        }

        // if the caller ID for this rule doesn't match, skip the rest of this rule
        if(!matcher.find()) {
            throw new Exception("Invalid line in forwarding rights for object '"+obj+"': "+line);
        }
        callerRule = unquote(matcher.group(1));
        if(callerRule.length()>0) {
          if(!idMatches(caller, callerRule, true) && !certMatches(certs, opContext, callerRule))
            continue;
        }
        
        // destination repo rule
        if(!matcher.find()) {
            throw new Exception("Invalid line in forwarding rights for object '"+obj+"': "+line);
        }
        targetRepoRule = unquote(matcher.group(1));
        if(targetRepoRule.length()>0 && !idMatches(authInfo.getEndpointRepositoryID(), targetRepoRule, true)) {
            continue;
        }
        
        // if we get to this point then the rule matches so we set the parameters and return
        authInfo.setInfo(rulePrefix, unquote(line.substring(matcher.end())));
        return authInfo;
      }
    } catch (Throwable t) {
      // if there are any errors reading the object/system rights
      // then return the default authInfo, which should deny the request
      return authInfo;
    } finally {
        if (rightsIn != null) try { rightsIn.close(); } catch (Exception e) {}
    }
    
    return authInfo;
  }
  
  
  
  /**
   * Returns true iff the given parameter rule occurs in the given set of
   * parameters.
   */
  private static final boolean paramMatches(HeaderSet params, String paramRule) {
    int startIdx = -1;
    int endIdx = 0;
    startIdx = endIdx;
    String key = null;
    String val = null;
    paramRule = paramRule.trim();
    if(paramRule.equals("*") || paramRule.equals(""))
      return true;
    
    while(endIdx>=0) {
      key = null;
      val = null;
      endIdx = paramRule.indexOf('&', startIdx+1);
      String segment = endIdx<0 ?
        paramRule.substring(startIdx+1) :
        paramRule.substring(startIdx+1, endIdx);

      int eqIdx = segment.indexOf('=');
      if(eqIdx<0) {
        key = HeaderSet.unescapeURLTxt(segment);
        val = "";
      } else {
        key = HeaderSet.unescapeURLTxt(segment.substring(0, eqIdx));
        val = HeaderSet.unescapeURLTxt(segment.substring(eqIdx+1));
      }

      for(Iterator it = params.iterator(); it.hasNext(); ) {
        HeaderItem hitem = (HeaderItem)it.next();
        if(compare(key, 0, hitem.getName(), 0, true) &&
           compare(val, 0, hitem.getValue(), 0, true)) {
          return true;
        }
      }
    }
    return false;
  }

          
  private final boolean certMatches(String certs[],
                                           DOOperationContext context,
                                           String matchStr)
  {
    if(certs==null) return false;
    for(int i=certs.length-1; i>=0; i--) {
      String cert = certs[i];
      if(idMatches(cert, matchStr, true)) {
        // the certificate ID matches... now we must make sure that it is valid
        if(context.authenticateCredential(cert))
          return true;
      }
    }
    
    return false;
  }
  
  /**
   * Returns true iff the given string matches the given ID in a case insensitive
   * comparison.  Also checks for matchStr in definedRights.
   */
  private final boolean idMatches(String idStr, String matchStr, boolean caseSensitive)
  {
      if(idMatchesNoDefines(idStr,matchStr,caseSensitive)) return true;
      if(definedRights==null) return false;
      return idMatchesDefines(idStr,matchStr,caseSensitive,new HashSet<String>());
  }

  private final boolean idMatchesDefines(String idStr, String matchStr, boolean caseSensitive, Set<String> seen)
  {
      if(seen.contains(matchStr)) return false;
      List<String> handles = definedRights.get(matchStr);
      if(handles==null) return false;
      seen.add(matchStr);
      for (String handle : handles) {
          if(idMatchesNoDefines(idStr,handle,caseSensitive)) return true;
          if(idMatchesDefines(idStr,handle,caseSensitive,seen)) return true;
      }
      return false;
  }
  
  /**
   * Returns true iff the given string matches the given ID in a case insensitive
   * comparison.
   */
  private static final boolean idMatchesNoDefines(String idStr, String matchStr,
                                         boolean caseSensitive)
  {
    int sidx;
    int eidx;

    // if there are multiple entries on this line, parse them out.
    // also handle backslash-encoded special characters
    int startIdx = 0;
    int endIdx = 0;
    char ch;
    char ch2;
    int strlen = matchStr.length();
    while(endIdx < strlen) {
      ch = matchStr.charAt(endIdx++);
      if(ch=='\\' && endIdx<strlen) {
        // convey backslashed characters (including commas)
        endIdx++;
      } else if(ch==',') {
        // this is the end of one possible match... compare it
        if(startIdx+1<endIdx && compare(idStr, 0,
                                        matchStr.substring(startIdx, endIdx), 0,
                                        caseSensitive)) {
          return true;
        }
        startIdx = endIdx;
      }
    }
    
    // this is the end of one possible match... compare it
    if(startIdx<endIdx && compare(idStr, 0,
                                  matchStr.substring(startIdx, endIdx), 0,
                                  caseSensitive)) {
      return true;
    }
    return false;
  }

  private static boolean compare(String cmpVal, String matchVal, boolean caseSensitive) {
    return compare(cmpVal, 0, matchVal, 0, caseSensitive);
  }
  
  private static boolean compare(String cmpVal, int cmpIdx,
                                 String matchVal, int matchIdx,
                                 boolean caseSensitive)
  {
    if(cmpVal==null || matchVal==null) return false;
    char cmpCh;
    char matchCh;
    int cmpLen = cmpVal.length();
    int matchLen = matchVal.length();
    boolean escapedChar;
    while(cmpIdx < cmpLen && matchIdx < matchLen) {
      escapedChar = false;
      matchCh = matchVal.charAt(matchIdx++);
      cmpCh = cmpVal.charAt(cmpIdx++);
      if(matchCh=='\\' && matchIdx<matchLen) {
        escapedChar = true;
        matchCh = matchVal.charAt(matchIdx++);
        // decode backslashed characters
        switch(matchCh) {
          case 'n': matchCh = '\n'; break;
          case 'r': matchCh = '\r'; break;
          case 't': matchCh = '\t'; break;
          case '*': matchCh = '*'; break;
          default: break; // just use the matchCh value
        }
      }

      if(!escapedChar && matchCh=='*') {
        // wildcard... see if the remainder of cmpVal matches the remainder of matchVal
        for(int cmpIdx2=cmpIdx-1; cmpIdx2<=cmpLen; cmpIdx2++) {
          if(compare(cmpVal, cmpIdx2, matchVal, matchIdx, caseSensitive))
            return true;
        }
        return false;
      } else if(caseSensitive) {
        if(matchCh!=cmpCh)
          return false;
      } else {
        if(Character.toLowerCase(matchCh)!=Character.toLowerCase(cmpCh)) {
          return false;
        }
      }
    }
    return cmpIdx==cmpLen && matchIdx==matchLen;
  }
  
}
