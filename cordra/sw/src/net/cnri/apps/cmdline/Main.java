/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.cmdline;

import net.cnri.apps.doutilgui.AuthWindow;
import net.cnri.dobj.*;
import net.cnri.util.IOForwarder;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * This is a simple utility to invoke an operation on an object from the command-line.
 */
public class Main {
  private DOAuthentication myID = null;

  private static final void printUsageAndExit() {
    System.err.println("usage: do-invoke [-a] [-r <repositoryID>] <objectID> <operationID> "+
                       "[param1=value1 [param2=value2]...]");
    System.err.println("Possible operationIDs include:");
    System.err.println(" 1037/0  - List Available Operations");
    System.err.println(" 1037/1  - Create Object");
    System.err.println(" 1037/2  - Declare Object to be of the type defined by parameter <type>");
    System.err.println(" 1037/3  - Un-declare Object to be of the type defined by parameter <type>");
    System.err.println(" 1037/4  - Ask if the object has the type defined by parameter <type>");
    System.err.println(" 1037/5  - Get the data element named by parameter <elementid>");
    System.err.println(" 1037/6  - Store data into the element named by parameter <elementid>");
    System.err.println(" 1037/7  - Delete the data element named by parameter <elementid>");
    System.err.println(" 1037/8  - List the data elements");
    System.err.println(" 1037/9  - List the object types");
    System.err.println(" 1037/10 - List the objects in the repository (repo objects only)");
    System.err.println(" 1037/11 - Query the audit log (repo objects only)");
    System.err.println(" 1037/12 - Retrieve part of an audit log transaction (repo objects only)");
    System.err.println(" 1037/13 - Inject a knowbot into the server (repo objects only)");
    System.err.println("-a -> Authenticate as anonymous");
    System.exit(1);
  }
  
  public static void main(String argv[])
    throws Exception
  {
    String objectID = null;
    String operationID = null;
    String repoID = null;
    boolean anonymous = false;
    HeaderSet params = new HeaderSet();

    for(int i=0; i<argv.length; i++) {
      String arg = argv[i];
      if(arg.startsWith("-")) {
        String option = arg.substring(1);
        if(option.equals("r") && repoID==null && i+1<argv.length) {
          repoID = argv[++i];
          continue;
        } else if(option.equals("a")) {
          anonymous = true;
        } else {
          printUsageAndExit();
        }
      } else if(objectID==null) {
        objectID = arg;
      } else if(operationID==null) {
        operationID = arg;
      } else {
        int eqIdx = arg.indexOf('=');
        if(eqIdx<0) {
          params.addHeader(arg, "");
        } else {
          params.addHeader(arg.substring(0, eqIdx),
                           arg.substring(eqIdx+1));
        }
      }
    }
    if(operationID==null)
      printUsageAndExit();

    // get the authentication details from the user
    DOAuthentication auth;
    if(anonymous) {
      auth = AbstractAuthentication.getAnonymousAuth();
    } else {
      AuthWindow authWin = new AuthWindow(null);
      authWin.setVisible(true);
      if(authWin.wasCanceled()) {
        System.exit(1);
      }
      auth = authWin.getAuthentication();
      authWin.dispose();
    }
    
    DOClient doClient = new DOClient(auth);
    StreamPair io = null;
    // perform the operation
    try {
      io = doClient.performOperation(repoID, objectID, operationID, params);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
    InputStream in = io.getInputStream();
    OutputStream out = io.getOutputStream();

    // read input and simultaneously write output to the operation
    System.err.println("Enter Operation Input, then press Control-D (Control-Z on Windows)");
    System.err.flush();
    byte buf[] = new byte[2048];
    int r;

    IOForwarder fwdOut = new IOForwarder(System.in, out);
    new Thread(fwdOut, "stdin -> server").start();
    IOForwarder fwdIn = new IOForwarder(in, System.out);
    new Thread(fwdIn, "server -> stdout").start();
    
    while(!fwdIn.finished || !fwdOut.finished) {
      Thread.sleep(200);
    }

    int exitStatus = 0;
    if(fwdOut.err!=null) {
      fwdOut.err.printStackTrace(System.err);
      exitStatus = 1;
    }
    if(fwdIn.err!=null) { 
      fwdIn.err.printStackTrace(System.err);
      exitStatus = 1;
    }
    System.exit(exitStatus);
  }

}
