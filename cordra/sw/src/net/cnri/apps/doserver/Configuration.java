/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.cnri.simplexml.*;

import java.io.*;
import java.security.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration objects represent the configuration of a repository.  The
 * configuration, and the repository itself is bootstrapped from the repository
 * storage.  Note that this is the service-specific configuration.  Any configuration
 * that applies only to a server should be in the server-configuration and not
 * in the repository object.
 */
public class Configuration {
  static final Logger logger = LoggerFactory.getLogger(Configuration.class);
 
  public static final String CONFIG_DATA_ELEMENT = "internal.repository_config";
  private Main repository;
  private PrivateKey serverKey;
  private Storage storage = null;
  private XTag configXML = null;
  
  public Configuration(Main repo, Storage storage, PrivateKey serverKey)
    throws Exception
  {
    this.repository = repo;
    this.storage = storage;
    this.serverKey = serverKey;
    loadConfiguration();
  }

  /**
   * Adds a Knowbot operator to the repository's configuration and
   * load it.
   */
  synchronized void addKnowbot(String knowbotID, InputStream kbStream)
    throws Exception
  {
    if(configXML==null)
      throw new DOException(DOException.INTERNAL_ERROR,
                            "Configuration is not yet set");
    
    String serverID = repository.getServerID();
    XTag knowbotList = configXML.getSubTag("KNOWBOTS");
    if(knowbotList==null) {
      knowbotList = new XTag("KNOWBOTS");
      configXML.addSubTag(knowbotList);
    }
    
    // check to see if we already have a knowbot with this knowbot ID
    boolean hasID = false;
    for(int i=0; knowbotList!=null && i<knowbotList.getSubTagCount(); i++) {
      XTag knowbotTag = knowbotList.getSubTag(i);
      if(knowbotTag.getName().equalsIgnoreCase("PRELOADKB") &&
         knowbotTag.getStrValue().trim().equals(knowbotID)) {
        hasID = true;
        break;
      }
    }

    // start a thread to sign the agent and stream it to an output stream that
    // will be forwarded to the storage.
    Signature serverSig = Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(serverKey.getAlgorithm()));
    serverSig.initSign(serverKey);
    PipedOutputStream pout = new PipedOutputStream();
    PipedInputStream pin = new PipedInputStream(pout);
    AgentSignerThread ast = new AgentSignerThread("hdl:"+serverID,
                                                  serverSig,
                                                  kbStream,
                                                  pout);
    ast.start();
    storage.storeDataElement(serverID, knowbotID, pin, true, false);
    
    if(!hasID) {
      // if we're not simply replacing a knowbot with the same ID, then update
      // the knowbot list in the configuration element
      knowbotList.addSubTag(new XTag("PRELOADKB", knowbotID));
      
      // write the updated configuration
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      configXML.write(bout);
      storage.storeDataElement(serverID, Configuration.CONFIG_DATA_ELEMENT,
                               new ByteArrayInputStream(bout.toByteArray()),
                               true, false);
      Thread.sleep(1000);
    }
    
    // re-read the whole configuration
    loadConfiguration();
  }
  
  public void loadConfiguration()
    throws Exception
  {
    String repoID = repository.getServerID();
    
    InputStream configStream = null;
    configXML = null;
    configStream = storage.getDataElement(repoID, CONFIG_DATA_ELEMENT);
    if(configStream!=null) {   // load the configuration and start up
      try {
          configXML = new XParser().parse(new InputStreamReader(configStream, "UTF8"), false);
      } finally {
          configStream.close();
      }
    } else {
      configXML = new XTag("config");
    }
    
    XTag knowbotList = configXML.getSubTag("KNOWBOTS");
    repository.clearServiceBots();
    
    for(int i=0; knowbotList!=null && i<knowbotList.getSubTagCount(); i++) {
      XTag knowbotTag = knowbotList.getSubTag(i);
      if(knowbotTag.getName().equalsIgnoreCase("PRELOADKB")) {
        try {
          logger.info("Suppressing loading of dynamic knowbot: "+knowbotTag.getStrValue());
          //repository.loadServiceBot(storage.getDataElement(repoID, knowbotTag.getStrValue()));
        } catch (Exception e) {
          logger.error("Error loading service",e);
        }
      }
    }
  }
  

}
