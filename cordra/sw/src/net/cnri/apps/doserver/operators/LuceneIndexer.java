/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;


import net.cnri.apps.doserver.*;
import net.cnri.apps.doserver.txnlog.*;
import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue.CloseableEnumeration;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.knowbots.lib.*;
import net.cnri.knowbots.station.CollaborationHub;
import net.cnri.util.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Operator that provides text indexing capabilities using the Lucene search engine.
 * This version analyzes the transaction log from a particular repository and 
 * indexes any objects in that repository.
 */
public class LuceneIndexer
extends Knowbot
implements DOOperation,
Runnable,
DOConstants
{
    static final Logger logger = LoggerFactory.getLogger(LuceneIndexer.class);
    
    public static final String INDEX_BUILDER_CLASS_KEY = "index_builder_class";
    public static final String INSECURE_SEARCH_KEY = "insecure_search";
    public static final String ALT_INSECURE_SEARCH_KEY = "enable_insecure_search";
    public static final String MIN_REOPEN_INTERVAL_KEY = "index_min_reopen_interval";
    public static final String MAX_REOPEN_INTERVAL_KEY = "index_max_reopen_interval";
    public static final String COMMIT_INTERVAL_KEY = "index_commit_interval";
    public static final String FEDERATED_UPDATE_INTERVAL = "index_federated_update_interval";

    public static final int MAX_DOCS = 16384; // maximum number of hits returned by a search 
    private static final String INDEX_DIR = "index";
    public static final String LUCENE_CONFIG_ELEMENT = "lucene.config";
    public static final String FEDERATED_INDEXES_KEY = "federated_indexes";

    // transaction log constants
    public static final String PARAM_TXN_ID = "txn_id";
    public static final String PARAM_OBJ_ID = "object_id";
    public static final String PARAM_TXN_TYPE = "txn_type";
    public static final String PARAM_TIMESTAMP = "tstamp";
    public static final String PARAM_ACTUAL_TIMESTAMP = "atstamp";
    public static final String PARAM_ELEMENT_ID = "elementid";

    public static final String TXN_TYPE_ADD_OBJ = "add";
    public static final String TXN_TYPE_DEL_OBJ = "del";
    public static final String TXN_TYPE_UPDATE_ELEMENT = "update_element";
    public static final String TXN_TYPE_DEL_ELEMENT = "del_element";
    public static final String TXN_TYPE_COMMENT = "comment";

    private int NUM_OBJECTS_BATCH_SIZE = 1000; // only index this many objects at a time.

    private transient volatile boolean keepRunning = true; 

    private final Hashtable myOperations = new Hashtable();
    private final String operationKeys[] = {
            SEARCH_OP_ID,
            REINDEX_OBJECT_ID,
            INDEX_UP_TO_DATE_ID
    };
    
    private final Main serverMain;
    private final StorageProxy repoStorage;
    private final DOAuthentication auth;
    private final File scanStatusFile;
    private final File suppressIndexingFile;
    private final FSDirectory indexDirectory;
    private final IndexBuilder indexBuilder;
    private final boolean insecureSearch;
    
    private List federatedSearchTargets = new ArrayList();
    private final StreamTable indexScanStatus;

    private IndexWriter indexWriter;
    private final ReadWriteLock indexWriterLock = new ReentrantReadWriteLock();
    
    private final Thread indexerThread;
    private final TransactionQueueListener queueListener;
    private final ScheduledExecutorService execServ = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService commitExecServ = Executors.newSingleThreadScheduledExecutor();
    private final Object updateWaitCondition = new Object();
    private volatile boolean txnUpdateNeeded = false;
    private volatile boolean periodicUpdateNeeded = true;
    private long lastReopen = 0;
    private final Object lastReopenSync = new Object();
    private volatile long latestTransactionTimestampSeen = 0;
    private volatile long latestTransactionTimestampIndexed = 0;
    private volatile long latestTransactionTimestampAvailableToSearch = 0;
    private final Object latestTransactionSync = new Object();
    private final Object latestTransactionIndexedSync = new Object();
    private IndexSearcherManager indexSearcherManager;
    private final ExecutorService backgroundIndexerExecServ = Executors.newCachedThreadPool();

    private long minReopenInterval = 100;
    private long maxReopenInterval = 10000;
    private long commitInterval = 300000;
    private long federatedUpdateInterval = 30000;
    
    public LuceneIndexer(final Main serverMain, IndexBuilder indexBuilder) 
    throws Exception
    {
        this.serverMain = serverMain;
        this.repoStorage = new ConcreteStorageProxy(serverMain.getStorage(), 
                serverMain.getServerID(),
                serverMain.getServerID(),
                null);

        for(int i=0; i<operationKeys.length; i++)
            myOperations.put(operationKeys[i].toLowerCase(), "");
        auth = AbstractAuthentication.getAnonymousAuth();


        // add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });

        String repoID = serverMain.getServerID();
        Storage storage = serverMain.getStorage();
        File serverBase = serverMain.getBaseFolder();
        File indexBase = new File(serverBase, INDEX_DIR);
        suppressIndexingFile = new File(indexBase, "suppress_indexing");
        File indexDir = new File(indexBase, "index");

        minReopenInterval = Integer.parseInt(serverMain.getConfigVal(MIN_REOPEN_INTERVAL_KEY, String.valueOf(minReopenInterval)));
        maxReopenInterval = Integer.parseInt(serverMain.getConfigVal(MAX_REOPEN_INTERVAL_KEY, String.valueOf(maxReopenInterval)));
        commitInterval = Integer.parseInt(serverMain.getConfigVal(COMMIT_INTERVAL_KEY, String.valueOf(commitInterval)));
        federatedUpdateInterval = Integer.parseInt(serverMain.getConfigVal(FEDERATED_UPDATE_INTERVAL, String.valueOf(federatedUpdateInterval)));
        
        String insecureSearchString = serverMain.getConfigVal(INSECURE_SEARCH_KEY, serverMain.getConfigVal(ALT_INSECURE_SEARCH_KEY, "false")).trim().toLowerCase();
        insecureSearch = insecureSearchString.startsWith("y") || insecureSearchString.startsWith("t");
        
        if (indexBuilder == null) {
            this.indexBuilder = new DefaultIndexBuilder();
        } else {
            this.indexBuilder = indexBuilder;
        }
        indexBuilder.init(serverMain);

        indexDirectory = FSDirectory.open(indexDir);
        indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(Version.LUCENE_47, indexBuilder.getAnalyzer()).setOpenMode(OpenMode.CREATE_OR_APPEND));
        if(!DirectoryReader.indexExists(indexDirectory)) {
            indexWriter.commit();
        }
        indexSearcherManager = new IndexSearcherManager(DirectoryReader.open(indexWriter, true));

        try {
            indexScanStatus = new StreamTable();
            scanStatusFile = new File(indexBase, "status.dct");
            if(scanStatusFile.exists()) {
                indexScanStatus.readFromFile(scanStatusFile);
            }
            latestTransactionTimestampSeen = serverMain.getTxnQueue().getLastTimestamp();
            latestTransactionTimestampIndexed = indexScanStatus.getLong("self_scan_txn", -1);
            latestTransactionTimestampAvailableToSearch = latestTransactionTimestampIndexed;
        } catch (StringEncodingException e) {
            throw new IOException("Error reading lucene scan status: "+e);
        }

        // kick off the asynchronous indexer..
        indexerThread = new Thread(this);
        indexerThread.setPriority(Thread.MIN_PRIORITY);
        indexerThread.start();

        execServ.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                if (!keepRunning) return;
                periodicUpdateNeeded = true;
                synchronized(updateWaitCondition) {
                    updateWaitCondition.notifyAll();
                }
            }
        }, 0, federatedUpdateInterval, TimeUnit.MILLISECONDS);

        execServ.scheduleWithFixedDelay(periodicReopenTask, 0, maxReopenInterval / 10, TimeUnit.MILLISECONDS);
        commitExecServ.scheduleWithFixedDelay(commitTask, 0, commitInterval, TimeUnit.MILLISECONDS);
        
        queueListener = new LuceneTransactionQueueListener();
        serverMain.getTxnQueue().addQueueListener(queueListener);
        
        new Thread(new ReindexerRunnable()).start();
    }

    private class LuceneTransactionQueueListener implements TransactionQueueListener {
        public void transactionAdded(Transaction txn) {
            latestTransactionTimestampSeen = Math.max(latestTransactionTimestampSeen,txn.timestamp);
            if(!txnUpdateNeeded) {
                txnUpdateNeeded = true;
                synchronized(updateWaitCondition) {
                    updateWaitCondition.notifyAll();
                }
            }
        }
    }

    private class ReindexerRunnable implements Runnable {
        ExecutorService execServ = Executors.newSingleThreadExecutor();
        IndexSearcher indexSearcher;
        boolean first = true;
        
        @Override
        public void run() {
            if (!keepRunning) return;
            Query query = null;
            try {
                query = indexBuilder.objectsNeedingReindexingQuery();
            }
            catch(AbstractMethodError e) { return; }
            if(query==null) return;
            IndexSearcherManager.LockTrackingIndexSearcherWrapper indexSearcherWrapper = indexSearcherManager.getAndLockIndexSearcherWrapper();
            try {
                indexSearcher = indexSearcherWrapper.getIndexSearcher();
                indexSearcher.search(query,new ReindexerCollector());
                execServ.shutdown();
                execServ.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS);
                if(!first) logger.info("Reindexing done.");
            }
            catch(Exception e) {
                logger.error("Error in reindexing",e);
            }
            finally {
                indexSearcherWrapper.releaseAndCloseIfNeeded();
            }
        }

        private class ReindexerCollector extends Collector {
            private int docBase;
            
            @Override
            public boolean acceptsDocsOutOfOrder() {
                return true;
            }
            @Override
            public void setScorer(Scorer scorer) throws IOException {
                // ignore
            }
            @Override
            public void setNextReader(AtomicReaderContext context) throws IOException {
                this.docBase = context.docBase;
            }
            @Override
            public void collect(int doc) throws IOException {
                if(first) {
                    first = false;
                    logger.info("Reindexing started.");
                }
                if (keepRunning) execServ.submit(new ReindexDocumentRunnable(docBase + doc));
            }
        }

        private class ReindexDocumentRunnable implements Runnable {
            private int doc;
            public ReindexDocumentRunnable(int doc) {
                this.doc = doc;
            }
            @Override
            public void run() {
                try {
                    if (!keepRunning) return;
                	DocumentStoredFieldVisitor fieldVisitor = new DocumentStoredFieldVisitor("id");
                	indexSearcher.doc(doc,fieldVisitor);
                    String id = fieldVisitor.getDocument().get("id");
                    logger.debug("Reindexing {}",id);
                    reindexObject(id); 
                }
                catch(Exception e) {
                    logger.error("Error in reindexing",e);
                }
            }
        }
    }
    
    public void executeInContext() {
        try {
            indexerThread.join();
        } catch (Exception e) {
            logger.error("error waiting for indexer thread in LuceneIndexer.executeInContext()",e);
        }
    }


    public boolean canHandleOperation(DOOperationContext context) {
        return myOperations.containsKey(context.getOperationID().toLowerCase());
    }


    /**
     * Returns a list of operations that this operator can perform
     * on the object identified by the DOOperationContext parameter.
     */
    public String[] listOperations(DOOperationContext context) {
        // we always build an index in the main repository object
        if(context.getTargetObjectID().equals(context.getServerID())) {
            return operationKeys;
        }

        try {
            if(context.getStorage().doesDataElementExist(LUCENE_CONFIG_ELEMENT))
                return operationKeys;
        } catch (Exception e) {
            logger.error("Error checking for element: element="+
                    LUCENE_CONFIG_ELEMENT,e);
        }

        return null;
    }


    public void run() {
        logger.info("Starting Lucene Indexer async indexing");

        try {
            while(keepRunning) {
                boolean bigUpdate = periodicUpdateNeeded;
                try {
                    updateIndex(bigUpdate);
                } catch (Exception t) {
                    logger.error("Error updating index",t);
                }
                if(bigUpdate) periodicUpdateNeeded = false;

                synchronized(updateWaitCondition) {
                    while(keepRunning && !txnUpdateNeeded && !periodicUpdateNeeded) {
                        try {
                            updateWaitCondition.wait();
                        }
                        catch(InterruptedException e) {
                            break;
                        }
                    }
                }
            }
        } finally {
            shutdown();
        }
    }

    public synchronized void shutdown() {
        if(!keepRunning) return;
        try {
            keepRunning = false;
            synchronized(updateWaitCondition) {
                updateWaitCondition.notifyAll();
            }
            if (queueListener!=null) serverMain.getTxnQueue().removeQueueListener(queueListener);
            if (execServ!=null) execServ.shutdown();
            if (commitExecServ!=null) commitExecServ.shutdown();
            if (backgroundIndexerExecServ!=null) backgroundIndexerExecServ.shutdown();
            backgroundIndexerExecServ.awaitTermination(2, TimeUnit.MINUTES);
            IndexWriter iw = indexWriter;
            if (iw != null) iw.close();
            saveSourceStatus();
        } catch (Exception e) {
            logger.error("Error closing indexwriter",e);
        }
    }
    
    /**
     * Performs the given operation (which this object has advertised that it
     * can handle) which consists of reading input (if any is expected) from the
     * given InputStream and writing the output of the operation (if any) to the
     * OutputStream.  This method should *always* close the input and output streams
     * when finished with them.  If there are any errors in the input, the error
     * message must be communicated on the OutputStream since all errors must be
     * at the application level.  Any exceptions thrown by this method will *not*
     * be communicated to the caller and are therefore not acceptable.
     */
    public void performOperation(DOOperationContext context, InputStream in, OutputStream out) {
        try {
            String operation = context.getOperationID();
            if(operation.equalsIgnoreCase(SEARCH_OP_ID)) {
                doSearch(context, in, out);
            } else if(operation.equalsIgnoreCase(REINDEX_OBJECT_ID)) {
              doReindex(context, in, out);
            } else if(operation.equalsIgnoreCase(INDEX_UP_TO_DATE_ID)) {
                indexUpToDateOperation(context, in, out);
            } else {
                // shouldn't happen
                sendErrorResponse(out, "Operation '"+operation+"' not implemented!",
                        DOException.OPERATION_NOT_AVAILABLE);
                return;
            }
        } catch(IOException e) {
            if("Pipe closed".equals(e.getMessage()) || "Attempted to write to a closed OutputStream".equals(e.getMessage())) {
                logger.debug("Exception in performOperation",e);
            } else {
                logger.error("Exception in performOperation",e);
            }
        } catch (Exception e) {
            logger.error("Exception in performOperation",e);
        } finally {
            try { in.close(); } catch (Exception e) {}
            try { out.close(); } catch (Exception e) {}
        }
    }

  
    /**
     * Implements the search operation.  This performs a search on the index
     * in the current object based on the 'query' string parameter.
     */
    private void doReindex(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
    {
      HeaderSet headers = context.getOperationHeaders();
      String objectId = headers.getStringHeader("objectid",null);
      if(objectId==null) objectId = context.getTargetObjectID();
      try {
          reindexObject(objectId);
          reopen();
      } catch (Exception e) {
        sendErrorResponse(out, "There was an error reindexing object "+context.getTargetObjectID(),
                          DOException.STORAGE_ERROR);
        return;
      }
      sendSuccessResponse(out);
    }


    public void reindexObject(String objectId) throws Exception {
        indexWriterLock.readLock().lock();
        try {
            updateObjectFromStorage(objectId);
        } finally {
            indexWriterLock.readLock().unlock();
        }
    }  

    /**
     * Implements the search operation.  This performs a search on the index
     * in the current object based on the 'query' string parameter.
     */
    private void indexUpToDateOperation(DOOperationContext context, InputStream in, OutputStream out) throws IOException
    {
      try {
          blockUntilUpToDate();
      } catch (Exception e) {
        sendErrorResponse(out, "Error waiting until up to date: " + e,
                          DOException.INTERNAL_ERROR);
        return;
      }
      sendSuccessResponse(out);
    }

    private void blockUntilUpToDate() throws InterruptedException {
        long timestamp = latestTransactionTimestampSeen;
        if(latestTransactionTimestampAvailableToSearch >= timestamp) return;
        synchronized (latestTransactionIndexedSync) {
            while (latestTransactionTimestampIndexed < timestamp) latestTransactionIndexedSync.wait();
        }
        reopenSoon(timestamp);
        synchronized(latestTransactionSync) {
            while(latestTransactionTimestampAvailableToSearch < timestamp) latestTransactionSync.wait();
        }
    }

    private void commit() {
        try {
            indexWriter.commit();
        } catch (Exception e) {
            logger.error("Error committing", e);
            indexWriterLock.writeLock().lock();
            try {
                try {
                    indexWriter.close();
                } catch (Exception ex) {
                    logger.error("Error closing indexWriter", ex);
                }
                indexWriter = null;
                try {
                    indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(Version.LUCENE_47, indexBuilder.getAnalyzer()).setOpenMode(OpenMode.CREATE_OR_APPEND));
                    indexSearcherManager.close();
                    indexSearcherManager = new IndexSearcherManager(DirectoryReader.open(indexWriter, true));
                } catch (Exception ex) {
                    logger.error("Error reopening indexWriter", ex);
                }
            } finally {
                indexWriterLock.writeLock().unlock();
            }
        }
        saveSourceStatus();
    }

    private final Runnable commitTask = new Runnable() {
        public void run() {
            if (!keepRunning) return;
            commit();
        }
    };

    private void reopen() {
        synchronized (lastReopenSync) {
            long timestamp = latestTransactionTimestampIndexed;
            indexSearcherManager.reopen();
            lastReopen = System.currentTimeMillis();
            latestTransactionTimestampAvailableToSearch = timestamp;
        }
        synchronized(latestTransactionSync) { latestTransactionSync.notifyAll(); }
    }

    private void reopenSoon(final long timestamp) {
        synchronized (lastReopenSync) {
            if (latestTransactionTimestampAvailableToSearch >= timestamp) return; 
            long now = System.currentTimeMillis();
            if (now < lastReopen || (now - lastReopen) >= minReopenInterval) {
                reopen();
            } else {
                execServ.schedule(new Runnable() {
                    public void run() {
                        reopenSoon(timestamp);
                    }
                }, minReopenInterval - (now - lastReopen), TimeUnit.MILLISECONDS);
            }
        }
    }

    private final Runnable periodicReopenTask = new Runnable() {
        public void run() {
            if (!keepRunning) return;
            synchronized (lastReopenSync) {
                if (latestTransactionTimestampAvailableToSearch == latestTransactionTimestampIndexed) return;
                long now = System.currentTimeMillis();
                if (now < lastReopen || (now - lastReopen) > 9 * maxReopenInterval / 10) {
                    reopen();
                }
            }
        }
    };

