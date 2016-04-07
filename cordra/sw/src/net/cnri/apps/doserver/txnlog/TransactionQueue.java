/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import net.cnri.dobj.*;
import net.cnri.util.StringUtils;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 Class responsible for keeping track of the different transaction
 queue files.  The transaction queue is separated into separate
 files so that it will be easy to wipe out old transactions 
 (just delete the old files).  It also makes replication slightly faster
 since the entire queue doesn't need to be scanned when starting
 replication at a certain transaction ID. */
public class TransactionQueue extends AbstractTransactionQueue {
  static final Logger logger = LoggerFactory.getLogger(TransactionQueue.class);
    
  private File queueDir;
  private File queueIndexFile;
  private Vector queueFiles;
  private Calendar calendar;
  private File lockFile;
  
  private long lastTimestamp = 0;
  private boolean haveLock = false;
  private boolean initialized = false;
  
  public TransactionQueue(File queueDir) 
    throws Exception 
  {
    super();
    this.queueDir = queueDir;
    this.lockFile = new File(queueDir, "lock");
    this.queueIndexFile = new File(queueDir, "index");

    calendar = Calendar.getInstance();
    
    getLock();
    
    initQueueIndex();
    
    lastTimestamp = calculateLastTimestamp();

    this.initialized = true;
  }

  public synchronized long getLastTimestamp() {
      return lastTimestamp;
  }
  
  private synchronized long calculateLastTimestamp() {
    if(queueFiles.size()<=0) return 0;
    long lastTimestamp = 0;
    try {
      logger.debug("Calculating last timestamp...");
      QueueScanner scanner =
        new QueueScanner((QueueFileEntry)queueFiles.elementAt(queueFiles.size()-1), 0);
      while(scanner.hasMoreElements()) {
        lastTimestamp = ((Transaction)scanner.nextElement()).timestamp;
      } 
      logger.debug("Finished calculating last timestamp");
    } catch (Exception e) {
      
      logger.error("Error getting transaction ID: using "+lastTimestamp,e);
      
    } finally {
      
      // after doing something big like retrieving transaction logs, we
      // should garbage collect to free up memory and close open files..
//      try {
//        System.gc();
//        System.runFinalization();
//      } catch (Throwable t) {}
    }
    return lastTimestamp;
  }
  
  
  
  public synchronized long getFirstDate() {
    if(queueFiles.size()<=0) return Long.MAX_VALUE;
    QueueFileEntry entry = (QueueFileEntry)queueFiles.elementAt(0);
    return entry.firstTime;
  }
  
  private synchronized int getQueueFileName(Date dt) {
    calendar.setTime(dt);
    return 
      calendar.get(Calendar.YEAR)*10000 +
      (calendar.get(Calendar.MONTH)+1)*100 +
      calendar.get(Calendar.DAY_OF_MONTH);
  }

