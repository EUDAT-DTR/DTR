/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.DOConstants;
import net.cnri.simplexml.*;
import net.handle.hdllib.*;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class runs a server object that accepts and handles incoming requests
 * for operations on digital objects.  Agents in an embedded knowbot service 
 * station are used to determine access control as well as to perform operations
 * on objects in the server.
 */
public class SetupHelper {
  public static final int NO_DEFAULT = -1;          // Argument to getInteger()
  public static final int NO_LIMIT   = -1;          // Argument to getInteger()
  private InetAddress lastIPAddress = null;
  public PrintStream out;
  public PrintStream err;
  public BufferedReader in;
  private Resolver resolver = null;

  public SetupHelper()
    throws Exception
  {
    this.out = System.out;
    this.err = System.err;
    this.in = new BufferedReader(new InputStreamReader(System.in));
    this.resolver = new Resolver();
  }
  
  public String getServerDescription() throws Exception {
    return responseToPrompt("Please enter a short description of this server");
  }
  
  public InetAddress getExternalAddress() throws Exception {
    InetAddress addr = 
      getIPAddress("Through what IP address should clients connect to this server?"
                   + " (Domain names are OK)", lastIPAddress);
    if(addr!=null) lastIPAddress = addr;
    return addr;
  }
  public InetAddress getInternalAddress() throws Exception {
    InetAddress addr = 
      getIPAddress("If different, enter the IP address to which the server should bind.",
                   lastIPAddress);
    if(addr!=null) lastIPAddress = addr;
    return addr;
  }
  public int getListenPort() throws Exception {
    return getInteger("Enter the TCP port number this server will listen to (0 for no DOP interface)",
                      Main.DEFAULT_PORT);
  }
  
  public int getHTTPPort() throws Exception {
    return getInteger("Enter the HTTP port number this server will listen to (0 for no HTTP interface)",
                      Main.DEFAULT_HTTP_PORT);
  }
  
  public int getHTTPSPort() throws Exception {
      return getInteger("Enter the HTTPS port number this server will listen to (0 for no HTTPS interface)",
                        Main.DEFAULT_HTTPS_PORT);
    }

  public int getSSLPort() throws Exception {
    return getInteger("Enter the SSL/TLS port number this server will listen to (0 for no DOP-SSL interface)",
                      Main.DEFAULT_SSL_PORT);
  }
  
