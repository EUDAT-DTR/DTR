/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An AuditLogArchiver object will archive the logs for completed operations.
 */
public class AuditLogArchiver {
  static final Logger logger = LoggerFactory.getLogger(AuditLogArchiver.class);

  private AuditLog log = null;
  private Thread archiveThread = null;
  private boolean keepGoing = true;
  private String archiveLock = "archiveLock";


  public AuditLogArchiver(AuditLog log) {
    this.log = log;
  }

  /** Compresses all logs that are compressable with the given directory.
    * If the assumeNoneRunning flag is true then we can assume that no 
    * operations are currently in progress that are using any of the audit 
    * logs.
    */
  public void compressLogs(File dir, boolean assumeNoneRunning) 
    throws Exception
  {
    compressLogs(dir, 0, assumeNoneRunning);
  }
   
  /** Returns an InputStream with the contents of the given file, whether
    * archived or not.
    * @param txnDir The directory containing the record, if not archived
    * @param entryPart the part of the audit log entry to be returned
    * @param dirDepth the depth of the archive directories/zip files
    */
  public InputStream getAuditLogEntry(File txnDir, String entryPart, int dirDepth) {
    synchronized(archiveLock) {
      if(txnDir.exists()) {
        File partFile = new File(txnDir, entryPart);
        if(partFile.exists()) {
          try {
            return new FileInputStream(partFile);
          } catch (Exception e) {
            logger.error("Error returning archived transaction",e);
            return null;
          }
        }
      }
      
      // the transaction has been archived.  Locate and return the archived entry
      // by traversing up the directory hierarchy until we find either a 
      // non-archived 
      File currentDir = txnDir.getParentFile();
      File archiveFile = null;
      String currentSegment = txnDir.getName();
      Vector segments = new Vector();
      segments.addElement(entryPart);
      for(int i=0; i<dirDepth; i++) {
        // check for the existence of parent directories until we find one
        // that exists, and look for our transaction in the archive for it
        File testFile = new File(currentDir, currentSegment+".zip");
        if(testFile.exists()) {
          archiveFile = testFile;
          break;
        } else {
          segments.addElement(currentSegment+".zip");
          currentSegment = currentDir.getName();
          currentDir = currentDir.getParentFile();
        }
      }
      
      // we have the top-most level archive file that contains this
      // operation
      if(archiveFile==null) {
        logger.warn("Warning: Archive entry for '"+
                           txnDir.getAbsolutePath()+"' not found");
        return null;
      }
      
      // read the nested zip files in the archive until we arrive at the
      // log for the desired transaction
      try {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(archiveFile));
        while(true) {
          // this loop traverses the nested zip streams
          String segment = (String)segments.lastElement();
          segments.remove(segments.size()-1);
          
          while(true) {
            // this loop searches for the nested entry with the matching name
            // in the current zip stream
            ZipEntry zipEntry = zin.getNextEntry();
            if(zipEntry==null) {
              logger.warn("Warning: Entry '"+segment+"' not found in archive!");
              return null;
            } else if(zipEntry.getName().equals(segment)) {
              if(segments.size()==0) {
                return zin;
              } else {
                // return to the outer loop to traverse the nested zip file
                zin = new ZipInputStream(zin);
                break;
              }
            }
          }

        }
      } catch (Exception e) {
        logger.error("Error searching for archived transaction",e);
        return null;
      }
    }
  }
  
  /** Compresses all logs that are compressable with the given directory.
    * If the assumeNoneRunning flag is true then we can assume that no 
    * operations are currently in progress that are using any of the audit 
    * logs.
    */
  private synchronized boolean compressLogs(File dir, int dirDepth, 
                                            boolean assumeNoneRunning) 
    throws Exception
  {
    File dirContents[] = dir.listFiles();

    // build a list of the container directories that can be 
    // archived if no more transactions will be added
    TxnDirComparator comparator = new TxnDirComparator();
    TreeSet containerDirs = new TreeSet(comparator);
    
    boolean archiveComplete = true;
    // do a depth-first search, compressing operations into zip files,
    // and full directories of operations into zip files
    for(int i=0; dirContents!=null && i<dirContents.length; i++) {
      File f = dirContents[i];
      if(f.isDirectory()) {
        if(f.getName().endsWith("t")) {
          if(!compressOperationFolder(f, assumeNoneRunning))
            archiveComplete = false;
        } else {
          containerDirs.add(f);
        }
      } else if(!f.getName().endsWith(".zip")) {
        //System.err.println("skipping unrelated file: "+f.getAbsolutePath());
        archiveComplete = false;
      }
    }
    
    // if all contained directories have been archived then archive this directory
    if(archiveComplete) {
      // archive all but the last finished folder in order to avoid 
      // archiving any in-progress operations
      // if one of the older sub-folders still contains active logs then it
      // will not be archived this time around
      int dirCount = 0;
      int numDirs = containerDirs.size();
      for(Iterator it = containerDirs.iterator(); dirCount+1<numDirs && it.hasNext(); ) {
        dirCount++;
        File f = (File)it.next();
        if(!compressFolder(f))
          archiveComplete = false;
      }
    }
    
    return archiveComplete;
  }

  /** Compress the given folder that should contain nothing but zip files
    * of compressed sub-folders or operation folders.  If any non-archived
    * log directories exist then there are still operations being logged and
    * the compression will be aborted.
    */
  boolean compressFolder(File opDir) 
    throws IOException
  {
    synchronized(archiveLock) {
      boolean archiveSuccessful = true;
      File files[] = opDir.listFiles();
      File parentDir = opDir.getParentFile();
      String opDirName = opDir.getName();
      File archiveFile = new File(parentDir, opDirName+".zip");
      File indicatorFile = new File(parentDir, opDirName+".finished");
      
      if(!indicatorFile.exists()) {
        // only archive if the 'finished' indicator file doesn't exist
        logger.warn("Warning: directory "+opDir.getAbsolutePath()+
                           " exists but has already been archived.  Removing.");
        
        ZipOutputStream zout = 
          new ZipOutputStream(new FileOutputStream(archiveFile));
        
        for(int i=0; i<files.length; i++) {
          File f = files[i];
          if(f.isDirectory()) {
            // there is a sub-directory.  This shouldn't happen, which means
            // that something is wrong
            logger.warn("Warning:  unable to archive directory: "+
                               opDir.getAbsolutePath()+
                               " because it contains subdirectory: "+
                               f.getName());
            archiveSuccessful = false;
            break;
          } else {
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(f));
            zout.putNextEntry(new ZipEntry(f.getName()));
            byte buf[] = new byte[2048];
            int r;
            while((r=bin.read(buf))>=0)
              zout.write(buf, 0, r);
            zout.closeEntry();
          }
        }
        zout.close();
      }
      
      if(!archiveSuccessful) {
        return false;
      }
      
      // write the indicator file, showing that the directory has been archived
      FileOutputStream indicatorOut = new FileOutputStream(indicatorFile);
      indicatorOut.write(new byte[0]);
      indicatorOut.close();
      
      // remove all of the directories and return
      removeAllFiles(opDir);
      
      // remove the indicator file (it is no longer needed)
      indicatorFile.delete();
    }
    return true;
  }

  
  public static final void removeAllFiles(File dirToRemove) 
    throws IOException
  {
    File files[] = dirToRemove.listFiles();
    if(files!=null) {
        for(int i=0; i<files.length; i++) {
            if(files[i].isDirectory())
                removeAllFiles(files[i]);
            else
                files[i].delete();
        }
    }
    dirToRemove.delete();
  }
  
  private class TxnDirComparator 
    implements Comparator
  {
    public int compare(Object o1, Object o2) {
      return ((File)o1).getName().compareTo(((File)o2).getName());
    }
    
    public boolean equals(Object o) {
      return o==this;
    }
  }
  
  
  
  /** Compress the given operation folder after a check (if noCheckNeeded is false)
    * that the operation represented by the folder is not currently in progress. */
  boolean compressOperationFolder(File opDir, boolean noCheckNeeded) {
    synchronized(archiveLock) {
      boolean okToCompress = noCheckNeeded;
      if(!okToCompress) {
        File opFile = new File(opDir, AuditLog.OP_INFO_FILE);
        if(!opFile.exists()) return false;  // operation hasn't even started yet
        
        File inuseFile = new File(opDir, AuditLog.PROGRESS_FILE);
        if(inuseFile.exists()) return false; // operation is still in progress
      }
      
      // at this point, we are ready to archive this transaction
      
      //if(noCheckNeeded)
      try {
        File opParent = opDir.getParentFile();
        ZipOutputStream zout = 
          new ZipOutputStream(new FileOutputStream(new File(opDir.getParent(), opDir.getName()+".zip")));
        String txnFiles[] = opDir.list();
        byte buf[] = new byte[2048];
        int r;
        for(int i=0; txnFiles!=null && i<txnFiles.length; i++) {
          File f = new File(opDir, txnFiles[i]);
          if(!f.isFile()) continue;
          zout.putNextEntry(new ZipEntry(txnFiles[i]));
          FileInputStream fin = new FileInputStream(f);
          while((r=fin.read(buf))>=0)
            zout.write(buf, 0, r);
          zout.closeEntry();
        }
        zout.close();
        
        // delete the information files and operation directory
        for(int i=0; txnFiles!=null && i<txnFiles.length; i++) {
          File f = new File(opDir, txnFiles[i]);
          f.delete();
        }
        opDir.delete();
        return true;
      } catch (Exception e) {
        logger.error("Error archiving operation directory: "+opDir.getAbsolutePath(),e);
        return false;
      }
    }
  }
  
}


