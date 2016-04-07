/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue;
import net.cnri.apps.doserver.txnlog.ConcatenatedTransactionQueue;
import net.cnri.apps.doserver.txnlog.TransactionQueueBerkeleyDB;
import net.cnri.apps.doserver.txnlog.TransactionQueue;
import net.cnri.dobj.*;
import java.io.*;

/**
 *  This abstract class implements the enhanced logging functions for Storage (without enhancements!)
 *  in order to ease making old implementations compatible.
 */
abstract public class AbstractStorage implements Storage {
    private AbstractTransactionQueue txnQueue;
    
    public void initTransactionQueue(File txnDir) throws Exception {
        // set up the directory for the replication transaction queue
        if(!txnDir.exists())
        	txnDir.mkdirs();
        if (isConcatenatedQueueNeeded(txnDir)) {
        	TransactionQueue oldQueue = new TransactionQueue(txnDir); 
        	AbstractTransactionQueue currentQueue = new TransactionQueueBerkeleyDB(txnDir);
        	this.txnQueue = new ConcatenatedTransactionQueue(oldQueue, currentQueue);
        } else {
        	this.txnQueue = new TransactionQueueBerkeleyDB(txnDir);
        }   
    }
   
	public static boolean isConcatenatedQueueNeeded(File txnDir) {
        FilenameFilter filter = new TxnQueueFilter();
        File[] queueFiles = txnDir.listFiles(filter);
		return queueFiles.length !=0;
	}
	
	private static class TxnQueueFilter implements FilenameFilter {
	    public boolean accept(File dir, String name) {
	        return (name.endsWith(".q"));
	    }
	}    
    
    public AbstractTransactionQueue getTransactionQueue() {
        return txnQueue;
    }
    
    public void setTransactionQueue(AbstractTransactionQueue txnQueue) {
        this.txnQueue = txnQueue;
    }
    
    public String createObject(String objectID, String objectName, HeaderSet txnMetadata, long timestamp)
    throws DOException {
        return createObject(objectID,objectName,txnMetadata!=null,timestamp);
    }

    public void deleteObject(String objectID, HeaderSet txnMetadata, long asOfTimestamp)
    throws DOException {
        deleteObject(objectID,txnMetadata!=null,asOfTimestamp);
    }

    public void storeDataElement(String objectID, String elementID, 
            InputStream input, HeaderSet txnMetadata,
            boolean append, long timestamp)
    throws DOException {
        storeDataElement(objectID,elementID,input,txnMetadata!=null,append,timestamp);
    }

    public boolean deleteDataElement(String objectID, String elementID,
            HeaderSet txnMetadata, long timestamp)
    throws DOException {
        return deleteDataElement(objectID,elementID,txnMetadata!=null,timestamp);
    }

    public void setAttributes(String objectID, String elementID, 
            HeaderSet attributes,
            HeaderSet txnMetadata, long timestamp)
    throws DOException {
        setAttributes(objectID,elementID,attributes,txnMetadata!=null,timestamp);
    }

    public void deleteAttributes(String objectID, String elementID,
            String attributeKeys[], HeaderSet txnMetadata,
            long timestamp)
    throws DOException {
        deleteAttributes(objectID,elementID,attributeKeys,txnMetadata!=null,timestamp);
    }
}