  private synchronized void initQueueIndex() 
    throws Exception
  {
    queueFiles = new Vector();
    if(!queueIndexFile.exists() || queueIndexFile.length()<=0) {
      // setup the queues for a NEW server
      Transaction dummyTxn = 
        new Transaction(Transaction.ACTION_COMMENT, 
                        "initial entry", System.currentTimeMillis());
      addTransaction(dummyTxn);
    } else {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(queueIndexFile),"UTF-8"));
      while(true) {
        String line = reader.readLine();
        if(line==null) 
          break;
        line = line.trim();
        if(line.length()<=0) 
          continue;
        long firstTime = Long.parseLong(StringUtils.fieldIndex(line,'\t',0));
        int queueNumber  = Integer.parseInt(StringUtils.fieldIndex(line,'\t',1));
        queueFiles.addElement(new QueueFileEntry(firstTime, queueNumber));
      }
    }
  }
  
  
  //
  // XXX: race condition here!  another lock file could be created in between
  //      the check to see if it exists and when it is created.  Not horrible,
  //      since transaction queues are not created very often, but less
  //      than desirable.
  //
  private synchronized void getLock() 
    throws Exception
  {
    if(lockFile.exists()) {
      logger.error("Error: lock file ("+lockFile+") exists.  If you are sure "+
                         "that another server is not running, remove this file and restart "+
                         "the server");
      throw new Exception("Queue files are locked");
    } else {
      try {
        try {
          new File(lockFile.getParent()).mkdirs();
        } catch (Exception e) {}

        OutputStream out = new FileOutputStream(lockFile);
        out.write("lock".getBytes());
        out.close();
        haveLock = true;
      } catch (Exception e) {
        throw new Exception("Cannot create lock file: "+e);
      }
    }
  }


  private synchronized void releaseLock() {
    if(!haveLock)
      return;
    
    try {
      if(lockFile!=null) lockFile.delete();
      haveLock = false;
      lockFile = null;
    } catch (Throwable e) {
      logger.error("Error removing transaction queue lock file",e);
    }
  }
  
  
  private synchronized QueueFileEntry getCurrentQueue() {
    if(queueFiles.size()<=0) return null;
    return (QueueFileEntry)queueFiles.elementAt(queueFiles.size()-1);
  }
  
  
  /*******************************************************************************
   * Log the specified transaction to the current queue (creating a new queue, if 
   * necessary
   *******************************************************************************/
  public synchronized void addTransaction(Transaction txn) 
    throws Exception
  {
    // make sure the timestamp is monotonically increasing
    if(txn.timestamp<=lastTimestamp) {
      txn.timestamp = lastTimestamp+1;
    }
    lastTimestamp = txn.timestamp;
    
    // TODO: make sure the timestamp is not too far out of whack with the system time
    
    
    // check to see if we should start a new queue
    Date now = new Date(txn.timestamp);
    int qnum = getQueueFileName(now);
      
    QueueFileEntry currentQueue = getCurrentQueue();
    
    if(currentQueue==null || qnum > currentQueue.getQueueNumber()) {
      // if we are in a new time period, create a new queue
      currentQueue = createNewQueue(txn.timestamp, qnum);
    }
    
    currentQueue.writeRecord(txn);
    notifyQueueListeners(txn);
  }

  /*****************************************************************************
   * Create and initialize a new transaction queue for transaction on or
   * after the given starting date/time.  This will create the queue file
   * and add an entry for it into the queue index file.
   *****************************************************************************/
  private synchronized QueueFileEntry createNewQueue(long firstTime, int queueNum) 
    throws Exception
  {
    closeCurrentQueue();
    FileWriter writer = new FileWriter(queueIndexFile.getAbsolutePath(), true);
    QueueFileEntry newQueue = new QueueFileEntry(firstTime, queueNum);
    String record = 
      String.valueOf(firstTime)+'\t'+
      String.valueOf(queueNum)+"\t\n";
    writer.write(record);
    writer.close();
    queueFiles.addElement(newQueue);
    return newQueue;
  }
  
  private synchronized void closeCurrentQueue() 
    throws Exception
  {
    QueueFileEntry qfe = getCurrentQueue();
    if(qfe!=null) qfe.close();
  }

  private synchronized QueueFileEntry getNextQueue(QueueFileEntry presentQueue) {
    for(int i=queueFiles.size()-2; i>=0; i--) {
      QueueFileEntry qfe = (QueueFileEntry)queueFiles.elementAt(i);
      if(qfe==presentQueue) {
        return (QueueFileEntry)queueFiles.elementAt(i+1);
      }
    }
    
    // the called must have the last queue already...
    return null;
  }
  
  public void finalize() {
    try {
      shutdown();
    } catch (Throwable t) {
      logger.error("Error finalizing txn queue",t);
    }
  }

  public synchronized void shutdown() {
    if(!initialized)
      return;
    try {
      closeCurrentQueue();
    } catch (Throwable e) {
      logger.error("Error shutting down transaction queue",e);
    }
    releaseLock();
  }


  /** Returns an Enumeration of Transaction objects starting after the
    * given timestamp.  */
  public synchronized CloseableEnumeration getCloseableScanner(long lastTimestamp) 
    throws Exception
  {
    if(queueFiles.size()<=0) { // shouldn't happen!
      return null;
    }
    
    if(lastTimestamp==0) {
      return new QueueScanner((QueueFileEntry)queueFiles.elementAt(0), lastTimestamp);
    }
    QueueFileEntry prevEntry = null;
    for(int i=0; i<queueFiles.size(); i++) {
      QueueFileEntry nextEntry = (QueueFileEntry)queueFiles.elementAt(i);
      if(nextEntry.firstTime>=0 && nextEntry.firstTime>lastTimestamp) {
        if(prevEntry==null) {
          // this is the first daily queue and it doesn't include the last
          // received txn... bad news... the requestor will have to redump
          throw new DOException(DOException.REPLICATION_ERROR,
                                "Replication transaction ID "+lastTimestamp+
                                " too far out of date");
        } else {
          // this queue starts with later txn ID than the requestor is
          // asking for, so the previous queue should have what he wants.
          return new QueueScanner(prevEntry, lastTimestamp);
        }
      }
      logger.debug("  skipping queue file: {}",nextEntry);
      prevEntry = nextEntry;
    }
    
    if(prevEntry==null) {
      // no queues found?  shouldn't get here.
      return new QueueScanner((QueueFileEntry)queueFiles.elementAt(0), lastTimestamp);
    }

    // return a scanner for the most recent queue
    logger.debug("  returning queue file: {}  with afterDate={}",prevEntry,lastTimestamp);
    return new QueueScanner(prevEntry, lastTimestamp);
  }
  
  
  
  
  public class QueueScanner
    implements CloseableEnumeration
  {
    private BufferedInputStream txnStream;
    private QueueFileEntry queueFileEntry;
    private long firstDateInLog;
    private long afterDate;
    
    private Transaction preFetchedTxn = null;
    private boolean fetched = false;

    protected QueueScanner(QueueFileEntry queueFileEntry, long afterDate)
      throws Exception
    {
      this.firstDateInLog = queueFileEntry.getFirstTime();
      this.afterDate = afterDate;
      connectToQueue(queueFileEntry);
      try {
          // read all transactions until we get to one that has a greater
          // timestamp than was specified
          while(afterDate!=0 && afterDate!=-1) {
              Transaction txn = fetchTxn(false);
              if(txn==null) break;
              if(txn.timestamp <= afterDate) {
                  logger.debug("  skipping transaction: {}",txn);
                  fetchTxn(true);
              } else {
                  break;
              }
          }
      } catch (Exception e) {
          close();
          throw e;
      }
    }
    
    public long getFirstDateInLog() {
      return firstDateInLog;
    }
    
    private void connectToQueue(QueueFileEntry nextQueue)
      throws Exception
    {
      logger.debug(" opening queue: {}",nextQueue);
      txnStream = null;
      try {
        File f = nextQueue.getQueueFile();
        if(f.exists() && f.canRead()) {
          txnStream = new BufferedInputStream(new FileInputStream(f));
        } else {
          throw new Exception("Cannot access file: "+f);
        }
        queueFileEntry = nextQueue;
      } catch (Exception e) {
        throw new Exception("Unable to open transaction log: "+e);
      } finally {
//        System.gc();
//        System.runFinalization();
      }
    }
    
    public boolean hasMoreElements() {
      try {
        return fetchTxn(false)!=null;
      } catch (Exception e) {
        logger.error("Error fetching transaction",e);
        return false;
      }
    }
    
    public synchronized Object nextElement() {
      Transaction txn = null;
      try {
        txn = fetchTxn(true);
      } catch (Exception e) {
        logger.error("Error fetching transaction",e);
      }
      
      if(txn==null) throw new NoSuchElementException();
      return txn;
    }
    
    /** Returns the next available transaction.  If the next transaction
      * has already been retrieved, simply return it.  If the consumeIt
      * flag is true, then the pre-fetched transaction will be discarded so
      * that the subsequent invocation will retrieve the next transaction.
      */
    private synchronized Transaction fetchTxn(boolean consumeIt) 
      throws Exception
    {
      Transaction returnVal = null;
      if(fetched) {
        returnVal = preFetchedTxn;
      } else {
        preFetchedTxn = nextTxn();
        returnVal = preFetchedTxn;
        fetched = true;
      }
      logger.debug("     fetched: {}; consume={}",returnVal,consumeIt);
      
      if(consumeIt) {
        fetched = false;
        preFetchedTxn = null;
      }
      
      return returnVal;
    }

    private synchronized Transaction nextTxn() 
      throws Exception
    {
      do {
        Transaction txn = Transaction.readTxn(txnStream);
        if(txn==null) {
          // reached end of queue file.  if there are no more queues, we're done.
          // otherwise, start scanning the next queue.
          try { close(); } catch(Throwable t) {}
          QueueFileEntry nextQueue = getNextQueue(queueFileEntry);
          if(nextQueue==null) {
            // end of the line... no more queues
            return null;
          } else {
            connectToQueue(nextQueue);
          }
        } else {
          return txn;
        }
      } while(true);
    }


    public void close() {
      if (txnStream == null) return;
      try {
        txnStream.close();
      } catch (Exception e) {}
    }
    
  }
  
  class QueueFileEntry {
    private long firstTime;
    private int queueNumber;
    private OutputStream out = null;
    private File queueFile = null;
    
    QueueFileEntry(long firstTime, int queueNumber) {
      this.firstTime = firstTime;
      this.queueNumber = queueNumber;
    }
    
    long getFirstTime() { return firstTime; }
    long getQueueNumber() { return queueNumber; }
    
    synchronized File getQueueFile() {
      if(queueFile==null) {
        queueFile = new File(queueDir, String.valueOf(queueNumber)+".q");
      }
      return queueFile;
    }
    
    synchronized void writeRecord(Transaction txn) 
      throws IOException
    {
      if(out==null) {
        out = new BufferedOutputStream(new FileOutputStream(getQueueFile(), true));
      }
      synchronized(out) {
        Transaction.encodeTxn(txn, out);
        out.flush();
      }
    }
    
    synchronized void close() {
      OutputStream tmpOut = out;
      out = null;
      if(tmpOut!=null) {
        try {
          tmpOut.close();
        } catch (Exception e) {
          logger.error("Error closing queue writer",e);
        }
      }
    }
    
    public String toString() {
      return String.valueOf(queueNumber)+"; firsttime="+firstTime+
        "; file="+queueFile;
    }
    
  }
  
}

