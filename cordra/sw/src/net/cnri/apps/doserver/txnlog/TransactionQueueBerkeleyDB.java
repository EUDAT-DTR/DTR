/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.je.*;

/**
 * Class responsible for keeping track of the transactions.
 * Transactions are stored in a BerkleyDB.
 * The berkelydb  database files will be stored in a subfolder of /txns
 */
public class TransactionQueueBerkeleyDB extends AbstractTransactionQueue {
    static final Logger logger = LoggerFactory.getLogger(TransactionQueueBerkeleyDB.class);
    
	private static final String DB_DIR_NAME = "db"; //The berkelydb will be stored in a subfolder of /txns
	private Environment dbEnvironment = null;
	private Database txnLogDatabase = null;
	private long lastTimestamp = 0;
	private boolean shutdown;
	
	public TransactionQueueBerkeleyDB(File queueDir) throws Exception {
		super();
		try {
			File dbDir = new File(queueDir, DB_DIR_NAME);
	        if(!dbDir.exists()) {
	        	dbDir.mkdirs();
	        }
		    EnvironmentConfig envConfig = new EnvironmentConfig();
		    envConfig.setAllowCreate(true);
		    envConfig.setSharedCache(true);
		    envConfig.setTransactional(true); 
		    dbEnvironment = new Environment(dbDir, envConfig);
		    DatabaseConfig dbConfig = new DatabaseConfig();
		    dbConfig.setAllowCreate(true);
		    dbConfig.setTransactional(true); 
		    txnLogDatabase = dbEnvironment.openDatabase(null, "txnLogDatabase", dbConfig);
		} catch (DatabaseException dbe) {
		    logger.error("Error in constructor",dbe);
		    throw dbe;
		}   
		lastTimestamp = calculateLastTimestamp();
	}
	
	public long getLastTimestamp() {
	    return lastTimestamp;
	}
	
	private long calculateLastTimestamp() {
	    try {
	        DatabaseEntry key = new DatabaseEntry();
	        DatabaseEntry data = new DatabaseEntry();
	        Cursor cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED);
	        try {
	            OperationStatus status = cursor.getPrev(key, data, LockMode.READ_UNCOMMITTED); //gets the last entry in the db
	            if (status != OperationStatus.SUCCESS) {
	                return 0;
	            } else {
	                return fromByteArray(key.getData());
	            }
	        } 
	        finally {
	            cursor.close();
	        }
	    }
	    catch(DatabaseException e) {
	        throw new RuntimeException("Error getting last transaction timestamp",e);
	    }
	}
	
	@Override
	public synchronized void addTransaction(Transaction txn) throws Exception {
	    if(txn.timestamp<=lastTimestamp) { // make sure the timestamp is monotonically increasing
	    	txn.timestamp = lastTimestamp+1;
	    }
	    lastTimestamp = txn.timestamp;
		BytesMap bytesMap = new BytesMap(txn);
		txnLogDatabase.put(null, bytesMap.getKey(), bytesMap.getData());
	    notifyQueueListeners(txn);
	}

	@Override
	public synchronized void shutdown() {
	    if(shutdown) return;
		try {
	        if (txnLogDatabase != null) {
	            txnLogDatabase.close();
	        }
		    if (dbEnvironment != null) {
		        dbEnvironment.close();
		    } 
		    shutdown = true;
		} catch (DatabaseException dbe) {
			logger.error("Error closing environment",dbe);
		} 
	}

	@Override
	public CloseableEnumeration getCloseableScanner(long lastTimestamp) throws Exception {
		return new QueueScanner(txnLogDatabase, lastTimestamp);
	}
	
	public static byte[] toByteArray(long data) {
		return new byte[] {
		(byte)((data >> 56) & 0xff),
		(byte)((data >> 48) & 0xff),
		(byte)((data >> 40) & 0xff),
		(byte)((data >> 32) & 0xff),
		(byte)((data >> 24) & 0xff),
		(byte)((data >> 16) & 0xff),
		(byte)((data >> 8) & 0xff),
		(byte)((data >> 0) & 0xff),
		};
	}
	
	public static long fromByteArray(byte[] bytes) {
		long result = 0;
		for (int i = 0; i < bytes.length; i++) {
			result = (result << 8) + (bytes[i] & 0xff);
		}
		return result;
	}
	
	/**
	 * Wraps a Transaction a key value pair of byte arrays for use with BerkelyDB 
	 */
	private static class BytesMap {
		
		private byte[] key;
		private byte[] data;
		
		public BytesMap(Transaction txn) throws IOException {
			key = toByteArray(txn.timestamp);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Transaction.encodeTxn(txn, out);
			data = out.toByteArray();
		}
		
		public byte[] getKeyBytes() { return key; }
		
		public byte[] getValueBytes() { return data; }
		
		public DatabaseEntry getKey() { return new DatabaseEntry(key); }
		
		public DatabaseEntry getData() { return new DatabaseEntry(data); }
	}
	
	public static class QueueScanner implements CloseableEnumeration {

		private final Database txnLogDatabase; 
		private final long afterDate;
		private Cursor cursor;
		
		private Transaction next = null;
		
	    protected QueueScanner(Database txnLogDatabase, long afterDate) throws Exception {
	    	this.txnLogDatabase = txnLogDatabase;
	    	this.afterDate = afterDate;
	    	DatabaseEntry key = new DatabaseEntry(toByteArray(afterDate));
	    	DatabaseEntry data = new DatabaseEntry();
	      	cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED);
	      	try {
	      	    OperationStatus status = cursor.getSearchKeyRange(key, data, LockMode.READ_UNCOMMITTED); //moves cursor to the record at afterDate 
	      	    if(status != OperationStatus.SUCCESS) {
	      	        cursor.close();
	      	        cursor = null;
	      	    }
	      	    else {
	      	        long keyAsLong = fromByteArray(key.getData());
	      	        if (keyAsLong > afterDate) {
	      	            //there wasn't an exact match in the search so the current is already greater than afterdate
	      	            InputStream in = new ByteArrayInputStream(data.getData());
	      	            next = Transaction.readTxn(in);
	      	        }
	      	    }
	      	} catch (Exception e) {
	      	    cursor.close();
	      	    throw e;
	      	}
	    }
		
		@Override
		public boolean hasMoreElements() {
			if (next != null) {
				return true;
			} else {
				next = getNextFromDB();
				return next != null;
			}
		}

		@Override
		public Object nextElement() {
			if (next == null) {
				return getNextFromDB();
			} else {
				Transaction result = next;
				next = null;
				return result;
			}
		}

		private Transaction getNextFromDB() {
		    if(cursor==null) return null;
	    	DatabaseEntry key = new DatabaseEntry();
	    	DatabaseEntry data = new DatabaseEntry();			
			try {
				boolean success = cursor.getNext(key, data, LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS;
				if (success) {
					InputStream in = new ByteArrayInputStream(data.getData());
					Transaction result = Transaction.readTxn(in);
					return result;
				} else {
					cursor.close();
					cursor = null;
					return null;
				}
			} catch (DatabaseException e) {
			    logger.error("Error in getNextFromDB",e);
			    throw new RuntimeException(e);
			} catch (Exception e) {
                logger.error("Error in getNextFromDB",e);
                throw new RuntimeException(e);
			}
		}
		
		public void close() {
		    if (cursor == null) return;
		    try {
		        cursor.close();
		        cursor = null;
		    } catch (DatabaseException e) {
		        throw new RuntimeException(e);
		    }
		}
	}
}
