/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.cnri.apps.doserver.Main;
import net.cnri.do_api.DOKeyRing;
import net.cnri.do_api.DataElement;
import net.cnri.do_api.DigitalObject;
import net.cnri.dobj.DOConstants;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StorageProxy;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultIndexBuilder implements IndexBuilder2 {
    static final Logger logger = LoggerFactory.getLogger(DefaultIndexBuilder.class);
    private static final int VERSION = 2;
    
    private Main serverMain;
    /** The analyzer used by the IndexBuilder. */
    protected PerFieldAnalyzerWrapper analyzer;
    protected Map<String,Analyzer> analyzerMap = new ConcurrentHashMap<String, Analyzer>();

    private volatile DOKeyRing keyring;
    
    public DefaultIndexBuilder() {}

    public void init (Main serverMain) {
        this.serverMain = serverMain;
        initAnalyzer();
    }

    private void initAnalyzer() {
        analyzerMap.put("id",new KeywordAnalyzer());
        analyzerMap.put("repoid",new KeywordAnalyzer());
        analyzerMap.put("indexVersion",new WhitespaceAnalyzer(Version.LUCENE_47));
        analyzer = new PerFieldAnalyzerWrapper(new AlphanumericAnalyzer(),  analyzerMap);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }
    
    /** Convenience method to subclasses to change the analyzer used for particular fields */
    protected void addAnalyzer(String fieldName, Analyzer analyzer) {
        this.analyzerMap.put(fieldName,analyzer);
        this.analyzer = new PerFieldAnalyzerWrapper(new AlphanumericAnalyzer(), analyzerMap);
    }
    
    /**
     * Returns a query parser to parse queries to this index.  Returns a new instance of QueryParser each time,
     * as these objects are lightweight and not thread-safe.
     * 
     * @return a query parser to parse queries to this index.
     */
    public QueryParser getQueryParser() {
        return new QueryParser(Version.LUCENE_47, null, getAnalyzer());
    }
    
    public void setKeyRing(DOKeyRing keyring) {
        this.keyring = keyring;
    }

    @Override
	public String getSortFieldName(String field) {
		return "sort_" + field;
	}
    
    public Document documentOfStorageProxy(StorageProxy objStore, Collection<Runnable> cleanupActions) throws DOException, IOException {
        return documentOfStorageProxy(objStore);
    }

    public Document documentOfStorageProxy(StorageProxy objStore) throws DOException, IOException {
        Document doc = new Document();
        doc.add(new TextField("indexVersion",this.getClass().getName() + " " + VERSION,Field.Store.NO));
        
        doc.add(new StringField("id", objStore.getObjectID(), Field.Store.YES));

        doc.add(new StringField("repoid", objStore.getRepoID(), Field.Store.YES));

        // store the object attributes in the index
        HeaderSet attributes = objStore.getAttributes(null);
        for(Iterator iter=attributes.iterator(); iter.hasNext(); ) {
            HeaderItem attribute = (HeaderItem)iter.next();
            doc.add(new TextField("objatt_"+attribute.getName(),
                    attribute.getValue(),
                    Field.Store.YES));
            doc.add(new StringField(getSortFieldName("objatt_"+attribute.getName()),
            		attribute.getValue(),
            		Field.Store.NO));
        }

        doc.add(new StringField("objcreated", 
                DateTools.dateToString(new java.util.Date(attributes.getLongHeader(DOConstants.DATE_CREATED_ATTRIBUTE, 0)),
                        DateTools.Resolution.MILLISECOND),
                        Field.Store.YES));
        doc.add(new StringField("objmodified", 
                DateTools.dateToString(new java.util.Date(attributes.getLongHeader(DOConstants.DATE_MODIFIED_ATTRIBUTE, 0)),
                        DateTools.Resolution.MILLISECOND),
                        Field.Store.YES));

        for(Enumeration en=objStore.listDataElements(); en.hasMoreElements(); ) {
            String elementID = (String)en.nextElement();
            addFieldForElement(doc, objStore.getObjectID(), objStore, elementID);
        }

        //if(DEBUG) System.err.println("storing "+objID+" in index as doc:"+doc);
        doc.add(new StringField("dtindexed",
                DateTools.dateToString(new Date(),
                        DateTools.Resolution.MILLISECOND),
                        Field.Store.YES));
        return doc;
    }
    
    /** Index the given data element and add the contents to the given lucene document. */
    private void addFieldForElement(Document doc, String objectID, StorageProxy objStore, String elementID) {
        logger.debug("  element: {}",elementID);
        InputStream in = null;
        try {
            final String escapedElementID = escapeElementID(elementID);
            final boolean escapedSame = elementID.equals(escapedElementID);

            HeaderSet attributes = objStore.getElementAttributes(elementID, null);
            for(Iterator iter=attributes.iterator(); iter.hasNext(); ) {
                HeaderItem attribute = (HeaderItem)iter.next();
                doc.add(new TextField("elatt_"+escapedElementID+"_"+attribute.getName(), 
                        attribute.getValue(),
                        Field.Store.YES));
                doc.add(new StringField(getSortFieldName("elatt_"+escapedElementID+"_"+attribute.getName()),
                		attribute.getValue(),
                		Field.Store.NO));
                if(!escapedSame) {
                    doc.add(new TextField("elatt_"+elementID+"_"+attribute.getName(), 
                            attribute.getValue(),
                            Field.Store.NO));
                    doc.add(new StringField(getSortFieldName("elatt_"+elementID+"_"+attribute.getName()), 
                            attribute.getValue(),
                            Field.Store.NO));
                }
            }
        } catch (Exception e) {
            logger.error("error getting element "+elementID+" from object "+objectID,e);
        } finally {
            try { if(in!=null) in.close(); } catch (Throwable t) {}
        }


    }

    public Document documentOfDigitalObject(DigitalObject obj, Collection<Runnable> cleanupActions) throws DOException, IOException {
        return documentOfDigitalObject(obj);
    }
    
    public Document documentOfDigitalObject(DigitalObject obj) throws DOException, IOException {
        Document doc = new Document();
        try {
            doc.add(new TextField("indexVersion",this.getClass().getName() + " " + VERSION,Field.Store.NO));
            doc.add(new StringField("id", obj.getID(), Field.Store.YES));
            doc.add(new StringField("repoid", obj.getRepository().getID(), Field.Store.YES));
            String[] elementIDs = obj.listDataElements();
            String attributes[] = obj.listAttributes();
            for(int i=0; attributes!=null && i<attributes.length; i++) {
                String attName = attributes[i];
                doc.add(new TextField("objatt_"+attName,
                        obj.getAttribute(attName, ""),
                        Field.Store.YES));
                doc.add(new StringField(getSortFieldName("objatt_"+attName),
                		obj.getAttribute(attName, ""),
                		Field.Store.NO));
            }
            doc.add(new StringField("objcreated", 
                    DateTools.dateToString(obj.getDateCreated(),
                            DateTools.Resolution.MILLISECOND),
                            Field.Store.YES));
            doc.add(new StringField("objmodified", 
                    DateTools.dateToString(obj.getDateLastModified(),
                            DateTools.Resolution.MILLISECOND),
                            Field.Store.YES));

            // add the different attributes to the document...
            for(int i=0; i<elementIDs.length; i++) {
                //if(!elementIDs[i].startsWith("content")) continue;
                addFieldForElement(doc, obj.getDataElement(elementIDs[i]));
            }
        } catch (DOException e) {
            logger.error("Error indexing object "+obj.getID()+" in "+obj.getRepository(),e);
            if(e.getErrorCode()!=DOException.PERMISSION_DENIED_ERROR) {
                throw e;
            }
            doc.add(new StringField("idxerror", String.valueOf(e.getErrorCode()),
                    Field.Store.YES));
        }

        //if(DEBUG) System.err.println("storing "+objID+" in index as doc:"+doc);
        doc.add(new StringField("dtindexed",
                DateTools.dateToString(new Date(),
                        DateTools.Resolution.MILLISECOND),
                        Field.Store.YES));

        return doc;
    }
    
    /** Initiate a retrieval of the given data element and add the contents to the
     * given lucene document.  Doesn't close the Reader that is passed as the Field
     * that is added to the Document. */
    private void addFieldForElement(Document doc, DataElement element) {
        logger.debug("  element: {}",element);
        String elementID = element.getDataElementID();
        InputStream in = null;
        try {
            final String escapedElementID = escapeElementID(elementID);
            final boolean escapedSame = elementID.equals(escapedElementID);

            String attributes[] = element.listAttributes();
            for(int i=0; attributes!=null && i<attributes.length; i++) {
                String attName = attributes[i];
                doc.add(new TextField("elatt_"+escapedElementID+"_"+attName, 
                        element.getAttribute(attName, ""),
                        Field.Store.YES));
                doc.add(new StringField(getSortFieldName("elatt_"+escapedElementID+"_"+attName),
                		element.getAttribute(attName, ""),
                		Field.Store.NO));
                if(!escapedSame) {
                    doc.add(new TextField("elatt_"+elementID+"_"+attName, 
                            element.getAttribute(attName, ""),
                            Field.Store.NO));
                    doc.add(new StringField(getSortFieldName("elatt_"+elementID+"_"+attName),
                    		element.getAttribute(attName, ""),
                    		Field.Store.NO));
                }
            }
        } catch (Exception e) {
            logger.error("error getting element "+element,e);
        } finally {
            try { if(in!=null) in.close(); } catch (Throwable t) {}
        }
    }


    
    private static String escapeElementID(String elementID) {
        return elementID.replace("%","%25").replace("_","%5F");
    }

    @Override
    public Query objectsNeedingReindexingQuery() {
        PhraseQuery versionQuery = new PhraseQuery();
        versionQuery.add(new Term("indexVersion",this.getClass().getName()));
        versionQuery.add(new Term("indexVersion",String.valueOf(VERSION)));
        BooleanQuery res = new BooleanQuery();
        res.add(new MatchAllDocsQuery(),Occur.MUST);
        res.add(versionQuery,Occur.MUST_NOT);
        return res;
    }
}
