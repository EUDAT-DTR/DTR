/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.handle.hdllib.*;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DOStorageResolver extends Resolver {
  static final Logger logger = LoggerFactory.getLogger(DOStorageResolver.class);
  public static final String HANDLE_VALUE_ATTRIBUTE_PREFIX = "hdl.";

  private Resolver fallback = null;
  private Main server;
  
  public DOStorageResolver(Resolver fallbackResolver, Main server) {
    this.fallback = fallbackResolver;
    this.server = server;
  }

  public HandleValue[] resolveHandle(String handle, String typeList[],
                                     int indexes[], boolean secure)
  throws HandleException
  {

    // only override the handle with local values if we're not looking for specific indexes
    if(indexes==null || indexes.length==0) {
      Storage storage = server.getStorage();
      try {
        if(storage.doesObjectExist(handle)) {
          StorageProxy storageProxy = new ConcreteStorageProxy(storage, server.getServerID(), handle, null);
          HeaderSet attributes = storageProxy.getAttributes(null);
          ArrayList<HandleValue> doValues = new ArrayList<HandleValue>();

          if(typeList==null || typeList.length==-0) { // not looking for a specific type, so return all matches
            for(HeaderItem item : attributes) {
              String attName = item.getName();
              if(!attName.startsWith(HANDLE_VALUE_ATTRIBUTE_PREFIX)) continue;
              String attHSType = attName.substring(HANDLE_VALUE_ATTRIBUTE_PREFIX.length());
              // add the attribute value as a text handle value
              doValues.add(new HandleValue(doValues.size()+1,
                                           Util.encodeString(attHSType),
                                           Util.encodeString(item.getValue())));
            }

            // look for any data elements that contain binary handle values (hopefully not too large!)
            for(Enumeration elementIDs = storageProxy.listDataElements(); elementIDs.hasMoreElements(); ) {
              String elementID = (String)elementIDs.nextElement();
              if(!elementID.startsWith(HANDLE_VALUE_ATTRIBUTE_PREFIX)) continue;
              String attName = elementID.substring(HANDLE_VALUE_ATTRIBUTE_PREFIX.length());
              // add the attribute value as a handle value (assuming a text value)
              InputStream in = null;
              try {
                in = storageProxy.getDataElement(elementID);
                if (in != null) { 
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    byte buf[] = new byte[512];
                    int r;
                    while((r=in.read(buf))>=0) bout.write(buf, 0, r);
                    doValues.add(new HandleValue(doValues.size()+1,
                            Util.encodeString(attName),
                            bout.toByteArray()));
                }
              } catch (IOException ioE) {
                logger.error("Error reading element '"+elementID+
                                   "' for object '"+handle+"' in resolution short-cut",ioE);
              } finally {
                if (in != null) try { in.close(); } catch (Exception e) { }
              }
            }
          } else { // this block is when specific value types were requested
            for(HeaderItem item : attributes) {
              String attName = item.getName();
              if(!attName.startsWith(HANDLE_VALUE_ATTRIBUTE_PREFIX)) continue;
              String attHSType = attName.substring(HANDLE_VALUE_ATTRIBUTE_PREFIX.length());
              for(String hsType : typeList) {
                if(hsType.equalsIgnoreCase(attHSType)) {
                  // add the attribute value as a handle value (assuming a text value)
                  doValues.add(new HandleValue(doValues.size()+1,
                                               Util.encodeString(attHSType),
                                               Util.encodeString(item.getValue())));
                }
              }
            }

            // look for any data elements that contain binary handle values (hopefully not too large!)
            for(Enumeration elementIDs = storageProxy.listDataElements(); elementIDs.hasMoreElements(); ) {
              String elementID = (String)elementIDs.nextElement();
              if(!elementID.startsWith(HANDLE_VALUE_ATTRIBUTE_PREFIX)) continue;
              String attName = elementID.substring(HANDLE_VALUE_ATTRIBUTE_PREFIX.length());
              for(String hsType : typeList) {
                if(hsType.equalsIgnoreCase(attName)) {
                  // add the attribute value as a handle value (assuming a text value)
                  InputStream in = null;
                  try {
                    in = storageProxy.getDataElement(elementID);
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    byte buf[] = new byte[512];
                    int r;
                    while((r=in.read(buf))>=0) bout.write(buf, 0, r);
                    doValues.add(new HandleValue(doValues.size()+1,
                                                 Util.encodeString(attName),
                                                 bout.toByteArray()));
                  } catch (IOException ioE) {
                    logger.error("Error reading element '"+elementID+
                                       "' for object '"+handle+"' in resolution short-cut",ioE);
                  } finally {
                    if (in != null) try { in.close(); } catch (Exception e) { }
                  }
                }
              }
            }
          } // end if(typeList==null || typeList.length==0) else ...

          if(doValues.size()>0) {
            // if there were override values, return them as the result of the resolution
            return doValues.toArray(new HandleValue[doValues.size()]);
          }
        }
      } catch (DOException doe) {
        logger.error("Error checking local storage for handle '"+handle+"'",doe);
      }
    }

    // pass the resolution on to the normal handle resolver
    return fallback.resolveHandle(handle, typeList, indexes, secure);
  }

  
  public boolean checkAuthentication(AuthenticationInfo authInfo)
    throws Exception
  {
    Storage storage = server.getStorage();
    String handle = Util.decodeString(authInfo.getUserIdHandle());
    try {
      if(storage.doesObjectExist(handle)) {
        StorageProxy storageProxy = new ConcreteStorageProxy(storage, server.getServerID(), handle, null);
        HeaderSet attributes = storageProxy.getAttributes(null);
        
        if(authInfo instanceof SecretKeyAuthenticationInfo) {
          SecretKeyAuthenticationInfo secKeyAuth = (SecretKeyAuthenticationInfo)authInfo;
          byte secKey[] = secKeyAuth.getSecretKey();

          String secKeyStr = attributes.getStringHeader(HANDLE_VALUE_ATTRIBUTE_PREFIX + "HS_SECKEY", null);
          if(secKeyStr!=null) {
            return Util.equals(Util.encodeString(secKeyStr), secKey);
          }
        } else if(authInfo instanceof PublicKeyAuthenticationInfo) {
          
          // TODO: verify the private key by resolving the public keys
          
        }
      }
    } catch (Exception e) {
      logger.error("Error checking authentication",e);
    }
    return fallback.checkAuthentication(authInfo);
  }
  
}
