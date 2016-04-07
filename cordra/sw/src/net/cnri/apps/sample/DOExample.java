/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.sample;

import net.cnri.apps.doutilgui.AuthWindow;
import net.cnri.do_api.DigitalObject;
import net.cnri.do_api.DataElement;
import net.cnri.do_api.Repository;
import net.cnri.dobj.DOAuthentication;
import net.cnri.dobj.DOClient;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StreamPair;

import java.io.*;

/**
This class includes simple code to perform common functions using the DO API
*/

public class DOExample {
  private static boolean TESTING = false;
  
  public static void usageAndExit() {
    System.err.println("usage:  java net.cnri.apps.sample.DOExample "+
                       "[-no_encryption] "+
                       "[-r <repoID>] "+
                       "<objID> create|print|get|put|search [<searchterms>]\n");
    System.err.println("Note:  If using the create command, the -r argument is required");
    System.err.println("       The get/put commands use stdout/stdin for the data to get/put");
    System.err.println("       If using the search command, you must provide the <searchterms>");
    System.exit(1);
  }
  
  
  public static void main(String argv[]) 
    throws Exception
  {
    String repoID = null;
    String objID = null;
    String command = null;
    String searchTerms = null;
    boolean useEncryption = true;
    
    for(int i=0; i<argv.length; i++) {
      if(argv[i].equals("-no_encryption")) {
        useEncryption = false;
      } else if(argv[i].equals("-r") && i+1<argv.length) {
        repoID = argv[++i];
      } else if(objID==null) {
        objID = argv[i];
      } else if(command==null) {
        command = argv[i].toLowerCase();
      } else if(searchTerms==null) {
        searchTerms = argv[i];
      } else {
        usageAndExit();
      }
    }
    
    if(command==null || objID==null) {
      usageAndExit();
    }
    
    
    if(command.equals("create") && repoID==null) {
      usageAndExit();
    }
    
    if(command.equals("search") && searchTerms==null) {
      usageAndExit();
    }
    
    
    // display a login window to get the authentication info
    AuthWindow authWin = new AuthWindow(null);
    authWin.setVisible(true);
    if(authWin.wasCanceled()) {
      System.err.println("Authentication canceled");
      System.exit(1);
    }
    DOAuthentication auth = authWin.getAuthentication();
    
    if(TESTING) {
      System.err.println("Verified authentication.  Waiting 12 seconds");
      Thread.sleep(12000);
    }
    
    Repository repo;
    if(repoID!=null) {
      repo = new Repository(auth, repoID);
    } else {
      repo = new Repository(auth, DOClient.resolveRepositoryID(objID));
    }
    
    repo.setUseEncryption(useEncryption);
    
    if(TESTING) {
      System.err.println("Connected to repository.  Waiting 12 seconds");
      Thread.sleep(12000);
    }
    
    if(command.equals("create")) {
      
      // create a new digital object
      DigitalObject dObj = repo.createDigitalObject(objID);
      System.out.println("Object "+dObj+" was created");
      
    } else if(command.equals("print")) {
      
      // list the elements and attributes of the object
      
      DigitalObject dObj = repo.getDigitalObject(objID);
      System.out.println("Object: "+dObj);
      String objAtts[] = dObj.listAttributes();
      for(int i=0; objAtts!=null && i<objAtts.length; i++) {
        System.out.print(i==0 ? "Attributes: " : ", ");
        System.out.print(objAtts[i]);
        System.out.print(" = ");
        System.out.print(dObj.getAttribute(objAtts[i], null));
      }
      
      String elements[] = dObj.listDataElements();
      for(int i=0; elements!=null && i<elements.length; i++) {
        DataElement element = dObj.getDataElement(elements[i]);
        System.out.print(" <element id="+element.getDataElementID()+" ");
        String atts[] = element.listAttributes();
        for(int j=0; atts!=null && j<atts.length; j++) {
          System.out.print(j==0 ? "Attributes: " : ", ");
          System.out.print(atts[j]);
          System.out.print(" = ");
          System.out.print(element.getAttribute(atts[j], null));
        }
        System.out.println(">");
      }
    } else if(command.equals("get")) {
      
      // retrieve the "content" data element and write it to stdout
      
      DigitalObject dObj = repo.getDigitalObject(objID);
      DataElement content = dObj.getDataElement("content");
      InputStream in = content.read();
      
      byte buf[] = new byte[2048];
      int r;
      while((r=in.read(buf, 0, buf.length))>=0) 
        System.out.write(buf, 0, r);
      
    } else if(command.equals("put")) {
      
      // store everything sent to stdin to the "content" element
      
      DigitalObject dObj = repo.getDigitalObject(objID);
      DataElement content = dObj.getDataElement("content");
      System.err.println("Send input to stdin, terminating with ctrl-D");
      content.write(System.in);
      
    } else if(command.equals("search")) {
      // store everything sent to stdin to the "content" element
      DigitalObject dObj = repo.getDigitalObject(objID);
      HeaderSet params = new HeaderSet();
      params.addHeader("query", searchTerms);
      StreamPair io = dObj.performOperation("1037/search", params);
      InputStream in = io.getInputStream();
      HeaderSet results = new HeaderSet();
      int resultCount = 0;
      while(results.readHeaders(in)) {
        System.out.println("Search results: "+results);
        resultCount++;
      }
      System.out.println("Total results: "+resultCount);
      io.close();
    } else {
      usageAndExit();
    }
    
    if(TESTING) {
      System.err.println("Done primary function.  Waiting 12 seconds");
      Thread.sleep(12000);
      
      // retrieve the "internal.rights" data element and write it to stdout
      DigitalObject dObj = repo.getDigitalObject(objID);
      DataElement content = dObj.getDataElement("content");
      InputStream in = content.read();
      
      byte buf[] = new byte[2048];
      int r;
      int n = 0;
      while((r=in.read(buf, 0, buf.length))>=0) {
        System.out.write(buf, 0, r);
        n += r;
      }
      
      System.err.println("Done reading "+n+" bytes.  Waiting 12 seconds");
      Thread.sleep(12000);
    }
    
    // exit normally
    System.exit(0);
  }

}
