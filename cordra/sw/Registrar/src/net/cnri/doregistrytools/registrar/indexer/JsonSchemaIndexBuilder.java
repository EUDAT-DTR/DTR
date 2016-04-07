/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.indexer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.util.Version;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.apps.doserver.Main;
import net.cnri.apps.doserver.operators.DefaultIndexBuilder;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StorageProxy;
import net.cnri.doregistrytools.registrar.jsonschema.Constants;
import net.cnri.doregistrytools.registrar.jsonschema.InvalidException;
import net.cnri.doregistrytools.registrar.jsonschema.JsonUtil;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RelationshipsService;
import net.cnri.doregistrytools.registrar.jsonschema.SchemaAndNode;

public class JsonSchemaIndexBuilder extends DefaultIndexBuilder {
    private static Logger logger = LoggerFactory.getLogger(JsonSchemaIndexBuilder.class);
    
    private final RegistrarService registrar;
    private final RelationshipsService relationshipsService;
    
    public JsonSchemaIndexBuilder() {
        this.registrar = null;
        this.relationshipsService = null;
    }

    public JsonSchemaIndexBuilder(RegistrarService registrar, RelationshipsService relationshipsService) {
        this.registrar = registrar;
        this.relationshipsService = relationshipsService;
    }
    
    @Override
    public void init(Main serverMain) {
        super.init(serverMain);
        addAnalyzer("type", new KeywordAnalyzer());
        addAnalyzer("aclRead", new LowerCaseKeywordAnalyzer());
        addAnalyzer("aclWrite", new LowerCaseKeywordAnalyzer());
        addAnalyzer("createdBy", new LowerCaseKeywordAnalyzer());
        addAnalyzer("remoteRepository", new KeywordAnalyzer());
        addAnalyzer("username", new KeywordAnalyzer());
        addAnalyzer("schemaName", new KeywordAnalyzer());
        addAnalyzer("internal.pointsAt", new KeywordAnalyzer());
//        try {
//            String serverPrefix = serverMain.getConfigVal("handle_prefix");
//            registrar = new RegistrarService(new InternalRepository(serverMain, new SimpleAuthenticatedCaller(serverMain.getServerID())), serverPrefix);
//        } catch (RuntimeException e) {
//            throw e;
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
    }
    
    public QueryParser getQueryParser() {
        return new QueryParser(Version.LUCENE_47, "internal.all", getAnalyzer());
    }
    
    @Override
    public Document documentOfStorageProxy(StorageProxy obj, Collection<Runnable> cleanupActions) throws DOException, IOException {
        Document doc = super.documentOfStorageProxy(obj);
        HeaderSet attributes = obj.getAttributes(null);
        String type = attributes.getStringHeader("type", null);
        String remoteRepository = attributes.getStringHeader("remoteRepository", null);
        if (remoteRepository == null) {
            doc.add(new TextField("remoteRepository", "null", Field.Store.YES));
        } else {
            doc.add(new TextField("remoteRepository", remoteRepository, Field.Store.YES));
        }
        String meta = attributes.getStringHeader("meta", null);
        if (type != null) {
            doc.add(new TextField("type", type, Field.Store.YES));
            doc.add(new StringField(getSortFieldName("type"), type, Field.Store.NO));
            if (!"true".equals(meta)) addFieldsForObjectsOfKnownTypes(doc, obj, attributes, type, remoteRepository, cleanupActions);
        }
        String createdBy = attributes.getStringHeader("createdBy", null);
        if (createdBy != null) {
            doc.add(new TextField("createdBy", createdBy, Field.Store.YES));
        }
        String username = attributes.getStringHeader("username", null);
        if (username != null) {
            doc.add(new TextField("username", username, Field.Store.YES));
        }
        String users = attributes.getStringHeader("users", null);
        if (users != null) {
            for (String user : users.split("\n")) {
                if (user.isEmpty()) continue;
                doc.add(new TextField("users", user, Field.Store.YES));
            }
        }
        String schemaName = attributes.getStringHeader("schemaName", null);
        if (schemaName != null) {
            doc.add(new TextField("schemaName", schemaName, Field.Store.YES));
        }
        addFieldForAcl(doc, attributes, "aclRead");
        addFieldForAcl(doc, attributes, "aclWrite");
        return doc;
    }
    
