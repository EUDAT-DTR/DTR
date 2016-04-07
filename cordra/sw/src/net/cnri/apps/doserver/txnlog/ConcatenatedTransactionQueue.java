/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import net.cnri.apps.doserver.txnlog.TransactionQueueListener;

/**
 * 
 * Given the old style file based transaction queue and the new style queue 
 * this class provides a common interface concatenating the two queues allowing
 * the data to be iterated over as if in a single queue.
 *
 */
public class ConcatenatedTransactionQueue extends AbstractTransactionQueue {

	private final AbstractTransactionQueue oldQueue;
	private final AbstractTransactionQueue currentQueue;
    private final ConcatenatedTransactionQueue thisQueue;
	private final long lastTimeStampOfOldQueue;
	
	public ConcatenatedTransactionQueue(TransactionQueue oldQueue, AbstractTransactionQueue currentQueue) {
		this.oldQueue = oldQueue;
		this.currentQueue = currentQueue;
        this.thisQueue = this;
        
        TransactionQueueListener subListener = new TransactionQueueListener() {
            public void transactionAdded(Transaction txn) {
                thisQueue.notifyQueueListeners(txn);
            }
        };
        this.oldQueue.addQueueListener(subListener);
        this.currentQueue.addQueueListener(subListener);
        lastTimeStampOfOldQueue = oldQueue.getLastTimestamp(); 
	}
	
	@Override
	public long getLastTimestamp() {
	    return Math.max(lastTimeStampOfOldQueue,currentQueue.getLastTimestamp());
	}
	
	@Override
	public void addTransaction(Transaction txn) throws Exception {
	    if(txn.timestamp<=lastTimeStampOfOldQueue) {
	    	txn.timestamp = lastTimeStampOfOldQueue+1;
	    }
		currentQueue.addTransaction(txn);
		notifyQueueListeners(txn);
	}

	@Override
	public void shutdown() {
		oldQueue.shutdown();
		currentQueue.shutdown();
	}

	@Override
	public CloseableEnumeration getCloseableScanner(long lastTimestamp) throws Exception {
		CloseableEnumeration oldEnumeration = null;
		if (lastTimestamp < lastTimeStampOfOldQueue) {
			oldEnumeration = oldQueue.getCloseableScanner(lastTimestamp);
		}
		CloseableEnumeration currentEnumeration = currentQueue.getCloseableScanner(lastTimestamp);
		return new QueueScanner(oldEnumeration, currentEnumeration);
	}
	
	public class QueueScanner implements CloseableEnumeration {

		private Transaction next = null;
		private CloseableEnumeration oldEnumeration;
		private CloseableEnumeration currentEnumeration;
		
	    protected QueueScanner(CloseableEnumeration oldEnumeration, CloseableEnumeration currentEnumeration) throws Exception {
	    	this.oldEnumeration = oldEnumeration;
	    	this.currentEnumeration = currentEnumeration;
	    }
		
		@Override
		public boolean hasMoreElements() {
			if (next != null) {
				return true;
			} else {
				next = getNextFromEnumerations();
				return next != null;
			}
		}

		@Override
		public Object nextElement() {
			if (next == null) {
				return getNextFromEnumerations();
			} else {
				Transaction result = next;
				next = null;
				return result;
			}
		}

		private Transaction getNextFromEnumerations() {
			if (oldEnumeration != null) {
			    if (oldEnumeration.hasMoreElements()) {
			        return (Transaction) oldEnumeration.nextElement();
			    } else {
                    oldEnumeration.close();
                    oldEnumeration = null;
			    }
			}
			if (currentEnumeration.hasMoreElements()) {
			    return (Transaction) currentEnumeration.nextElement();
			} else {
			    return null;
			}
		}
		
		@Override
		public void close() {
		    if (oldEnumeration != null) oldEnumeration.close();
		    if (currentEnumeration != null) currentEnumeration.close();
		}
	}	
}
