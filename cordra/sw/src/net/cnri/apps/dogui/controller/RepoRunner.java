/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.apps.doserver.*;
import net.cnri.apps.dogui.view.*;
import net.cnri.dobj.*;
import net.handle.hdllib.*;
import net.cnri.util.*;
import net.cnri.simplexml.*;
import java.security.*;
import java.security.interfaces.*;
import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;

public class RepoRunner
  extends DNASynchronizer
{
  // this is used to avoid multiple read/writes to config file
  private static final Object resolverFileLock = new Object();
  
  private static int LUCENE_BOT_VER = 31;
  private static int STANDARD_BOT_VER = 14;
  
  public static final String NEW_SERVER_ID_PREFIX = "cnri.test.sean/loc.";
  
  private File serverDir = null;
  private Main mainObj;
  private AdminToolUI appUI = null;
  private String repoType = null;
  
  private int serverPort;
  private int httpPort;
  private int sslPort;
  private String serverDesc;
  
  private net.cnri.apps.doserver.Main serverMain = null;
  private Thread serverThread = null;
  private ServerRunnable serverRunnable = null;
  private Object serverRunLock = new Object();
  private boolean usesIndexer = false;
  private RepoRunner indexRepo = null;
  private ArrayList reposToIndex = new ArrayList();
  
  private boolean isRunning = false;
  
  /** Construct the synchronizer that provides an interface to start and
    * stop the server. */
  public RepoRunner(File serverDir, Main main, String repoType, 
                    int serverPortOffset,
                    String serverDesc, RepoRunner indexRepo) {
    this.serverDir = serverDir;
    this.serverPort = net.cnri.apps.doserver.Main.DEFAULT_PORT + serverPortOffset;
    this.httpPort = net.cnri.apps.doserver.Main.DEFAULT_HTTP_PORT + serverPortOffset;
    this.sslPort = net.cnri.apps.doserver.Main.DEFAULT_SSL_PORT + serverPortOffset;
    this.serverDesc = serverDesc;
    this.indexRepo = indexRepo;
    this.usesIndexer = indexRepo==null;
    this.mainObj = main;
    this.appUI = main.getUI();
    this.repoType = repoType;
    this.serverRunnable = new ServerRunnable();
    
    //changeState(STATE_RUNNING, -1, "Starting repository...");
    changeState(STATE_STOPPED, 0f, "Not running");
    
    if(usesIndexer) {
      new Thread("Index Configuration") {
        public void run() {
          while(true) {
            if(hasBeenKilled()) break;
            
            synchronized(reposToIndex) {
              try { reposToIndex.wait(9000); } catch (Throwable t) {}
              
              if(reposToIndex.size()<=0) continue;
              if(serverMain==null) continue;
              
              try {
                Storage storage = serverMain.getStorage();
                String svrID = serverMain.getServerID();
                
                StreamTable indexConfig = new StreamTable();
                InputStream in = null;
                try {
                  if(storage.doesDataElementExist(svrID, "lucene.config")) {
                    in = storage.getDataElement(svrID, "lucene.config");
                    indexConfig.readFrom(in);
                  }
                } catch (Exception e) {
                  System.err.println("Error parsing index configuration: "+e);
                } finally {
                  try { in.close(); } catch (Throwable t) {}
                }
                
                boolean wasChanged = false;
                StreamVector v = (StreamVector)indexConfig.get("sources");
                if(v==null) {
                  v = new StreamVector();
                  indexConfig.put("sources", v);
                }
//                // make sure we index any repositories that want it
//                for(int i=0; i<reposToIndex.size(); i++) {
//                  String repoID = (String)reposToIndex.get(i);
//                  if(!v.contains(repoID)) {
//                    v.add(repoID);
//                    wasChanged = true;
//                  }
//                }
                v.clear();
                
                // make sure we index any un-indexed objects from other indexes that we search
                String indexIDs[] = 
                  StringUtils.split(appUI.getUserObject().
                                    getAttribute(AdminToolUI.INDEXES_ATT, "").trim(),' ');
                StreamVector iv = new StreamVector();
                indexConfig.put("srcindexes", iv);
                for(int i=0; i<indexIDs.length; i++) {
                  String indexID = indexIDs[i];
                  if(indexID.trim().length()<=0) continue;
                  if(!iv.contains(indexID)) {
                    iv.addElement(indexID);
                    wasChanged = true;
                  }
                }
                for(int i=0; i<reposToIndex.size(); i++) {
                  String repoID = (String)reposToIndex.get(i);
                  if(!iv.contains(repoID)) {
                    iv.add(repoID);
                    wasChanged = true;
                  }
                }
                
                
                if(wasChanged) { // store the updated index list
                  StringWriter w = new StringWriter();
                  indexConfig.writeTo(w);
                  byte encodedCfg[] = w.toString().getBytes("UTF8");
                  
                  storage.storeDataElement(serverMain.getServerID(),
                                           "lucene.config", 
                                           new ByteArrayInputStream(encodedCfg),
                                           true, false);
                }
              } catch (Exception e) {
                System.err.println("Error adding repositories to index: "+e);
                e.printStackTrace(System.err);
              }
            }
          }
        }
      }.start();
    }
  }
  
  
  /** This is called by other RepoRunners in order to notify them that 
    * they want to be indexed */
  public void indexMe(String repoIDToBeIndexed) {
    synchronized(reposToIndex) {
      if(!reposToIndex.contains(repoIDToBeIndexed)) {
        reposToIndex.add(repoIDToBeIndexed);
        reposToIndex.notifyAll();
      }
    }
  }
  
  /** Returns true if the server is running and responding to requests */
  public boolean isRunning() {
    if(this.isRunning) {
      net.cnri.apps.doserver.Main sm = serverMain;
      if(sm!=null) return sm.isListening();
    }
    return false;
  }
  
  private class ServerRunnable
    implements Runnable
  {
    
    public void run() {
      synchronized(serverRunLock) {
        if(serverMain==null) {
          try {
            File privKeyFile = new File(serverDir, net.cnri.apps.doserver.Main.PRIVATE_KEY_FILE);
            FileInputStream fin = new FileInputStream(privKeyFile);
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte buf[] = new byte[1000];
            int r;
            while((r=fin.read(buf))>=0) bout.write(buf, 0, r);
            
            if(net.handle.hdllib.Encoder.readInt(buf, 0)==1) {
              // the private key was encrypted (usually with null)
              buf = Util.decrypt(bout.toByteArray(), null);
            }
            
            PrivateKey privKey = Util.getPrivateKeyFromBytes(buf, 0);
            
            serverMain = new net.cnri.apps.doserver.Main(privKey, serverDir, null);
            mainObj.getUI().setRepoID(repoType, serverMain.getServerID());
            
            // add or update the standard operations knowbot, if necessary
            if(mainObj.doesKnowbotNeedUpdate(repoType, "standardops", STANDARD_BOT_VER)) {
              InputStream opsBotIn = getClass().getResourceAsStream("/standardopsbot.jar");
              if(opsBotIn!=null) {
                System.err.println("Updating standardops knowbot");
                serverMain.addOperator("standardops", opsBotIn);
                mainObj.setKnowbotUpdated(repoType, "standardops", STANDARD_BOT_VER);
              } else {
                System.err.println("Error: standardops knowbot update not available!");
              }
            }
            
            // add or update the lucene indexing knowbot
            if(usesIndexer) {
              // update the lucene knowbot, if necessary
              // lucene indexer is now built into repository, so the knowbot is no longer
              // necessary
              /*
              if(mainObj.doesKnowbotNeedUpdate(repoType, "lucene", LUCENE_BOT_VER)) {
                InputStream opsBotIn = getClass().getResourceAsStream("/lucenebot.jar");
                if(opsBotIn!=null) {
                  System.err.println("Updating lucene knowbot");
                  serverMain.addOperator("lucene", opsBotIn);
                } else {
                  System.err.println("Error:  lucene knowbot update not available!");
                }
                mainObj.setKnowbotUpdated(repoType, "lucene", LUCENE_BOT_VER);
              }
               */
            }
            
            setServerPermissions(serverMain);
            if(usesIndexer) {
              // tell the indexer what authentication it should use when scanning objects
              serverMain.setKnowbotMapping("lucene-indexing-auth", 
                                           mainObj.getUI().getAuthentication(false));
              
              serverMain.setKnowbotMapping("lucene-indexing-keyring",
                                           mainObj.getUI().getKeyRing());
              
              
              // lucene indexer has to be invoked in order to kick off its 
              // asynchronous stuff, but we don't care about the result...
              /* no longer necessary, as indexer is built into repository
              StreamPair io = null;
              try {
                serverMain.performOperation(serverMain.getServerID(),
                                            "1037/search",
                                            null, new ByteArrayInputStream(new byte[0]),
                                            new ByteArrayOutputStream());
              } catch (Exception e) {
                System.err.println("Error starting asynchronous indexer: "+e);
                e.printStackTrace(System.err);
              } finally {
                try { io.close(); } catch (Throwable t) {}
              }
               */
            }
            
            if(indexRepo!=null) {
              indexRepo.indexMe(serverMain.getServerID());
            }
          } catch (Exception e) {
            System.err.println("Error creating server object: "+e);
            changeState(STATE_STOPPED, -1, "Error: "+e);
            e.printStackTrace(System.err);
          }
        }
      }
      
      try {
        isRunning = true;
        updateStatus(-2, "Serving requests");
        serverMain.setConfigVal(net.cnri.apps.doserver.Main.HTTP_PORT_KEY, 
                                String.valueOf(httpPort));
        serverMain.serveRequests();
        isRunning = false;
        changeState(STATE_STOPPED, -1, "Server stopped normally");
      } catch (Throwable t) {
        isRunning = false;
        changeState(STATE_STOPPED, -1, "Error: "+t);
        t.printStackTrace();
      } finally {
        isRunning = false;
      }
    }
    
    public void stopNow() {
      synchronized(serverRunLock) {
        if(!isRunning) return; // already stopped
        
        try {
          if(serverMain!=null) {
            serverMain.shutdown();
            serverMain = null;
          }
        } catch (Throwable t) {
          System.err.println("Error telling server to stop: "+t);
        }
        try { serverRunLock.notifyAll(); } catch (Exception e) {}
      }
    }
    
  }
  
  private void startServer() {
    updateStatus(-1, "Starting server...");
    synchronized(serverRunLock) {
      if(isRunning) return;
      isRunning = true;
      serverThread = new Thread(serverRunnable);
      serverThread.start();
    }
  }
  
  private void stopServer() {
    updateStatus(-1, "Stopping server...");
    serverRunnable.stopNow();
  }
  
  //updateStatus(-1, "Error synchronizing with "+srcSvcID+": "+e);
  //changeState(STATE_STOPPED, -1, "Finished publishing objects");
  
  /** Perform synchronization.  This is called from a separate thread.  If this
    * is a long-running method then it should occasionally call getState() to see
    * if it should keep running. */
  void performSynchronizationTask() {
    try {
      if(isRunning) return;
      int state = getState();
      File finishedFile = new File(serverDir, "setupdone");
      boolean setupNeeded = state==STATE_RUNNING && 
        (!serverDir.exists() || !finishedFile.exists());
      if(setupNeeded) {
        updateStatus(-1, "Configuring new repository...");
        if(!serverDir.exists()) serverDir.mkdirs();
        
        File privKeyFile = new File(serverDir, 
                                    net.cnri.apps.doserver.Main.PRIVATE_KEY_FILE);
        File pubKeyFile = new File(serverDir, 
                                   net.cnri.apps.doserver.Main.PUBLIC_KEY_FILE);
        
        updateStatus(-1, "Generating repository keys...");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(HSG.KEY_ALGORITHM);
        kpg.initialize(1024);
        KeyPair keys = kpg.generateKeyPair();
        PrivateKey priv = keys.getPrivate();
        PublicKey pub = keys.getPublic();
        byte privKeyBytes[] = Util.getBytesFromPrivateKey(priv);
        byte pubKeyBytes[] = Util.getBytesFromPublicKey(pub);
        
        FileOutputStream keyOut = new FileOutputStream(privKeyFile);
        keyOut.write(Util.encrypt(privKeyBytes, null));
        keyOut.close();
        
        keyOut = new FileOutputStream(pubKeyFile);
        keyOut.write(pubKeyBytes);
        keyOut.close();
        
        updateStatus(-1, "Configuring server...");
        net.cnri.apps.doserver.Main newServer;
        // This needs to be synchronized in case the new server needs to read the resolver file for other reasons, e.g. SSL
        synchronized (resolverFileLock) {
            newServer = 
                new net.cnri.apps.doserver.Main(priv, serverDir, new CustomSetupHelper());
        }
        
        mainObj.getUI().setRepoID(repoType, newServer.getServerID());
        newServer.shutdown();
        
        mainObj.savePreferences();
        
        FileOutputStream doneOut = new FileOutputStream(new File(serverDir, "setupdone"));
        doneOut.write("done".getBytes());
        doneOut.close();
        
        updateStatus(-1, "Finished configuring server...");
      } else {
        if(state==STATE_RUNNING && !isRunning) {
          // need to start the server
          startServer();
        } else if(state==STATE_STOPPED && isRunning) {
          // need to stop the server
          stopServer();
        }
      }
    } catch (Throwable t) {
      changeState(STATE_BROKEN, -1f, "Error running server: "+t);
      t.printStackTrace(System.err);
    }
  }
  
  
  private void setServerPermissions(net.cnri.apps.doserver.Main svr) 
    throws Exception
  {
    // set up the default permissions...
    HeaderSet params = new HeaderSet();
    String serverID = svr.getServerID();
    StringBuffer sb = new StringBuffer();
    
    // the server itself can do anything
    sb.append("accept:").append(serverID).append("\t*\t*\n");
    // anyone can do anything to any default object 
    sb.append("accept:").append("*\t").append("*\t*\n");
    byte ruleBytes[] = sb.toString().getBytes();
    
    params.removeAllHeaders();
    params.addHeader("elementid", "internal.default_rights");
    svr.performOperation(serverID,
                         DOConstants.STORE_DATA_OP_ID,
                         params, 
                         new ByteArrayInputStream(ruleBytes),
                         new ByteArrayOutputStream());
    sb = new StringBuffer();
    // the server itself can do anything
    sb.append("accept:*\t").append(serverID).append("\t*\n");
    // anonymous can only do some things
    String ops[] = {
      DOConstants.GET_DATA_OP_ID,
      DOConstants.GET_REPO_TXNS_OP_ID,
      DOConstants.GET_SERIALIZED_FORM_OP_ID,
      DOConstants.DOES_OBJ_EXIST_OP_ID,
      DOConstants.LIST_DATA_OP_ID,
      DOConstants.GET_ATTRIBUTES_OP_ID,
      DOConstants.LIST_OPERATIONS_OP_ID,
      DOConstants.LIST_OBJECTS_OP_ID,
      DOConstants.CREATE_OBJ_OP_ID,
      "1037/search",
    };
    for(int i=0; i<ops.length; i++)
      sb.append("accept:").append(ops[i]).append("\t*\t*\n");
    ruleBytes = sb.toString().getBytes();
    
    params.removeAllHeaders();
    params.addHeader("elementid", "internal.rights");
    svr.performOperation(serverID,
                         DOConstants.STORE_DATA_OP_ID,
                         params, 
                         new ByteArrayInputStream(ruleBytes),
                         new ByteArrayOutputStream());
  }
  
  
  private class CustomSetupHelper
    extends SetupHelper
  {
    private String customSvrID;
    
    CustomSetupHelper() throws Exception {
      customSvrID = Integer.toHexString(new java.util.Random().nextInt());
    }
    
    public String getServerDescription() { return serverDesc; }
    public int getListenPort() { return serverPort; }
    public int getHTTPPort() { return httpPort; }
    public int getSSLPort() { return sslPort; }
    
    public InetAddress getExternalAddress() 
      throws Exception
    {
      return InetAddress.getByName("127.0.0.1");
    }
    public InetAddress getInternalAddress() 
      throws Exception
    {
      return InetAddress.getByName("127.0.0.1");
    }
    
    public String getUniqueServerID() { return "1"; }
    public boolean getRedirectStdErr() { return false; }
    
    public String getUniqueServiceID() {
      return NEW_SERVER_ID_PREFIX + customSvrID;
    }
  
    public boolean verifyID(String serviceID, String localServerID) {
      return true;
    }
    
    
    public void registerServer(String serverID, String localServerID, 
                               byte pubKeyBytes[], InetAddress externalAddr,
                               int port, int sslPort, int httpPort, String description,
                               File infoFile) 
      throws Exception
    {
      XTag serverInfoTag = new XTag("serverinfo");
      XTag serverTag = new XTag("server");
      serverInfoTag.addSubTag(serverTag);
      serverTag.addSubTag(new XTag("id", localServerID));
      serverTag.addSubTag(new XTag("label", description));
      serverTag.addSubTag(new XTag("publickey", Util.decodeHexString(pubKeyBytes, false)));
      serverTag.addSubTag(new XTag("hostaddress", externalAddr.getHostAddress()));
      serverTag.addSubTag(new XTag("port", String.valueOf(port)));
      serverTag.addSubTag(new XTag("ssl-port", String.valueOf(sslPort)));
      serverTag.addSubTag(new XTag("protocol", "DOP"));
      
      File hdlConfig = DOClient.getResolver().getConfigFile();
      
      synchronized(resolverFileLock) {
        XTag config = null;
        if(hdlConfig.exists()) {
          config = new XParser().parse(new InputStreamReader(new FileInputStream(hdlConfig),"UTF-8"), false);
        }
        if(config==null) config = new XTag("hsconfig");
        
        // Update the local handles in the resolver.xml config file to include 
        // an override reference to the new server
        XTag localHdlsTag = config.getSubTag("local_handles");
        if(localHdlsTag==null) {
          localHdlsTag = new XTag("local_handles");
          config.addSubTag(localHdlsTag);
        }
        XTag hdlTag = new XTag("handle");
        hdlTag.setAttribute("handle", serverID);
        hdlTag.setAttribute("case_sensitive", true);
        hdlTag.setAttribute("override_type", "always");
        XTag valTag = new XTag("hdlvalue");
        valTag.setAttribute("type", "CNRI.OBJECT_SERVER_INFO");
        valTag.setValue(serverInfoTag.toString());
        hdlTag.addSubTag(valTag);
        
        valTag = new XTag("hdlvalue");
        valTag.setAttribute("type", "HS_PUBKEY");
        valTag.setAttribute("encoding", "hex");
        valTag.setValue(Util.decodeHexString(pubKeyBytes, false));
        hdlTag.addSubTag(valTag);
        
        valTag = new XTag("hdlvalue");
        valTag.setAttribute("type", "CNRI.OBJECT_SERVER");
        valTag.setValue(serverID);
        hdlTag.addSubTag(valTag);
        
        localHdlsTag.addSubTag(hdlTag);
        
        FileWriter configWriter = new FileWriter(hdlConfig);
        config.write(configWriter);
        configWriter.close();
      }
    }
    
  }
  
  
}
