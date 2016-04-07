/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.knowbots.lib.*;
import java.io.*;
import java.security.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AgentSignerThread
  extends Thread
{
  static final Logger logger = LoggerFactory.getLogger(AgentSignerThread.class);
    
  private Signature sig;
  private String signerID;
  private InputStream in;
  private OutputStream out;
    
  AgentSignerThread(String signerID, Signature sig,
                    InputStream in, OutputStream out) {
    this.sig = sig;
    this.signerID = signerID;
    this.in = in;
    this.out = out;
  }

  public void run() {
    try {
      AgentStreamSigner signer = new AgentStreamSigner(signerID, sig);
      signer.signAgentStream(in, out);
    } catch (Exception e) {
        logger.error("Error signing agent stream",e);
    } finally {
      try { out.close(); } catch (Exception e) {}
    }
  }
}