    private void addFieldForAcl(Document doc, HeaderSet attributes, String name) {
        String acl = attributes.getStringHeader(name, null);
        if (acl == null) {
            doc.add(new TextField(name, "missing", Field.Store.YES));
        } else {
            String[] ids = acl.split("\n");
            for (String id : ids) {
                doc.add(new TextField(name, id, Field.Store.YES));
            }
        }
    }

    private void addFieldsForObjectsOfKnownTypes(Document doc, StorageProxy obj, HeaderSet attributes, String type, String remoteRepository, Collection<Runnable> cleanupActions) {
        boolean isKnownType = false;
        boolean indexPayloads = true;
        if (registrar != null) {
            SchemaAndNode schemaAndNode = registrar.getSchema(type, remoteRepository);
            isKnownType = schemaAndNode != null;
            if (schemaAndNode != null) indexPayloads = shouldIndexPayloads(schemaAndNode.schemaNode);
            if (!isKnownType) {
                try {
                    registrar.loadPersistentMetadata();//a new type might have been created at runtime.
                } catch (Exception e) {
                    logger.warn("Exception indexing " + obj.getObjectID(), e);
                } 
                isKnownType = registrar.isKnownType(type, remoteRepository);
            }
        }
        String json = attributes.getStringHeader("json", null);
        if (json != null) {
            try {
                JsonNode jsonNode = JsonUtil.parseJson(json);
                addFieldsForJson(doc, jsonNode);
                if (indexPayloads) addPayloads(doc, jsonNode, obj, cleanupActions);
                if (isKnownType && type != null) addReferencesField(doc, obj, type, remoteRepository, jsonNode);
            } catch (InvalidException e) {
                logger.warn("Exception indexing " + obj.getObjectID(), e);
            } catch (DOException e) {
                logger.warn("Exception indexing " + obj.getObjectID(), e);
            }
        }
        if (isKnownType && type != null && json != null) {
            doc.add(new TextField("valid", "true", Field.Store.YES));
        } 
    }
    
    private static boolean shouldIndexPayloads(JsonNode schemaNode) {
        JsonNode indexPayloadsProperty = JsonUtil.getDeepProperty(schemaNode, Constants.REPOSITORY_SCHEMA_KEYWORD, "indexPayloads");
        if (indexPayloadsProperty == null) return true;
        if (!indexPayloadsProperty.isBoolean()) return true;
        return indexPayloadsProperty.asBoolean();
    }
    
    private void addPayloads(Document doc, JsonNode jsonNode, StorageProxy obj, Collection<Runnable> cleanupActions) throws InvalidException, DOException {
        @SuppressWarnings("unchecked")
        Enumeration<String> elementNames = obj.listDataElements();
        for (String elementName : Collections.list(elementNames)) {
            // Name of the field is the element name.
            // However, for backward compatibility with json-pointer-payloads, we turn payloads named
            // with json pointers to array elements into wildcards.
            String wildcardPayloadPointer = elementName;
            if (JsonUtil.isPayloadPointer(elementName)) wildcardPayloadPointer = JsonUtil.convertJsonPointerToUseWildCardForArrayIndices(elementName, jsonNode);
            final InputStream inputStream = obj.getDataElement(elementName);
            if (inputStream != null) {
//                closeOnCleanup(cleanupActions, inputStream);
                try {
                    addPayloadTokensToDocument(inputStream, doc, wildcardPayloadPointer, obj.getObjectID());
                } finally {
                    try { inputStream.close(); } catch (Exception e) { }
                }
            } else {
                logger.warn("Exception problem: Input stream is null");
            }
        }
    }

//    private void closeOnCleanup(Collection<Runnable> cleanupActions, final Closeable closeable) {
//        cleanupActions.add(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    closeable.close();
//                } catch (IOException e) {
//                    // ignore
//                }
//            }
//        });
//    }

