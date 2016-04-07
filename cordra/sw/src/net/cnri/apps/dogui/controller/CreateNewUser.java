/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.apps.doutilgui.*;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.handle.hdllib.*;

import java.io.*;

public class CreateNewUser {
  private static final String DEFAULT_PDO_REPO = "cnri.test.sean/nrep1";
  private static final String DEFAULT_HOME_REPO = "cnri.test.sean/nrep1";
  private static final String DEFAULT_INDEX = "cnri.test.sean/nidx1";
  private static final String NEW_USER_PREFIX = "cnri.test.";
  private static final String DEFAULT_HDL_SERVICE = "0.NA/cnri.test.sean";
  private static final String RIGHTS_ELEMENT = "internal.rights";
  
  private static final String VERSION_STRING = "1.0";
  private DOAuthentication doAuth = null;
  private AuthenticationInfo hdlAuth = null;
    
  public CreateNewUser(DOAuthentication doAuth, AuthenticationInfo hdlAuth) {
    this.doAuth = doAuth;
    this.hdlAuth = hdlAuth;
  }
  
  
  public void go() 
    throws Exception
  {
    HeaderSet info = new HeaderSet();
    while(info.readHeaders(System.in)) {
      System.out.println("Creating user with info: "+info);
      String userID = info.getStringHeader("user", null);
      if(userID==null || userID.trim().length()<=0) {
        throw new Exception("No user ID (user) found in record: "+info);
      }
      userID = userID.trim();
      String userRepo = info.getStringHeader("urepo", DEFAULT_PDO_REPO);
      String userHome = info.getStringHeader("uhome", DEFAULT_HOME_REPO);
      byte pubKey[] = info.getHexByteArrayHeader("pubkey", null);
      if(pubKey==null) {
        String privKeyFileName = info.getStringHeader("destprivkeyfile", null);
        if(privKeyFileName!=null) {
          // generate a new public key and store it 
          File privKeyFile = new File(privKeyFileName);
          File pubKeyFile = new File(privKeyFileName+"-pub");
          String passphrase = info.getStringHeader("passphrase", null);
          byte[] passBytes = passphrase==null ? null : Util.encodeString(passphrase);
          net.cnri.apps.cmdline.KeyGenerator.generateKeys("RSA", 2048, 
                                                          passBytes,
                                                          privKeyFile, pubKeyFile);
          
          DataInputStream din = new DataInputStream(new FileInputStream(pubKeyFile));
          pubKey = new byte[(int)pubKeyFile.length()];
          din.readFully(pubKey);
          din.close();
        } else {
          throw new Exception("No public key (pubkey) found in record: "+info);
        }
      }
      String userHdl = null;
      String prefixHdl = null;
      if(userID.indexOf('/')>0) {
        userHdl = userID;
      } else {
        userHdl = NEW_USER_PREFIX + userID + '/' + userID;
        prefixHdl = "0.NA/"+NEW_USER_PREFIX+userID;
      }
      HandleValue values[];
      
      AdminRecord rootAdmin = new AdminRecord(Util.encodeString("0.NA/0.NA"), 200,
                                              true, true, false, false,
                                              true, true, true, true,
                                              true, true, true, true);
      AdminRecord prefixAdmin = new AdminRecord(Util.encodeString(userHdl), 200,
                                                true, true, false, false,
                                                true, true, true, true,
                                                true, true, true, true);
      AdminRecord userAdmin = new AdminRecord(Util.encodeString(userHdl), 200,
                                              true, true, true, true,
                                              true, true, true, true,
                                              true, true, true, true);
      
      Resolver resolver = DOClient.getResolver();
      HandleResolver hdlResolver = resolver.getResolver();
      AbstractResponse response = null;
      //hdlResolver.traceMessages = true;
      
      // if the user handle doesn't already have its own prefix then create and home one
      SiteInfo handleSite = null;
      if(prefixHdl!=null) {
        System.out.println("Creating prefix: "+prefixHdl);
        
        String hdlService = info.getStringHeader("hdlserv", DEFAULT_HDL_SERVICE);
        values = new HandleValue[] {
          new HandleValue(1, Common.STD_TYPE_HSSERV, Util.encodeString(hdlService)),
          new HandleValue(100, Common.STD_TYPE_HSADMIN, Encoder.encodeAdminRecord(prefixAdmin)),
          new HandleValue(101, Common.STD_TYPE_HSADMIN, Encoder.encodeAdminRecord(rootAdmin)),
          new HandleValue(300, Common.STD_TYPE_HSPUBKEY, pubKey),
        };
        // create the prefix handle
        response =
          hdlResolver.processRequest(new CreateHandleRequest(Util.encodeString(prefixHdl), 
                                                             values, hdlAuth));
        
        System.out.println("Homing prefix: "+prefixHdl);
        // home the prefix
        HandleValue siteVals[] = resolver.resolveHandle(hdlService,
                                                        new String[] {"HS_SITE"});
        for(int i=0; siteVals!=null && i<siteVals.length; i++) {
          if(siteVals[i].hasType(Common.STD_TYPE_HSSITE)) {
            SiteInfo site = new SiteInfo();
            Encoder.decodeSiteInfoRecord(siteVals[i].getData(), 0, site);
            handleSite = site;
            break;
          }
        }
        
        System.err.println("sending home-prefix request to site: "+handleSite);
        GenericRequest req =
        new GenericRequest(Util.encodeString(prefixHdl), 
                           AbstractMessage.OC_HOME_NA, hdlAuth);
        req.isAdminRequest = true;
        
        response = hdlResolver.sendRequestToSite(req, handleSite);
        if(response.responseCode!=AbstractMessage.RC_SUCCESS) {
          throw new Exception("Unsuccessful homing of prefix: "+prefixHdl+
                              "; response: "+response);
        }
      }
      
      
      System.err.println("Creating PDO: "+userHdl+" on repository "+userRepo);
      Repository repo = new Repository(doAuth, userRepo);
      try {
        // set up the user object while we're waiting for the new prefix to propagate
        DigitalObject userObject = repo.createDigitalObject(userHdl);
        userObject.setAttribute("do.indexes", info.getStringHeader("do.index", DEFAULT_INDEX));
        userObject.setAttribute("do.home_repo", userHome);
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Writer wout = new OutputStreamWriter(bout, "UTF8");
        wout.write("accept:*\t"+userHdl+"\t*\n");
        wout.write("accept:*\t200/0\t*\n");
        wout.write("accept:*\tcnri.test.sreilly/sreilly\t*\n");
        wout.write("\n");
        wout.write("accept:1037/46\t*\t*\n");
        wout.write("accept:1037/5\t*\t*\n");
        wout.write("accept:1037/8\t*\t*\n");
        wout.write("accept:1037/0\t*\t*\n");
        wout.write("accept:1037/50\t*\t*\n");
        wout.write("accept:1037/44\t*\t*\n");
        wout.write("accept:1037/43\t*\t*\n");
        wout.write("accept:1037/52\t*\t*\n");
        wout.close();
        
        DataElement rightsElement = userObject.getDataElement(RIGHTS_ELEMENT);
        rightsElement.write(new ByteArrayInputStream(bout.toByteArray()));
      } finally {
        repo.getConnection().close();
      }
      
      // create the user handle (need to wait a little while... at least for
      // prefix handle to be created
      values = new HandleValue[] {
        new HandleValue(300, Common.STD_TYPE_HSPUBKEY, pubKey),
        new HandleValue(100, Common.STD_TYPE_HSADMIN, 
                        Encoder.encodeAdminRecord(userAdmin)),
        new HandleValue(101, Common.STD_TYPE_HSADMIN, 
                        Encoder.encodeAdminRecord(userAdmin)),
        new HandleValue(1, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE),
                        Util.encodeString(DEFAULT_HOME_REPO)),
      };
      
      CreateHandleRequest cuReq = 
        new CreateHandleRequest(Util.encodeString(userHdl), values, hdlAuth);
      
      System.err.println("Creating user handle: "+userHdl);
      if(handleSite!=null) {
        response = hdlResolver.sendRequestToSite(cuReq, handleSite);
      } else {
        response = hdlResolver.processRequest(cuReq);
      }
      System.err.println("Got response: "+response);
      
      System.out.println("Created user: "+userHdl);
      
    }
  }
  
  public static void main(String argv[]) 
    throws Exception
  {
    AuthWindow authWin = new AuthWindow(null, "create_users");
    authWin.setVisible(true);
    if(authWin.wasCanceled()) {
      System.exit(1);
    }
    
    DOAuthentication doAuth = authWin.getAuthentication();
    AuthenticationInfo hdlAuth = doAuth.toHandleAuth();
    authWin.dispose();
    new CreateNewUser(doAuth, hdlAuth).go();
    System.exit(0);
  }
  
  
}
