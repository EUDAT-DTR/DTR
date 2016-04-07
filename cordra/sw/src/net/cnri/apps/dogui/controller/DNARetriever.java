/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.apps.dogui.view.AdminToolUI;
import net.cnri.do_api.DigitalObject;
import net.cnri.do_api.Repository;
import net.cnri.dobj.DOClient;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StreamPair;
import net.cnri.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class DNARetriever 
extends DNASynchronizer
{
  private static final int QUERY_STEP = 0;
  private static final int DELETE_STEP = 1;
  private static final int RETRIEVE_STEP = 2;
  private static final int FINISHED_STEP = 3;
  
  public static final String SEARCH_OP_ID = "1037/search";
  private AdminToolUI ui;
  
  private ArrayList objectsToSync = new ArrayList();
  
  private boolean needsReset = false;
  private int currentStep = QUERY_STEP;
  private Object stepLock = new Object();
  
  /** Copy all digital objects from the given source to the destination. */
  public DNARetriever(AdminToolUI ui) {
    this.ui = ui;
  }
  
  private void startOver() {
    synchronized(stepLock) {
      this.needsReset = true;
      this.currentStep = QUERY_STEP;
      this.objectsToSync.clear();
      updateStatus(0f, "Resetting synchronization");
    }
  }
  
  /** If we are not in need of a reset, report our progress, set 
    * a status message and return true.  If we are in need of a reset,
    * just return false. */
  private boolean reportAndCheckErrors(float progress, String statusMsg) {
    if(getState()!=STATE_RUNNING) return false;
    synchronized(stepLock) {
      if(needsReset) return false;
      updateStatus(progress, statusMsg);
    }
    return true;
  }
  
  /** Perform synchronization.  This is called from a separate thread.  If this
    * is a long-running method then it should occasionally call getState() to see
    * if it should keep running. */
  void performSynchronizationTask() {
    int step;
    synchronized(stepLock) {
      if(needsReset) needsReset = false;
      step = currentStep;
    }
    if(step==FINISHED_STEP) {
      if(!reportAndCheckErrors(-1, "Finished retrieval")) return;
      changeState(STATE_STOPPED, -1f, "Finished retrieval");
    }
    
    if(step==QUERY_STEP) {
      // retrieve a list of all objects that match our parameters
      if(!reportAndCheckErrors(0f, "Querying objects")) return;
      
      String searchTerms = ui.getMain().prefs().getStr("search_terms", "");
      if(searchTerms.trim().length()<=0) {
        changeState(STATE_STOPPED, -1f, "No search terms specified");
        return;
      }

      DigitalObject userObj = null;
      String queryStr = null;
      String indexIDs[] = null;
      try {
        userObj = ui.getUserObject();
        if(userObj==null) {
          updateStatus(getProgress(), "User object not available");
          return;
        }
        
        queryStr = userObj.getAttribute(AdminToolUI.SYNC_QUERY_ATT, "");
        if(queryStr.trim().length()<=0) {
          updateStatus(getProgress(), "Syncronizer query not set");
          return;
        }
        
        String indexes_att = userObj.getAttribute(AdminToolUI.INDEXES_ATT, "");
        indexIDs =
          StringUtils.split(indexes_att.trim(),' ');
        
        if(indexIDs.length<=0 || indexes_att.equals("")) {
          updateStatus(getProgress(), "Search Indexes not set");
          return;
        }
        
      } catch (Exception e) {
        updateStatus(getProgress(), "Unknown error: "+e);
      }
      
      StreamPair io = null;
      try {
        // the actual query happens here
        // query the list of objects and add them to the objectsToSync list
        objectsToSync.clear();
        
        for(int i=0; i<indexIDs.length; i++) {
          if(indexIDs[i]==null || indexIDs[i].trim().length()<=0) continue;
          
          Repository indexObject = ui.getRepositoryByID(indexIDs[i]);
          if(indexObject==null) continue;
          
          HeaderSet params = new HeaderSet();
          params.addHeader("query", searchTerms);
          io = indexObject.performOperation(SEARCH_OP_ID, params);
          io.getOutputStream().close();
          InputStream in = io.getInputStream();
          HeaderSet line = new HeaderSet();
          long count = 0;
          String objectID = "<waiting>";
          while(line.readHeaders(in)) {
            //System.err.println(">=>=>=>=[searchresults]:"+line);
            if(line.hasHeader("objectid")) {
              objectsToSync.add(line);
              line = new HeaderSet();
            } else {
              System.err.println("Got non-object line from search results: "+line);
            }
            
            if(needsReset) return;
            
            if(++count%50==0) {
              if(!reportAndCheckErrors(0.15f, "Receiving search results: (#"+count+") "+objectID))
                return;
            }
          }
          if(!reportAndCheckErrors(1/3f, "Finished querying objects"))
            return;
        }
        step = DELETE_STEP;
      } catch (Exception e) {
        changeState(STATE_STOPPED, -1, "Error querying objects: "+e);
        e.printStackTrace(System.err);
      } finally {
        try { io.close(); } catch (Throwable t) {}
      }
    }
    
    if(step==DELETE_STEP) {
      
      // delete all objects that we no longer want in our cache
      if(!reportAndCheckErrors(0.35f, "Deleting stale objects")) return;
      try {
        Repository destination = ui.getRepository(AdminToolUI.INBOX);
        if(destination==null) {
          updateStatus(getProgress(), "Waiting for inbox to be initialized");
          return;
        }
        
        Iterator it = destination.listObjects();
        ArrayList objectsInRepo = new ArrayList();
        // gather a list of all objects in the repository
        while(it.hasNext()) {
          String objectID = (String)it.next();
          if(objectID==null || objectID.trim().length()<=0) {
            //System.err.println("Received object info with no objectid: "+info);
            continue;
          }
          if(needsReset) return;
          objectsInRepo.add(objectID);
        }
        
        long count = 0;
        it = objectsInRepo.iterator();
        // delete all of the objects in the repository that aren't in the query results
        while(it.hasNext()) {
          if(needsReset) return;
          
          String objectID = (String)it.next();
          if(objectID.equals(destination.getID())) // don't delete the repository object!
            continue;
          
          boolean hasObject = false;
          for(int i=objectsToSync.size()-1; i>=0; i--) {
            if(((HeaderSet)objectsToSync.get(i)).getStringHeader("objectid","").equals(objectID)) {
              hasObject = true;
              break;
            }
          }
          
          if(!hasObject) {
            // need to delete the given object...
            System.err.println("Deleting "+objectID+" from "+destination);
            if(++count%50==0) {
              if(!reportAndCheckErrors(0.35f, "Deleting stale object (#"+count+") "+objectID)) {
                return;
              }
            }
            destination.deleteDigitalObject(objectID);
          }
        }
        step = RETRIEVE_STEP;
      } catch (Exception e) {
        reportAndCheckErrors(0.35f, "Error deleting stale objects: "+e);
        e.printStackTrace(System.err);
        return;
      }
    }
    
    if(step==RETRIEVE_STEP) {
      // retrieve any new objects that should be in our cache
      if(!reportAndCheckErrors(0.4f, "Copying objects into cache"))
        return;
      float progress = 0.4f;
      try {
        int count = 0;
        int numObjects = objectsToSync.size();
        
        Repository destination = ui.getRepository(AdminToolUI.INBOX);
        if(destination==null) {
          updateStatus(getProgress(), "Inbox repository not yet initialized");
          return;
        }
        
        while(objectsToSync.size()>0) {
          HeaderSet searchResult = (HeaderSet)objectsToSync.get(0);
          String objectID = searchResult.getStringHeader("objectid", "");
          String repoID = searchResult.getStringHeader("repoid", "");
          if(objectID.trim().length()<=0) continue;
          
          if(destination.verifyDigitalObject(objectID)) {
            // the object is already in the local repository
            objectsToSync.remove(searchResult);
            continue;
          }
          
          if(count%10==0) {
            progress = (float)(0.4f + (0.6*(count/(float)numObjects)));
            if(!reportAndCheckErrors(progress, "Copying object #"+count+": "+objectID)) {
              return;
            }
          }
          if(needsReset) return;
          count++;
          
          System.err.println("Getting repo="+repoID+"; object="+objectID);
          if(repoID==null) 
            repoID = DOClient.resolveRepositoryID(objectID);
          Repository repo = ui.getRepositoryByID(repoID);
          
          try {
            destination.copyObjectFrom(repo, objectID);
          } catch (DOException e) {
            if(e.getErrorCode()==DOException.NO_SUCH_OBJECT_ERROR) {
              System.err.println("Warning:  Object "+objectID+
                                 " was removed from source, deleting local version");
              if(destination.verifyDigitalObject(objectID)) {
                destination.deleteDigitalObject(objectID);
              }
            } else {
              throw e;
            }
          }
          
          objectsToSync.remove(searchResult);
        }
        changeState(DNASynchronizer.STATE_STOPPED, -1, "Finished object retrieval");
      } catch (Exception e) {
        reportAndCheckErrors(progress, "Error retrieving objects: "+e);
        e.printStackTrace(System.err);
        return;
      }
    }
  }
  
  
}
