/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

public class HttpCordraObjectSearchResults implements SearchResults<CordraObject> {
    private final Gson gson = new Gson();
    private final CloseableHttpResponse response;
    private final HttpEntity entity;
    private final JsonReader jsonReader;
    private final int size;
    
    public HttpCordraObjectSearchResults(CloseableHttpResponse response) throws CordraException {
        try {
            this.response = response;
            this.entity = response.getEntity();
            this.jsonReader = new JsonReader(new InputStreamReader(this.entity.getContent(), "UTF-8"));
            jsonReader.beginObject();
            int size = -1;
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if ("size".equals(name)) {
                    size = jsonReader.nextInt();
                } else if ("results".equals(name)) {
                    jsonReader.beginArray();
                    break;
                } else {
                    jsonReader.nextString();
                }
            }
            this.size = size;
        } catch (IOException e) {
            throw new CordraException(e);
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Iterator<CordraObject> iterator() {
        return new JsonReaderIterator();
    }
    
    @Override
    public void close() {
        if (jsonReader != null) try { jsonReader.close(); } catch (IOException e) { }
        if (entity != null) EntityUtils.consumeQuietly(entity);
        if (response != null) try { response.close(); } catch (IOException e) { }
    }
    
    // TODO close when iterator is done

    private class JsonReaderIterator implements Iterator<CordraObject> {
        @Override
        public boolean hasNext() {
            try {
                return jsonReader.hasNext();
            } catch (IOException e) {
                throw new UncheckedCordraException(new CordraException(e));
            }
        }
        
        @Override
        public CordraObject next() {
            try {
                CordraObject d = gson.fromJson(jsonReader, CordraObject.class);
                return d;
            } catch (Exception e) {
                throw new UncheckedCordraException(new CordraException(e));
            }
        }
    }
}