  public boolean getHosted() throws Exception {
    String s = responseToPrompt("Would you like the repository to be automatically set up on a hosted handle service? [n]");
    return ("y".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s));
  }
  
  public String getUniqueServiceID() throws Exception {
    String s =responseToPrompt("Please enter the handle for this repository");
    return s;
  }
  
  public String getUniqueServerID() throws Exception {
    String s = responseToPrompt("Please enter a label for this server that is different than other servers of the same repository (eg '1', '2', '3'... etc) [1]");
    if(s!=null) s = s.toLowerCase();
    if(s==null || s.length()==0) s = "1";
    return s;
  }
  
  public boolean verifyID(String serviceID, String localServerID) {
    if(serviceID==null || serviceID.trim().length()<=0) return false;
    if(localServerID==null || localServerID.trim().length()<=0) return false;
    
    if(serviceID.indexOf('-')>=0 || serviceID.indexOf(' ')>=0) {
      System.out.println("Error:  service ID should not include a hyphen or space");
      return false;
    }
    
    return true;
  }
  
  class AccumCallback implements ResponseMessageCallback {
      ArrayList<String> handles = new ArrayList<String>();
      
      public void handleResponse(AbstractResponse response) throws HandleException {
          if(response instanceof ListHandlesResponse) {
              try {
                  ListHandlesResponse lhResp = (ListHandlesResponse)response;
                  byte handles[][] = lhResp.handles;
                  for(int i=0; i<handles.length; i++) {
                      this.handles.add(Util.decodeString(handles[i]));
                  }
              } catch (Exception e) {
                  HandleException he = new HandleException(HandleException.INTERNAL_ERROR,"Error listing handles");
                  he.initCause(e);
                  throw he;
              }
          } else {
              throw new HandleException(HandleException.INTERNAL_ERROR,"Error listing handles");
          }
      }
  }
  
  /**
   * If serviceID is null, gets a handle through the hosting service.  The return value is the (input or generated) serviceID.
   */
  public String registerServer(String serviceID, String localServerID, 
          byte pubKeyBytes[], InetAddress externalAddr,
          int port, int sslPort, int httpPort, int httpsPort, String description, File infoFile, 
          File pubKeyFile, File privKeyFile, PrivateKey privKey) 
  throws Exception
  {
    boolean hosted = serviceID==null;
        
    XTag serverInfoTag = new XTag("serverinfo");
    XTag serverTag = new XTag("server");
    serverInfoTag.addSubTag(serverTag);
    serverTag.addSubTag(new XTag("id", localServerID));
    serverTag.addSubTag(new XTag("label", description));
    // omitting public key, can get it from HS_PUBKEY value instead
    // serverTag.addSubTag(new XTag("publickey", Util.decodeHexString(pubKeyBytes, false)));
    serverTag.addSubTag(new XTag("hostaddress", externalAddr.getHostAddress()));
    if(port>0) {
      serverTag.addSubTag(new XTag("port", String.valueOf(port)));
    }
    if(sslPort>0) {
      serverTag.addSubTag(new XTag("ssl-port", String.valueOf(sslPort)));
    }
    if(httpPort>0) {
      serverTag.addSubTag(new XTag("http_url", "http://"+externalAddr.getHostAddress()+
                                   ":"+httpPort+"/get"));
    }
    if(httpsPort>0) {
        serverTag.addSubTag(new XTag("https_url", "https://"+externalAddr.getHostAddress()+
                                     ":"+httpsPort+"/get"));
      }
    serverTag.addSubTag(new XTag("protocol", "DOP"));
    
    if (hosted) {
        String email = null;
        AuthenticationInfo authInfo = null;
        while(email==null || email.length()==0) {
            email = responseToPrompt("Please enter your email address");
        }
        email = email.trim();
        
        byte[] authHandle = new byte[] { '2', '0', '0', '/' , '9', '9', '6' };

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        InputStream fin = getClass().getResourceAsStream("resources/200--996.priv");
        byte[] buf = new byte[1024];
        int r = 0;

        /* read the private key file into memory */
        while((r=fin.read(buf))>=0) bout.write(buf, 0, r);
        byte[] privateKeyFileContents = bout.toByteArray();

        /* decrypt the private key */
        byte[] decryptedPrivateKey = Util.decrypt(privateKeyFileContents, null);

        /* return a java.security.PrivateKey */
        PrivateKey privateKey = Util.getPrivateKeyFromBytes(decryptedPrivateKey, 0);
        authInfo = new PublicKeyAuthenticationInfo(authHandle, 300, privateKey);

        AbstractRequest listReq = new ListHandlesRequest(Util.encodeString("0.NA/cnri.test.do"),authInfo);
        AccumCallback callback = new AccumCallback();
        resolver.getResolver().processRequest(listReq,callback);
        Collections.sort(callback.handles);
        int dor = 10001;
        for(String handle : callback.handles) {
            if(handle.equalsIgnoreCase("cnri.test.do/dor" + dor)) {
                dor++;
            }
        }
        
        serviceID = "cnri.test.do/dor" + dor;
        
        HandleValue val;
        ArrayList vals = new ArrayList(6);
        val = new HandleValue(100, net.handle.hdllib.Common.ADMIN_TYPE, 
                Encoder.encodeAdminRecord(new AdminRecord(Util.encodeString(serviceID),200,true,false,false,false,true,false,false,true,false,false,true,true)));
        vals.add(val);
        val = new HandleValue(200, Common.ADMIN_GROUP_TYPE, Encoder.encodeValueReferenceList(new ValueReference[] { new ValueReference(Util.encodeString(serviceID),200) }));
        vals.add(val);
        val = new HandleValue(300, net.handle.hdllib.Common.STD_TYPE_HSPUBKEY, pubKeyBytes);
        vals.add(val);
        val = new HandleValue(1, Util.encodeString(DOConstants.OBJECT_SVRINFO_HDL_TYPE), Util.encodeString(serverInfoTag.toString()));
        vals.add(val);
        val = new HandleValue(2, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), Util.encodeString(serviceID));
        vals.add(val);
        val = new HandleValue(3, Common.STD_TYPE_EMAIL, Util.encodeString(email));
        vals.add(val);

        HandleValue[] values = new HandleValue[vals.size()];
        values = (HandleValue[])vals.toArray(values);

        while(true) {
            AbstractRequest request = new CreateHandleRequest(Util.encodeString(serviceID), values, authInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if(response.responseCode==AbstractMessage.RC_SUCCESS) {
                break;
            }
            if(response.responseCode==AbstractMessage.RC_HANDLE_ALREADY_EXISTS) {
                dor ++;
                serviceID = "cnri.test.do/dor" + dor;
                continue;
            }
            
            out.println("Problem with created hosted dor handle " + serviceID + ":");
            out.println(AbstractResponse.getResponseCodeMessage(response.responseCode));
            responseToPrompt("Press 'enter' to continue");
            hosted = false;
            break;
        }
        
        if(hosted) {
            val = (HandleValue)vals.get(0);
            vals.clear();
            vals.add(val);
            val = new HandleValue(1, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), Util.encodeString(serviceID));
            vals.add(val);
            
            values = new HandleValue[vals.size()];
            values = (HandleValue[])vals.toArray(values);

            byte[] handle = Util.encodeString(getNewObjectPrefixHandle(serviceID,localServerID));
            AbstractRequest request = new CreateHandleRequest(handle, values, authInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);

            if(response.responseCode!=AbstractMessage.RC_SUCCESS) {
                out.println("Unexpected result while creating handles:");
                out.println(AbstractResponse.getResponseCodeMessage(response.responseCode));
                hosted = false;
                responseToPrompt("Press 'enter' to continue");
            }
        }
    }
    
    try {
      if(infoFile!=null) {
        FileOutputStream fout = new FileOutputStream(infoFile);
        fout.write(Util.encodeString(serverInfoTag.toString()));
        fout.close();
      }
      
      out.println("-----------------------------------------------------------------------");
      out.println("\nThis repository has been configured with the following handle:");
      out.println("   "+serviceID);
      out.println("You can communicate with this server using the given handle with a ");
      out.println("digital object client program.");
      out.println();
      if(!hosted) {
          out.println("The following information:");
          out.println(serverInfoTag.toString());
          out.println("should be stored in the repository handle in a value of type ");
          out.println("'CNRI.OBJECT_SERVER_INFO' so that your server may be found by");
          out.println("digital object clients.");
          if(infoFile!=null) {
              out.println();
              out.println("This information has also been stored in the following file: ");
              out.println("  "+infoFile.getAbsolutePath());
          }
          out.println();
          out.println("This server's public key should be stored in a handle value of");
          out.println("type 'HS_PUBKEY' in the repository handle.  The public key is");
          out.println("stored in the following file:");
          out.println("  "+pubKeyFile.getAbsolutePath());
          out.println();
          out.println("Handles for digital objects stored in this repository should contain");
          out.println("a value of type 'CNRI.OBJECT_SERVER' containing the repository handle.");
      }
      out.println("By default new objects will be created with handles beginning with");
      out.println("'" + getNewObjectPrefix(serviceID,localServerID) +"'");
      out.println();
      out.println("The repository handle can also be used as a user handle for");
      out.println("authenticating; by default only it has administrative rights.");
      out.println("The private key for this authentication is stored in the file:");
      out.println("  "+privKeyFile.getAbsolutePath());
    } catch (Exception e) {
      err.println("Error storing server information: "+e);
    }
    
    responseToPrompt("Press 'enter' to continue");
    return serviceID;
  }
  
  /** Return the default configured prefix for identifiers that are auto-created 
    * by this server having the given identifers. */
  public String getNewObjectPrefix(String serviceID, String serverID) {
    return serviceID+"."+serverID+"-";
  }
  
  
  /** Return the default configured prefix for identifiers that are auto-created 
    * by this server having the given identifers. */
  public String getNewObjectPrefixHandle(String serviceID, String serverID) {
    return serviceID+"."+serverID;
  }
  
  
  /** Return whether or not the repository should redirect stderr to the errorlog file */
  public boolean getRedirectStdErr() {
    return true;
  }
  
  /** Return whether or not the repository should log accesses */
  public boolean getLogAccesses() {
    return true;
  }

  /** Return rotation interval for logs */
  public String getLogSaveInterval() {
      return Main.MONTHLY;
  }
  
  public final void generateKeys(File pubKeyFile, File privKeyFile, String purpose)
    throws Exception
  {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(HSG.KEY_ALGORITHM);
    
    out.println("\nGenerating keys for: " + purpose);
    kpg.initialize(1024);
    KeyPair keys = kpg.generateKeyPair();
    
    out.println( "\nThe private key that is about to be generated should be stored"
                 + "\nin an encrypted form on your computer.  Encryption of the"
                 + "\nprivate key requires that you choose a secret passphrase that"
                 + "\nwill need to be entered whenever the server is started."
                 + "\nNote: If you the encryption capabilities are not available"
                 + "\nin your country, your private key may be stored unencrypted."
                 + "\nPlease take all precautions to make sure that only authorized"
                 + "\nusers can read your private key." );

    boolean encrypt = getBoolean("  Would you like to encrypt your private key?", true);
    
    byte secKey[] = null;
    
    if (encrypt) {
      while (true) {
        // Read the passphrase and use it to encrypt the private key
        secKey = Util.getPassphrase("\nPlease enter the private key passphrase for " + purpose + ": ");
        byte secKey2[] = Util.getPassphrase("\nPlease re-enter the private key passphrase: ");
        if (!Util.equals(secKey, secKey2)) {
          err.println("\nPassphrases do not match!  Try again.\n");
          continue;
        } else {
          break;
        }
      }
    }

    // Get the bytes making up the private key
    PrivateKey priv = keys.getPrivate();
    byte keyBytes[] = Util.getBytesFromPrivateKey(priv);
    
    byte encKeyBytes[] = null;
    if (encrypt) {                              // Encrypt the private key bytes
      encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
      for (int i = 0; i < keyBytes.length; i++)
        keyBytes[i] = (byte)0;
      for (int i = 0; i < secKey.length; i++)
        secKey[i] = (byte)0;
    } else {
      encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_NONE);
    }

    // Save the private key to the file
    FileOutputStream keyOut = new FileOutputStream(privKeyFile);
    keyOut.write(encKeyBytes);
    keyOut.close();

    // Save the public key to the file
    PublicKey pub = keys.getPublic();
    keyOut = new FileOutputStream(pubKeyFile);
    keyOut.write(Util.getBytesFromPublicKey(pub));
    keyOut.close();
  }
  

  public synchronized boolean getBoolean(String prompt, boolean defaultAnswer)
    throws Exception
  {
    String defaultAnswerStr = defaultAnswer ? "y" : "n";
    while (true) {
      String line = responseToPrompt(prompt + "(y/n) [" + defaultAnswerStr + "]");
      if(line==null) throw new Exception("No more input");
      line = line.trim().toUpperCase();

      if (line.length() == 0)                          // User just hit Enter
        return defaultAnswer;
      
      if ((line.equals("N")) || (line.equals("NO")))
        return false;

      if ((line.equals("Y")) || (line.equals("YES")))
        return true;
      
      out.println("\nUnrecognized response, try again.");
    }
  }

  
  
  public synchronized InetAddress getIPAddress(String prompt, InetAddress defaultAddr)
    throws Exception
  {
    String localAddress = "";
    try {
      if(defaultAddr!=null) {
        localAddress = defaultAddr.getHostAddress();
      } else {
        localAddress = InetAddress.getLocalHost().getHostAddress();
      }
    } catch (Exception e) {
      localAddress = "";
    }

    if (localAddress.length() > 0)
      prompt = prompt + " [" + localAddress + "]";
    
    while(true) {
      String line = responseToPrompt(prompt);
      if ((line.equals("")) && (localAddress.length() > 0))
        line = localAddress;
      
      try {
        return InetAddress.getByName(line);
      } catch (Exception e) {
        out.println("Invalid address (" + e + "), please try again.");
      }
    }
  }



  public final String responseToPrompt(String prompt)
    throws IOException
  {
    if(in==null) return null;
    out.print("\n" + prompt + ": ");
    out.flush();
    String line = in.readLine();
    return line==null ? null : line.trim();
  }

  
 /**
  * Prompt the user for a positive integer.  No default is provided the user if
  * defaultAnswer is NO_DEFAULT.
  */
  public int getInteger(String prompt, int defaultAnswer)
    throws Exception
  {
    return getInteger(prompt, defaultAnswer, NO_LIMIT, NO_LIMIT);
  }

  
 /**
  * Prompt the user for a positive integer (within specified limits, if any).
  * No default is provided the user if defaultAnswer is NO_DEFAULT.  No minimum
  * enforcement if minimum is NO_LIMIT. No maximum enforcement if maximum is
  * NO_LIMIT.
  */
  public synchronized int getInteger(String prompt, int defaultAnswer, int minimum, int maximum)
    throws Exception
  {
    if (prompt == null || prompt.length() < 1
        || ((defaultAnswer != NO_DEFAULT) && (defaultAnswer < 0))
        || ((minimum       != NO_LIMIT)   && (minimum       < 0))
        || ((maximum       != NO_LIMIT)   && (maximum       < 0))) {
      throw new Exception("Programming Error:  getInteger(" + prompt + ", " +
                          minimum + ", " + maximum + ")");
    }
    
    String promptString = prompt.trim();
    
    if (defaultAnswer != NO_DEFAULT)
      promptString = promptString + " [" + defaultAnswer + "]";
    
    String finalInstruction = "";              // Pablum for possible spoon-feeding below
    if (minimum != NO_LIMIT)
      finalInstruction = finalInstruction + " greater than " + (minimum-1);
    if (maximum != NO_LIMIT) {
      if (finalInstruction.length() > 0)
        finalInstruction = finalInstruction + " and";
      finalInstruction = finalInstruction + " less than " + (maximum+1);
    }
    
    while (true) {
      String line = responseToPrompt(promptString).trim();
      
      if (line.length() == 0) {
        if (defaultAnswer != NO_DEFAULT)
          return defaultAnswer;
      } else {
        try {
          int number = Integer.parseInt(line);
          
          if (number >= 0
              && ((minimum == NO_LIMIT) || (number >= minimum))
              && ((maximum == NO_LIMIT) || (number <= maximum))) {
            return number;                                   // Success
          }
          throw new Exception(number + " is unacceptable.");
        } catch (Exception e) {
          out.println("ERROR: " + e);
        }
      }
      
      // Should have returned: spoon-feed the user
      out.println("\nPlease enter a positive number" + finalInstruction + ".");
    }
 }

}