// Not a terrible idea, but doesn't work due to possibility of multiple transactions in a single millisecond.  
// Would have to include transaction keys in the index to get it robust.
//    private boolean isUpToDate(String objectId) throws IOException {
//        if(repoStorage==null) {
//            repoStorage = new ConcreteStorageProxy(serverMain.getStorage(), 
//                    serverMain.getServerID(),
//                    serverMain.getServerID(),
//                    null);
//        }
//        StorageProxy storage = repoStorage.getObjectAccessor(objectId);
//        
//        Query query = new TermQuery(new Term("id",objectId));
//        
//        IndexSearcherManager.LockTrackingIndexSearcherWrapper indexSearcherWrapper = indexSearcherManager.getAndLockIndexSearcherWrapper();
//        try {
//            IndexSearcher indexSearcher = indexSearcherWrapper.getIndexSearcher();
//
//            TopDocs hits = indexSearcher.search(query,1);
//            if(hits.totalHits==0) return !repoStorage.getObjectAccessor(objectId).doesObjectExist();
//            
//            Document doc = indexSearcher.doc(hits.scoreDocs[0].doc);
//            String indexDate = doc.get("objmodified");
//            String storageDate = DateTools.dateToString(new java.util.Date(storage.getAttributes(null).getLongHeader(DOConstants.DATE_MODIFIED_ATTRIBUTE, 0)),DateTools.Resolution.MILLISECOND);
//            return storageDate.equals(indexDate);
//        }
//        finally {
//            indexSearcherWrapper.releaseAndCloseIfNeeded();
//        }
//    }
    
    /**
     * Implements the search operation.  This performs a search on the index
     * in the current object based on the 'query' string parameter.
     */
    private void doSearch(DOOperationContext context, InputStream in, OutputStream out)
    throws Exception
    {
        StorageProxy storage = context.getStorage();
        HeaderSet params = context.getOperationHeaders();
        String queryStr = params.getStringHeader("query", null);
        if(queryStr==null) {
            sendErrorResponse(out, "Request was missing 'query' parameter",
                    DOException.APPLICATION_ERROR);
            return;
        }        
        
        String returnedFieldsStr = params.getStringHeader("returnedFields", null);
        List<String> returnedFields = null; 
        if (returnedFieldsStr!= null) returnedFields = getFieldsFromString(returnedFieldsStr);
        
        String sortFieldsStr = params.getStringHeader("sortFields", null);
        List<String> sortFields = null; 
        if (sortFieldsStr!= null) sortFields = getFieldsFromString(sortFieldsStr);
                
        boolean reverseAllSort = false;
        if(sortFields!=null && !sortFields.isEmpty()) {
            String sortOrder = params.getStringHeader("sortOrder", null);
            if(sortOrder!=null && sortOrder.toUpperCase().startsWith("DESC")) {
                reverseAllSort = true;
            }
        }
        
        int pageSize = params.getIntHeader("pageSize", 0);
        int pageOffset = params.getIntHeader("pageOffset", 0);
        boolean getTotalMatches = params.getBooleanHeader("getTotalMatches", true);
        
        boolean unsupportedFederationParams = (returnedFieldsStr!=null) || (sortFieldsStr!=null) || (pageSize!=0);
        boolean doFederation = federatedSearchTargets!=null && federatedSearchTargets.size()>0 && 
        		params.getBooleanHeader("federation", true);        
        
        if(doFederation && unsupportedFederationParams) {
        	sendErrorResponse(out, "Federated search does not support the following parameters: pageSize, pageOffset, sortOrder, sortFields, returnedFields", DOException.OPERATION_NOT_AVAILABLE);
        	return;
        }

        boolean indexUpToDate = params.getBooleanHeader("indexUpToDate",false);
        
        logger.debug("searching lucene for: {}"+queryStr);

        Query q0;
        try {
            q0 = parseCorrectingSlashesIfNeeded(queryStr);
        }
        catch(ParseException e) {
            sendErrorResponse(out,"Parse failure: " + queryStr, DOException.APPLICATION_ERROR);
            logger.debug("Parse failure: {}",queryStr,e);
            return;
        } catch(NullPointerException e) {
            sendErrorResponse(out,"Parse failure: " + queryStr, DOException.APPLICATION_ERROR);
            logger.debug("Parse failure: {}",queryStr,e);
            return;
        }

        if(indexUpToDate) blockUntilUpToDate();
        
        //q0.setBoost(10.0f);
        //BooleanQuery query = new BooleanQuery();
        //query.add(q0, BooleanClause.Occur.MUST);

        ArrayList federatedThreads = new ArrayList();
        IndexSearcherManager.LockTrackingIndexSearcherWrapper indexSearcherWrapper = indexSearcherManager.getAndLockIndexSearcherWrapper();
        try {
            IndexSearcher indexSearcher = indexSearcherWrapper.getIndexSearcher();

            if(logger.isDebugEnabled()) logger.debug("search for <<<{}>>> index-size: {}",q0,indexSearcher.getIndexReader().numDocs());

            int maxNumberOfResults = Math.max(MAX_DOCS, 1 + pageSize * (pageOffset+1));
            if(insecureSearch && pageSize > 0) {
                maxNumberOfResults = pageSize * (pageOffset+1);
            }
            
            TopDocsCollector collector;
            boolean needsPostSort = false;
            if (sortFields != null && !sortFields.isEmpty()) {
            	Sort sort = createSort(sortFields, reverseAllSort);
            	if(sort==null || sort.getSort().length<sortFields.size()) needsPostSort = true;
            	collector = TopFieldCollector.create(sort,maxNumberOfResults,true,false,false,false);
            } else {
                collector = TopScoreDocCollector.create(maxNumberOfResults,true);
            }
            indexSearcher.search(q0, collector);
            int totalMatches = collector.getTotalHits();
            logger.debug("Found {} results.",totalMatches);
            sendSuccessResponse(out);

            boolean needsPostPagination = doFederation || !insecureSearch || needsPostSort;

            List<ScoreDoc> results;
            if(pageSize <= 0 || needsPostPagination) {
                TopDocs hits  = collector.topDocs();
                results = Arrays.asList(hits.scoreDocs);
                boolean gotMaxResults = results.size() == maxNumberOfResults;
                Boolean more = null;
                if (!gotMaxResults || totalMatches == results.size()) more = Boolean.FALSE; // but might be set to true due to pagination below
                else if (totalMatches > results.size()) more = Boolean.TRUE;
                boolean noMoreBeforePagination = more == Boolean.FALSE;
                boolean canTrim = pageSize > 0 && !doFederation && !getTotalMatches && !needsPostSort;
                if(!insecureSearch) {
                    results = limitResultsToPermitted(results, context, indexSearcher, pageSize, pageOffset, canTrim);
                }
                if (pageSize > 0 && results.size() > (pageSize * pageOffset + pageSize)) more = Boolean.TRUE;

                if (!doFederation) {
                    HeaderSet queryMetaData = new HeaderSet("queryInfo");
                    boolean write = false;
                    if(insecureSearch && totalMatches >= 0) {
                        queryMetaData.addHeader("totalMatches",totalMatches);
                        write = true;
                    }
                    else if(!canTrim && noMoreBeforePagination) {
                        queryMetaData.addHeader("totalMatches",results.size());
                        write = true;
                    }
                    if (more != null) {
                        queryMetaData.addHeader("more", String.valueOf(more));
                        write = true;
                    }
                    if (write) queryMetaData.writeHeaders(out);            
                }
                
                if(doFederation) {
                    // perform federated search, returning results from searches on other repositories
                    params.removeHeadersWithKey("federation");
                    params.addHeader("federation", false);

                    for(Iterator iter=federatedSearchTargets.iterator(); iter.hasNext(); ) {
                        FederatedSearcher fedSearch = new FederatedSearcher((String)iter.next(), context, params, out);
                        fedSearch.start();
                        federatedThreads.add(fedSearch);
                    }
                }
                if (needsPostSort) { //sort performed post search
                    results = sort(results, sortFields, indexSearcher, reverseAllSort);
                } 
                if (pageSize > 0) {
                    results = limitResultsToPage(results, pageSize, pageOffset);
                }
            } else {
                TopDocs hits  = collector.topDocs(pageSize*pageOffset, pageSize + 1);
                results = Arrays.asList(hits.scoreDocs);
                Boolean more = null;
                if (results.size() > pageSize) more = Boolean.TRUE;
                else if (totalMatches > pageSize * pageOffset + pageSize) more = Boolean.TRUE;
                else if (totalMatches >= 0 && totalMatches <= pageSize * pageOffset + pageSize) more = Boolean.FALSE;
                HeaderSet queryMetaData = new HeaderSet("queryInfo");
                boolean write = false;
                if (totalMatches >= 0) {
                    queryMetaData.addHeader("totalMatches",totalMatches);
                    write = true;
                }
                if (more != null) {
                    queryMetaData.addHeader("more", String.valueOf(more));
                    write = true;
                }
                if (write) queryMetaData.writeHeaders(out);
            }

            HeaderSet result = new HeaderSet("result");
            int count = 0;
            for (ScoreDoc scoreDoc : results) {
                result.removeAllHeaders();
                Document doc = indexSearcher.doc(scoreDoc.doc);
                String objectID = doc.get("id");
                if(objectID==null) continue;
                result.addHeader("objectid", objectID);
                result.addHeader("repoid", serverMain.getServerID());
                if(!Float.isNaN(scoreDoc.score)) result.addHeader("score", String.valueOf(scoreDoc.score));
                for(IndexableField f : doc.getFields()) {
                	if (returnedFields==null) {
                		addField(result, f);
                	}
                	else if (returnedFields.contains(f.name())) { 
                		addField(result, f);
                	}
                	else if(escapedElementAttContains(returnedFields,f.name())) {
                        addField(result, f);
                	}
                }
                synchronized(out) {
                    result.writeHeaders(out);
                }
                count++;
                if (pageSize > 0 && count >= pageSize) break;
            }
        }
        finally {
            indexSearcherWrapper.releaseAndCloseIfNeeded();
        }

        // wait for the federated searches to finish
        for(Iterator iter=federatedThreads.iterator(); iter.hasNext(); ) {
            ((Thread)iter.next()).join();
        }

        out.close();
    }

    private Query parseCorrectingSlashesIfNeeded(String queryStr) throws ParseException {
// We'd love to have regexp query expressions (introduced in Lucene 4) work, but
// a bug in Lucene makes the following attempt not work. (A NullPointerException is thrown later on.)
//        try {
//            return indexBuilder.getQueryParser().parse(queryStr);
//        } catch(ParseException e) {
            String queryStrFixed = fixSlashes(queryStr);
//            if(queryStrFixed.equals(queryStr)) throw e;
            return indexBuilder.getQueryParser().parse(queryStrFixed);
//        }
    }
    
    static String fixSlashes(String s) {
        if(s==null) return null;
        return s.replaceAll("(?<!\\\\)/","\\\\/");
    }
    
    // A bit of a hack to allow users to specify unescaped names and get escaped names returned
    private static boolean escapedElementAttContains(List<String> returnedFields, String name) {
        if(name==null) return false;
        if(!name.startsWith("el")) return false;
        int underscore1 = name.indexOf("_");
        if(underscore1 < 0) return false;
        int underscore2 = name.indexOf("_",underscore1+1);
        if(underscore2 < 0) return false;
        String unescapedName = name.substring(0,underscore1) + StringUtils.decodeURLIgnorePlus(name.substring(underscore1,underscore2)) + name.substring(underscore2);
        return returnedFields.contains(unescapedName);
    }
    
	private void addField(HeaderSet result, IndexableField f) {
		String val = f.stringValue();
		if(val==null) val = "";
		result.addHeader("field:"+f.name(), val);
	}
	
	private SortField getSortFieldAfterProcessing(String sortFieldNameOnly, boolean thisReverse, boolean useLuceneSortableOnly) {
        if(useLuceneSortableOnly) sortFieldNameOnly = indexBuilder.getSortFieldName(sortFieldNameOnly);
        if(sortFieldNameOnly==null) return null;
        return new SortField(sortFieldNameOnly,SortField.Type.STRING,thisReverse);
	}
	
	private SortField getSortField(String sortFieldString, boolean reverseAllSort, boolean useLuceneSortableOnly) {
        if(sortFieldString.toUpperCase().endsWith(" ASC")) {
            String sortFieldNameOnly = sortFieldString.substring(0,sortFieldString.length()-4);
            return getSortFieldAfterProcessing(sortFieldNameOnly,reverseAllSort,useLuceneSortableOnly);
        }
        else if(sortFieldString.toUpperCase().endsWith(" DESC")) {
            String sortFieldNameOnly = sortFieldString.substring(0,sortFieldString.length()-5);
            return getSortFieldAfterProcessing(sortFieldNameOnly,!reverseAllSort,useLuceneSortableOnly);
        }
        else {
            String sortFieldNameOnly = sortFieldString;
            return getSortFieldAfterProcessing(sortFieldNameOnly,reverseAllSort,useLuceneSortableOnly);
        }
	}
	
	private Sort createSort(List<String> sortFields, boolean reverseAllSort) {
		List<SortField> sortFieldObjects = new ArrayList<SortField>();
		for (String sortFieldString : sortFields) {
		    SortField sortField = getSortField(sortFieldString,reverseAllSort,true);
			if(sortField==null) continue;
			sortFieldObjects.add(sortField); 
		}
		if(sortFieldObjects.isEmpty()) return null;
		SortField[] sortFieldArray = sortFieldObjects.toArray(new SortField[sortFieldObjects.size()]);
		return new Sort(sortFieldArray);
	}
	
	//Filters out those search results that this context is not permitted to access
	private List<ScoreDoc> limitResultsToPermitted(List<ScoreDoc> results, DOOperationContext context, IndexSearcher indexSearcher, int pageSize, int pageOffset, boolean canTrim) 
		throws IOException {
		List<ScoreDoc> permittedResults = new ArrayList<ScoreDoc>();
		net.cnri.apps.doserver.Authorizer authorizer = serverMain.getAuthorizer();
		for (ScoreDoc scoreDoc : results) {
			DocumentStoredFieldVisitor fieldVisitor = new DocumentStoredFieldVisitor("id");
            indexSearcher.doc(scoreDoc.doc,fieldVisitor);
            Document doc = fieldVisitor.getDocument();
            String objectID = doc.get("id");
            if(objectID==null) continue;
            if(authorizer.operationIsAllowed(context, objectID, DOConstants.GET_DATA_OP_ID)) {
            	permittedResults.add(scoreDoc);
                if (canTrim && pageSize > 0 && permittedResults.size() > (pageSize * pageOffset + pageSize)) break;
            }
		}
		return permittedResults;
	}
    
	//returns a limited list of the results. pageOffset begins at zero.
	private List<ScoreDoc> limitResultsToPage(List<ScoreDoc> results, int pageSize, int pageOffset) {
		int fromIndex = pageSize*pageOffset;
		int toIndex = fromIndex+pageSize;
		return limitResults(results, fromIndex, toIndex);
	}
	
	//fromIndex is inclusive, toIndex is exclusive.
	private List<ScoreDoc> limitResults(List<ScoreDoc> results, int fromIndex, int toIndex) {
		if(results.size() <= fromIndex || fromIndex >= toIndex) return new ArrayList<ScoreDoc>();
		if(fromIndex < 0) fromIndex = 0;
		if(toIndex > results.size()) toIndex = results.size();
		List<ScoreDoc> resultsPage = results.subList(fromIndex, toIndex);
		return resultsPage;
	}   
    
    private List<String> getFieldsFromString(String s) {
        if(s.isEmpty()) return null;
    	return Arrays.asList(s.split(","));
    }
    
    private class FederatedSearcher
    extends Thread
    {
        private String searchTarget;
        private DOOperationContext context;
        private HeaderSet params;
        private OutputStream out;

        FederatedSearcher(String searchTarget, DOOperationContext context, HeaderSet params,
                OutputStream out) {
            this.searchTarget = searchTarget;
            this.context = context;
            this.params = params;
            this.out = out;
        }

        public void run() {
            try {
                logger.debug("federating search to {}",searchTarget);
                context.performOperation(null, searchTarget, SEARCH_OP_ID, params,
                        new ByteArrayInputStream(new byte[0]), 
                        new DontCloseOutputStream(this, out));
                logger.debug("finished search federation to {}",searchTarget);
            } catch (Exception e) {
                logger.error("Error federating search to index: "+searchTarget,e);
            }
        }

        public String toString() {
            return "fedsearch at '"+searchTarget+"' for terms '"+params+"'";
        }

        void receivedResult(byte line[]) 
        throws IOException
        {
            synchronized(out) { out.write(line); }
            if(logger.isDebugEnabled()) logger.debug("result from {}: {}",searchTarget,new String(line));
        }
    }
    
    //Sorting the results post search rather than using the Lucene sort as Lucene cannot sort on tokenized fields.
    private List<ScoreDoc> sort(List<ScoreDoc> scoreDocs, List<String> sortFields, IndexSearcher indexSearcher, boolean reverseAllSort) {
    	List<ScoreDoc> result = new ArrayList<ScoreDoc>(scoreDocs.size());
    	List<SortableScoreDoc> sortableScoreDocs = new ArrayList<SortableScoreDoc>(scoreDocs.size());
    	SortField[] sortFieldsProcessed = new SortField[sortFields.size()];
    	String[] sortFieldsNameOnly = new String[sortFields.size()];
    	for (int i = 0; i < sortFields.size(); i++) {
    	    String sortFieldString = sortFields.get(i);
    	    sortFieldsProcessed[i] = getSortField(sortFieldString,reverseAllSort,false);
    	    sortFieldsNameOnly[i] = sortFieldsProcessed[i].getField();
        }
    	for (ScoreDoc scoreDoc : scoreDocs) {
    		Document doc = null;
    		try {
    			DocumentStoredFieldVisitor fieldVisitor = new DocumentStoredFieldVisitor(sortFieldsNameOnly);
    			indexSearcher.doc(scoreDoc.doc,fieldVisitor);
    			doc = fieldVisitor.getDocument();
    			sortableScoreDocs.add(new SortableScoreDoc(scoreDoc, doc, sortFieldsProcessed));
    		} catch (IOException ioe){
    		    logger.error("Error sorting",ioe);
    		}
    	}
    	Collections.sort(sortableScoreDocs);
    	for (SortableScoreDoc ssd : sortableScoreDocs) {
    		result.add(ssd.scoreDoc);
    	}
    	return result;
    }

    private class SortableScoreDoc implements Comparable<SortableScoreDoc>{
    	private final ScoreDoc scoreDoc;
    	private final Document doc;
    	private final SortField[] sortFields;
    	    	
    	public SortableScoreDoc(ScoreDoc scoreDoc, Document doc, SortField[] sortFields) {
    		this.scoreDoc = scoreDoc;
            this.sortFields = sortFields;
            this.doc = doc;
    	}
        
    	public int compareTo(SortableScoreDoc other) {
    		for (int i = 0; i < sortFields.length; i++) {
    		    String sortField = sortFields[i].getField();
    		    int reverse = sortFields[i].getReverse() ? -1 : 1;
    			String thisField = get(sortField);
    			String otherField = other.get(sortField);
      			if ((thisField != null)&&(otherField == null)) return reverse;  
    			if ((thisField == null)&&(otherField != null)) return -reverse; 
    			if ((thisField == null)&&(otherField == null)) continue; //compare the next field
    			if (thisField.compareTo(otherField) == 0) continue; //compare the next field
    			return reverse * thisField.compareTo(otherField);
    		}
    		return 0; 
    	}
    	
    	public String get(String key) {
    		IndexableField field = doc.getField(key);
            if(field!=null) {
                String value = field.stringValue();
                return value;
            }
            else return null;
    	}
    }

    private class DontCloseOutputStream
    extends OutputStream
    {
        private OutputStream out;
        private ByteArrayOutputStream bout;
        private boolean sawStatusLine = false;
        private FederatedSearcher searcher;

        DontCloseOutputStream(FederatedSearcher searcher, OutputStream out) {
            this.searcher = searcher;
            this.out = out;
            this.bout = new ByteArrayOutputStream();
        }

        public void write(int b) 
        throws IOException
        {
            bout.write(b);
            if(((byte)b)=='\n') {
                byte line[] = bout.toByteArray();
                // TODO: Check for first line, which should be the operation's status and skip it
                if(!sawStatusLine) {
                    if(logger.isDebugEnabled()) logger.debug("got status from search: {}",net.handle.hdllib.Util.decodeString(line));
                    sawStatusLine = true;
                } else {
                    searcher.receivedResult(line);
                }
                bout.reset();
            }
        }

        public void close() {
            // noop
        }
    }


    /** Updates the indexes from any sources specified in the config file */
    private void updateIndex(boolean bigUpdate) {
        boolean txnUpdateStillNeeded = txnUpdateNeeded;
        StreamTable indexConfig = null;
        try {
            // check for the existence of the "suppress_indexing" file
            if(suppressIndexingFile.exists()) return;

            if(bigUpdate) {
                // (re-)initialize the authentication used for indexing
                CollaborationHub hub = serverMain.getServiceRegistry();
                Object keyrings[] = hub.lookupEntity(null, "lucene-indexing-keyring", DOKeyRing.class);
                if(keyrings!=null && keyrings.length>0) {
                    indexBuilder.setKeyRing((DOKeyRing)keyrings[0]);
                } else {
                    indexBuilder.setKeyRing(null);
                }
            }

            if(bigUpdate) {
                InputStream in = null;
                indexConfig = new StreamTable();
                try {
                    if(repoStorage.doesDataElementExist(LUCENE_CONFIG_ELEMENT)) {
                        in = repoStorage.getDataElement(LUCENE_CONFIG_ELEMENT);
                    }
                    if(in!=null) {
                        indexConfig.readFrom(in);

                        Object fedVect = indexConfig.get(FEDERATED_INDEXES_KEY);
                        if(fedVect!=null && fedVect instanceof List) {
                            federatedSearchTargets = (List)fedVect;
                        } else {
                            federatedSearchTargets = new ArrayList();
                        }
                    } else {
                        federatedSearchTargets = new ArrayList();
                    }
                } catch (Exception e) {
                    logger.error("Error reading configuration",e);
                    return;
                } finally {
                    try { in.close(); } catch (Exception e) {}
                }
            }    
            txnUpdateNeeded = false;
            txnUpdateStillNeeded = false;
        }
        finally {
            if(txnUpdateStillNeeded) {
                try {
                    Thread.sleep(30 * 1000);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // retrieve any updates from the repository in which we live
        indexWriterLock.readLock().lock();
        try {
            updateFromContainer();
            if(bigUpdate) {
                // retrieve any updates from other repositories
                StreamTable config = (StreamTable)indexConfig.deepClone();
                StreamVector sources = (StreamVector)config.get("sources");
                for(int i=0; sources!=null && i<sources.size(); i++) {
                    updateFromSource(String.valueOf(sources.get(i)));
                }

                // scan objects that other indexes couldn't scan in the hopes that we 
                // have the necessary rights to access and decrypt those objects
                StreamVector sourceIndexes = (StreamVector)indexConfig.get("srcindexes");
                for(int i=0; sourceIndexes!=null && i<sourceIndexes.size(); i++) {
                    updateFromIndex(String.valueOf(sourceIndexes.get(i)));
                }
            }
        } finally {
            indexWriterLock.readLock().unlock();
        }
    } 
    
    

    /** Updates the index from the given source */
    private void updateFromIndex(String indexID) {
        logger.debug("Updating from index: {}",indexID);
        StreamTable status = (StreamTable)indexScanStatus.get("index:"+indexID);
        synchronized(indexScanStatus) {
            if(status==null) {
                status = new StreamTable();
                indexScanStatus.put("index:"+indexID, status);
            }
        }
        DOClient client = new DOClient(auth);
        HashMap repos = new HashMap();

        HeaderSet params = new HeaderSet();
        String lastTimeStamp = status.getStr("last_idx_timestamp", null);
        StringBuffer query = new StringBuffer();
        query.append("idxerror:").append(DOException.PERMISSION_DENIED_ERROR).append(' ');
        if(lastTimeStamp!=null) {
            query.append("dtindexed:[").append(lastTimeStamp).append(" TO 999999999999999999999] ");
        }
        params.addHeader("query", query.toString());

        DOAuthentication localAuth = auth;
        if(localAuth==null) {
            localAuth = PKAuthentication.getAnonymousAuth();
        }

        StreamPair io = null;
        try {
            io = client.performOperation(indexID, SEARCH_OP_ID, params);
            HeaderSet response = new HeaderSet();
            InputStream in = io.getInputStream();

            while(response.readHeaders(in)) {
                String objectID = response.getStringHeader("objectid", null);
                if(objectID==null) continue;
                String repoID = response.getStringHeader("repoid", null);
                logger.info("indexing restricted record: {} @ {}",objectID,repoID);

                if(repoID==null) {
                    repoID = DOClient.resolveRepositoryID(objectID);
                }

                Repository repo = (Repository)repos.get(repoID);
                if(repo==null) {
                    repo = new Repository(localAuth, repoID);
                    repos.put(repoID, repo);
                }

                updateObjectInIndex(repo, objectID);

                String lastIndexed = response.getStringHeader("field:dtindexed", null);
                if(lastIndexed!=null) {
                    status.put("last_idx_timestamp", lastIndexed.trim());
                }
            }
        } catch (Exception e) {
            logger.error("Error updating from index "+indexID,e);
        } finally {
            try { io.close(); } catch (Throwable t) {}
        }
    }

    private void updateFromContainer() {
        HashMap updatedObjects = new HashMap();

        try {
            long lastTimestamp = latestTransactionTimestampIndexed;
            ArrayList objectsToUpdate = new ArrayList();

            if(lastTimestamp==-1) {
                // we haven't done an update yet, scan all the objects in the repository
                // scan all of the objects...
                Enumeration allObjects = repoStorage.listObjects();
                while(allObjects.hasMoreElements()) {
                    updateObjectFromStorage((String)allObjects.nextElement());
                }

                lastTimestamp = serverMain.getTxnQueue().getLastTimestamp();
            } else {
                // we are updating all objects since the last transaction update
                CloseableEnumeration txnEnum = serverMain.getTxnQueue().getCloseableScanner(lastTimestamp);
                try {
                    while(txnEnum.hasMoreElements()) {
                        Transaction txn = (Transaction)txnEnum.nextElement();

                        if(txn.objectID==null) continue; // ignore all non-object specific transactions

                        switch(txn.action) {
                        case Transaction.ACTION_COMMENT:
                            break;
                        case Transaction.ACTION_OBJ_DEL:
                        case Transaction.ACTION_OBJ_ADD:
                        case Transaction.ACTION_OBJ_DATA_UPDATE:
                        case Transaction.ACTION_OBJ_DATA_DELETE:
                        case Transaction.ACTION_OBJ_ATTRIBUTE_UPDATE:
                        case Transaction.ACTION_OBJ_ATTRIBUTE_DELETE:
                        case Transaction.ACTION_LOGGED_ACTION:
                            if(!updatedObjects.containsKey(txn.objectID)) {
                                // only update the object once per session
                                objectsToUpdate.add(txn.objectID);
                                updatedObjects.put(txn.objectID, "");
                            }
                            break;
                        }

                        lastTimestamp = txn.timestamp;

                        if(objectsToUpdate.size() >= NUM_OBJECTS_BATCH_SIZE) {
                            break;
                        }
                    }
                } finally {
                    txnEnum.close();
                }

                for(Iterator iter=objectsToUpdate.iterator(); iter.hasNext(); ) {
                    String objectID = (String)iter.next();
                    logger.debug("updating object: {} from storage",objectID);
                    updateObjectFromStorage(objectID);
                }
            }

            latestTransactionTimestampIndexed = lastTimestamp;
            synchronized (latestTransactionIndexedSync) { latestTransactionIndexedSync.notifyAll(); }
            indexScanStatus.put("self_scan_txn", lastTimestamp);
        } catch (Exception e) {
            logger.error("Error trying to get txns from storage",e);
        }
    }



    /** Updates the index from the given source */
    private void updateFromSource(String sourceID) {
        StreamTable sourceStatus;

        DOClientConnection connection = null;
        DOServiceInfo svcInfo = null;
        logger.debug("updating from {}",sourceID);
        try {
            svcInfo = new DOServiceInfo(sourceID);
        } catch (Exception e) {
            logger.error("Error resolving source: "+sourceID,e);
            return;
        }

        synchronized(indexScanStatus) {
            sourceStatus = (StreamTable)indexScanStatus.get(sourceID);
            if(sourceStatus==null) {
                indexScanStatus.put(sourceID, sourceStatus = new StreamTable());
            }
        }


        for(int i=0; i<svcInfo.getServerCount(); i++) {
            DOServerInfo svrInfo = svcInfo.getServer(i);
            if(svrInfo==null) continue;

            String serverID = svrInfo.getServerID();
            StreamTable serverStatus = null;
            synchronized(sourceStatus) {
                serverStatus = (StreamTable)sourceStatus.get(serverID);
                if(serverStatus==null) {
                    sourceStatus.put(serverID, serverStatus = new StreamTable());
                }
            }

            logger.debug("updating from: src={}; svr={}",sourceID,serverID);
            HeaderSet params = new HeaderSet();
            if(serverStatus.containsKey("last_txn_id")) {
                params.addHeader("txn_id", serverStatus.getStr("last_txn_id", null));
            }

            StreamPair io = null;
            Repository repo = null;
            DOClientConnection conn = null;
            try {
                DOAuthentication localAuth = auth;
                if(localAuth==null) {
                    localAuth = PKAuthentication.getAnonymousAuth();
                }
                conn = new DOClientConnection(localAuth);
                conn.setUseEncryption(false);
                conn.connect(svrInfo);

                repo = new Repository(sourceID, conn);

                io = conn.performOperation(sourceID, DOConstants.GET_REPO_TXNS_OP_ID,
                        params);

                InputStream in = io.getInputStream();
                HeaderSet record = new HeaderSet();
                HashMap updatedObjects = new HashMap();

                while(record.readHeaders(in)) {
                    String timestampStr = record.getStringHeader("tstamp", null);
                    if(timestampStr==null) {
                        logger.warn("Warning:  ignoring received transaction with "+
                                "null timestamp: "+record);
                        continue;
                    }

                    String objID = record.getStringHeader("object_id", null);
                    if(objID==null) continue; // ignore all non-object specific transactions
                    long timestamp = record.getLongHeader("tstamp", 0);

                    String txnType = record.getStringHeader(PARAM_TXN_TYPE, "");
                    if(txnType.equals(TXN_TYPE_COMMENT)) {
                        // nothing to index...
                    } else if(txnType.equals(TXN_TYPE_DEL_OBJ)) {
                        // delete the object from the index
                        deleteObjectFromIndex(objID);
                    } else {
                        // for all other modifications update the object in the index

                        if(!updatedObjects.containsKey(objID)) {
                            // only update the object once per session
                            updateObjectInIndex(repo, objID);
                            updatedObjects.put(objID, "");
                        }
                    }

                    serverStatus.put("last_txn_id", timestamp);
                }
            } catch (Exception e) {
                logger.error("Error trying to get txns from src="+sourceID+
                        " svr="+serverID,e);
            } finally {
                try { io.close(); } catch (Throwable t) {}
                try {
                    conn.close();
                } catch (Throwable t) {
                    logger.warn("Warning: Error closing connection to "+sourceID,t);
                }
                conn = null;
            }
        }
    }


    private void saveSourceStatus() {
        try {
            synchronized(indexScanStatus) {
                indexScanStatus.writeToFile(scanStatusFile);
            }
        } catch (Exception e) {
            logger.error("Error saving replication status",e);
        }
    }

    /** Re-retrieve the object and add it to the index */
    private void deleteObjectFromIndex(String objID) 
    throws IOException
    {
        logger.debug("deleting object: {}",objID);
        
        indexWriter.deleteDocuments(new Term("id", objID));
    }

    /** Update the object directly from the storage module */
    private void updateObjectFromStorage(final String objectID) 
    throws Exception
    {
        logger.debug("Indexing object from storage: {}",objectID);
        Thread thread = Thread.currentThread();
        String oldThreadName = thread.getName();
        thread.setName("Indexing object '"+objectID+"' directly from storage");
        try {
            final StorageProxy objStore = repoStorage.getObjectAccessor(objectID);
            if(!objStore.doesObjectExist()) {
                indexWriter.deleteDocuments(new Term("id", objectID)); // delete any old entries for this ID
                return;
            }
            HeaderSet attributes = objStore.getAttributes(null);
            if (attributes.getBooleanHeader("internal.skipIndex", false)) {
                logger.debug("Skipping indexing for ", objectID);
                return;
            }
            
            boolean async = attributes.getBooleanHeader("internal.asyncIndex", false);
            if (async) {
                backgroundIndexerExecServ.execute(new Runnable() {
                    @Override
                    public void run() {
                        List<Runnable> cleanupActions = new ArrayList<Runnable>();
                        try {
                            Document doc;
                            if (indexBuilder instanceof IndexBuilder2) {
                                doc = ((IndexBuilder2) indexBuilder).documentOfStorageProxy(objStore, cleanupActions);
                            } else {
                                doc = indexBuilder.documentOfStorageProxy(objStore);
                            }
                            indexWriter.updateDocument(new Term("id", objectID),doc);  // add the new document
                        } catch (IOException e) {
                            logger.debug("Error indexing in background thread", e);
                        } finally {
                            for (Runnable action : cleanupActions) {
                                action.run();
                            }
                        }
                    }
                });
            } else {
                List<Runnable> cleanupActions = new ArrayList<Runnable>();
                try {
                    Document doc;
                    if (indexBuilder instanceof IndexBuilder2) {
                        doc = ((IndexBuilder2) indexBuilder).documentOfStorageProxy(objStore, cleanupActions);
                    } else {
                        doc = indexBuilder.documentOfStorageProxy(objStore);
                    }
                    indexWriter.updateDocument(new Term("id", objectID),doc);  // add the new document
                } finally {
                    for (Runnable action : cleanupActions) {
                        action.run();
                    }
                }
            }
        } finally {
            thread.setName(oldThreadName);
        }
    }

    /** Re-retrieve the object and add it to the index */
    private void updateObjectInIndex(Repository repo, String objectID) 
    throws IOException
    {
        DigitalObject obj = repo.getDigitalObject(objectID);
        List<Runnable> cleanupActions = new ArrayList<Runnable>();
        try {
            Document doc;
            if (indexBuilder instanceof IndexBuilder2) {
                doc = ((IndexBuilder2) indexBuilder).documentOfDigitalObject(obj, cleanupActions);
            } else {
                doc = indexBuilder.documentOfDigitalObject(obj);
            }
            indexWriter.updateDocument(new Term("id", objectID),doc);  // add the new document
        } finally {
            for (Runnable action : cleanupActions) {
                action.run();
            }
        }
    }

    private void sendErrorResponse(OutputStream out, String msg, int code)
    throws IOException
    {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "error");
        if(msg!=null)
            response.addHeader("message", msg);
        if(code>=0)
            response.addHeader("code", code);
        response.writeHeaders(out);
        out.flush();
    }

    private void sendSuccessResponse(OutputStream out)
    throws IOException
    {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "success");
        response.writeHeaders(out);
        out.flush();
    }


}


