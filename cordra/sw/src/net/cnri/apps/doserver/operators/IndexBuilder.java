/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;

import net.cnri.apps.doserver.Main;
import net.cnri.do_api.DOKeyRing;
import net.cnri.do_api.DigitalObject;
import net.cnri.dobj.DOException;
import net.cnri.dobj.StorageProxy;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

public interface IndexBuilder {
// TODO
//    Note there is one case in LuceneIndexer.java where a search is performed, to determine recent permission denied errors:
//        StringBuffer query = new StringBuffer();
//        query.append("idxerror:").append(DOException.PERMISSION_DENIED_ERROR).append(' ');
//        if(lastTimeStamp!=null) {
//            query.append("dtindexed:[").append(lastTimeStamp).append(" TO 999999999999999999999] ");
//        }
//        params.addHeader("query", query.toString());
    
 // TODO reindexing presumes a stored "id" field
    
    /** Initialize the index builder and notify it of its containing server Main instance. */
    public void init(Main serverMain);
    
    /** Returns the analyzer used to write documents into the index. */
    public Analyzer getAnalyzer();
    
    /**
     * Returns a query parser to parse queries to this index.  Returns a new instance of QueryParser each time,
     * as these objects are lightweight and not thread-safe.
     */
    public QueryParser getQueryParser();

    /** Sets the DOKeyRing which could be used by the index builder to index encrypted documents. */
    public void setKeyRing(DOKeyRing keyring);
    
    /** Creates a Lucene Document from an object in local storage. */
    public Document documentOfStorageProxy(StorageProxy objStore) throws DOException, IOException;    

    /** Creates a Lucene Document from an object accessed remotely. */
    public Document documentOfDigitalObject(DigitalObject obj) throws DOException, IOException;
    
    /** Returns the name of the field that will be used for sorting. */
    public String getSortFieldName(String field);
    
    /** Returns a query which matches objects which need to be reindexed.
     * It is the responsibility of the document... methods to introduce version information into the Lucene documents.
     */
    public Query objectsNeedingReindexingQuery();
}
