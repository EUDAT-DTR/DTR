/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class responsible for keeping track of the transaction queue. */
abstract public class AbstractTransactionQueue {
  static final Logger logger = LoggerFactory.getLogger(AbstractTransactionQueue.class);
  private List<TransactionQueueListener> queueListeners;
  
  public AbstractTransactionQueue()
  {
    this.queueListeners = new CopyOnWriteArrayList<TransactionQueueListener>();
  }

  public void addQueueListener(TransactionQueueListener l) {
    queueListeners.add(l);
  }

  public void removeQueueListener(TransactionQueueListener l) {
    queueListeners.remove(l);
  }
    
  abstract public long getLastTimestamp();
  
  /******************************************************************
   * Notify any objects that are listening for new transactions
   * that the given transaction has been added to the queue.
   ******************************************************************/
  protected void notifyQueueListeners(Transaction txn) {
    for(TransactionQueueListener listener : queueListeners) {
      try {
        listener.transactionAdded(txn);
      } catch (Exception e) {
        logger.error("error notifying queue listeners",e);
      }
    }
  }
  
  /*******************************************************************************
   * Log the specified transaction to the current queue (creating a new queue, if 
   * necessary
   *******************************************************************************/
  abstract public void addTransaction(Transaction txn) 
    throws Exception;

  abstract public void shutdown();

  /** Returns an Enumeration of Transaction objects starting after the
    * given timestamp.  
    * 
    * @deprecated Use #getCloseableScanner(long).
    * */
  @Deprecated
  public final Enumeration getScanner(long lastTimestamp) throws Exception {
      return getCloseableScanner(lastTimestamp);
  }

  /** Returns an Enumeration of Transaction objects starting after the
   * given timestamp. 
   */
  abstract public CloseableEnumeration getCloseableScanner(long lastTimestamp) throws Exception;

  public static interface CloseableEnumeration extends Enumeration, Closeable {
      @Override void close();
  }
}