    private void addPayloadTokensToDocument(InputStream stream, Document doc, String payloadFieldName, String objectId) {
        Exception exception = null;
        Reader reader = null;
        StringBuilder sb = new StringBuilder();
        try {
            Tika tika = new Tika();
            reader = tika.parse(stream);
            char[] buf = new char[1024];
            int r;
            try {
                while ((r = reader.read(buf)) > 0) {
                    sb.append(buf, 0, r);
                }
            } catch (Exception e) {
                exception = e;
            }
        } catch (Exception e) {
            exception = e;
        } finally {
            try { reader.close(); } catch (IOException e) { }
        }
        String extractedText = sb.toString();
        doc.add(new TextField(payloadFieldName, extractedText, Field.Store.NO));
        doc.add(new TextField("internal.all", extractedText, Field.Store.NO));
        if (exception != null) {
            logger.warn("Exception indexing payload " + payloadFieldName + " of " + objectId, exception);
            doc.add(new TextField("payload_indexing_exception", "true", Field.Store.YES));
        }
    }
    
//    public Reader parseToReader(InputStream stream) throws IOException {
//        Tika tika = new Tika();
//        return tika.parse(stream);
//    }
    
//    public String parseToPlainText(InputStream stream) throws IOException, SAXException, TikaException {
//        AutoDetectParser parser = new AutoDetectParser();
//        BodyContentHandler handler = new BodyContentHandler();
//        Metadata metadata = new Metadata();
//        try {
//            parser.parse(stream, handler, metadata);
//            return handler.toString();
//        } finally {
//            stream.close();
//        }
//    }

    private void addFieldsForJson(Document doc, JsonNode jsonNode) {
        addFieldsForJson(doc, jsonNode, "");
    }
    
    private void addFieldsForJson(Document doc, JsonNode jsonNode, String jsonPointer) {
        if (jsonNode.isArray()) {
            for (int i = 0; i < jsonNode.size(); i++) {
                JsonNode child = jsonNode.path(i);
                addFieldsForJson(doc, child, jsonPointer + "/_");
            }
        } else if (jsonNode.isObject()) {
            Iterator<String> iter = jsonNode.fieldNames();
            while (iter.hasNext()) {
                String fieldName = iter.next();
                JsonNode child = jsonNode.path(fieldName);
                addFieldsForJson(doc, child, jsonPointer + "/" + JsonUtil.encodeSegment(fieldName));
            }
        } else {
            String text = jsonNode.asText(null);
            if (text != null) {
                doc.add(new TextField(jsonPointer, text, Field.Store.YES));
                doc.add(new StringField(getSortFieldName(jsonPointer), text, Field.Store.NO));
                doc.add(new TextField("internal.all", text, Field.Store.NO));
            }
        }
    }

    private void addReferencesField(Document doc, StorageProxy obj, String objectType, String remoteRepository, JsonNode jsonNode) {
        if (relationshipsService == null) return;
        List<RelationshipsService.ObjectPointer> pointedAtIds;
        try {
            pointedAtIds = relationshipsService.pointedAtIds(obj.getObjectID(), objectType, remoteRepository, jsonNode);
        } catch (InvalidException e) {
            logger.warn("Exception indexing " + obj.getObjectID(), e);
            return;
        }
        for (RelationshipsService.ObjectPointer objectPointer : pointedAtIds) {
            String id = objectPointer.objectId;
            doc.add(new TextField("internal.pointsAt", id, Field.Store.YES));
        }
    }
}
