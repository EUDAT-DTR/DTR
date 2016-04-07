/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonPrimitive;

import net.cnri.apps.doserver.Main;
import net.cnri.apps.doserver.operators.DefaultIndexBuilder;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StorageProxy;

public class JsonIndexBuilder extends DefaultIndexBuilder {
    private static Logger logger = LoggerFactory.getLogger(JsonIndexBuilder.class);
    
    @Override
    public void init(Main serverMain) {
        super.init(serverMain);
        addAnalyzer("type", new KeywordAnalyzer());
    }
    
    public QueryParser getQueryParser() {
        return new QueryParser(Version.LUCENE_47, "internal.all", getAnalyzer());
    }
    
    @Override
    public Document documentOfStorageProxy(StorageProxy obj) throws DOException, IOException {
        Document doc = super.documentOfStorageProxy(obj);
        HeaderSet attributes = obj.getAttributes(null);
        String type = attributes.getStringHeader("type", null);
        if (type != null) {
            doc.add(new TextField("type", type, Field.Store.YES));
        }
        String json = attributes.getStringHeader("json", null);
        if (json != null) {
            try {
                JsonElement jsonElement = new JsonParser().parse(json);
                addFieldsForJson(doc, jsonElement);
            } catch (JsonSyntaxException e) {
                logger.warn("Exception indexing " + obj.getObjectID(), e);
            }
        }
        if (type != null && json != null) {
            doc.add(new TextField("valid", "true", Field.Store.YES));
        }
        return doc;
    }
    
    private void addFieldsForJson(Document doc, JsonElement jsonElement) {
        addFieldsForJson(doc, jsonElement, "");
    }
    
    private void addFieldsForJson(Document doc, JsonElement jsonElement, String jsonPointer) {
        if (jsonElement.isJsonArray()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement child = jsonArray.get(i);
                addFieldsForJson(doc, child, jsonPointer + "/_");
            }
        } else if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> property : jsonObject.entrySet()) {
                String fieldName = property.getKey();
                JsonElement child = property.getValue();
                addFieldsForJson(doc, child, jsonPointer + "/" + encodeSegment(fieldName));
            }
        } else {
            JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
            String text = jsonPrimitive.getAsString();
            if (text != null) {
                doc.add(new TextField(jsonPointer, text, Field.Store.YES));
                doc.add(new TextField("internal.all", text, Field.Store.NO));
            }
        }
    }    
    
    private String encodeSegment(String s) {
        return s.replaceAll("~", "~0").replaceAll("/", "~1");
    }
}
