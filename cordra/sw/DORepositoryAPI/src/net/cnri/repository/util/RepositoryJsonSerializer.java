/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.codec.binary.Base64;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.DataElement;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.handle.hdllib.Util;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;

/**
 * Utility class for working with JSON representations of digital objects.
 * This class contains static methods for serializing and deserializing 
 * between digital objects and JSON.  Methods can work with JSON as {@code String} 
 * instances, or streaming with instances of Gson {@code JsonWriter} and 
 * {@code JsonReader}.  Serialization can be configured to only include data 
 * from select elements. 
 */
public class RepositoryJsonSerializer {
    /**
     * {@code internal.data_not_present}, the name of an attribute set on
     * data elements where the data is not serializaed.
     */
    public static final String DATA_NOT_PRESENT = "internal.data_not_present";

    private static class ExecServHolder {
        static ExecutorService execServ = Executors.newCachedThreadPool(new ThreadFactory() {
            ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setDaemon(true);
                return t;
            }
        });
    }
    
    /**
     * Creates or modifies a digital object in the given repository according to the JSON
     * representation.
     * 
     * @param jsonReader  the source of the JSON representation
     * @param repo  the repository in which to create or modify the digital object
     * @return the digital object created or modified
     */
    public static DigitalObject loadJsonForOneObjectIntoRepository(JsonReader jsonReader, Repository repo) throws RepositoryException, IOException {
        jsonReader.beginObject();
        if (!jsonReader.hasNext() || !"id".equals(jsonReader.nextName())) throw new MalformedJsonException("Expected id");
        String handle = jsonReader.nextString();
        DigitalObject dobj = repo.getOrCreateDigitalObject(handle);
        loadRemainingJsonObjectIntoDigitalObject(dobj, jsonReader);
        return dobj;
    }

    /**
     * Creates or modifies a digital object in the given repository according to the JSON
     * representation.
     * 
     * @param json  the source of the JSON representation
     * @param repo  the repository in which to create or modify the digital object
     * @return the digital object created or modified
     */
    public static DigitalObject loadJsonForOneObjectIntoRepository(String json, Repository repo) throws RepositoryException {
        try {
            return loadJsonForOneObjectIntoRepository(new JsonReader(new StringReader(json)), repo);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Creates or modifies multiple digital objects in the given repository according to the JSON
     * representation of an array of digital objects
     * 
     * @param jsonReader  the source of the JSON representation of the array
     * @param repo  the repository in which to create or modify the digital objects
     */
    public static void loadJsonForObjectsArrayIntoRepository(JsonReader jsonReader, Repository repo) throws RepositoryException, IOException {
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
            loadJsonForOneObjectIntoRepository(jsonReader, repo);
        }
        jsonReader.endArray();
    }

    /**
     * Creates or modifies multiple digital objects in the given repository according to the JSON
     * representation of an array of digital objects
     * 
     * @param json  the source of the JSON representation of the array
     * @param repo  the repository in which to create or modify the digital objects
     */
    public static void loadJsonForObjectsArrayIntoRepository(String json, Repository repo) throws RepositoryException {
        try {
            loadJsonForObjectsArrayIntoRepository(new JsonReader(new StringReader(json)), repo);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Modifies a digital object according the the JSON representation.
     * The handle in the JSON is ignored.
     * 
     * @param jsonReader  the source of the JSON representation
     * @param dobj  the digital object to modify
     */
    public static void loadJsonIntoDigitalObject(JsonReader jsonReader, DigitalObject dobj) throws RepositoryException, IOException {
        jsonReader.beginObject();
        loadRemainingJsonObjectIntoDigitalObject(dobj, jsonReader);
    }

    /**
     * Modifies a digital object according the the JSON representation.
     * The handle in the JSON is ignored.
     * 
     * @param json  the source of the JSON representation
     * @param dobj  the digital object to modify
     */
    public static void loadJsonIntoDigitalObject(String json, DigitalObject dobj) throws RepositoryException {
        try {
            loadJsonIntoDigitalObject(new JsonReader(new StringReader(json)), dobj);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }
    
    /**
     * Creates a {@link MemoryDigitalObject} according to the given JSON representation. 
     * 
     * @param jsonReader  the source of the JSON representation
     * @return a {@code MemoryDigitalObject} matching the JSON
     */
    public static MemoryDigitalObject createDigitalObjectFromJson(JsonReader jsonReader) throws RepositoryException, IOException {
        jsonReader.beginObject();
        if (!jsonReader.hasNext() || !"id".equals(jsonReader.nextName())) throw new MalformedJsonException("Expected id");
        String handle = jsonReader.nextString();
        MemoryDigitalObject dobj = new MemoryDigitalObject(null, handle);
        loadRemainingJsonObjectIntoDigitalObject(dobj, jsonReader);
        return dobj;
    }

    /**
     * Creates a {@link MemoryDigitalObject} according to the given JSON representation. 
     * 
     * @param json  the source of the JSON representation
     * @return a {@code MemoryDigitalObject} matching the JSON
     */
    public static MemoryDigitalObject createDigitalObjectFromJson(String json) throws RepositoryException {
        try {
            return createDigitalObjectFromJson(new JsonReader(new StringReader(json)));
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Creates a list of {@link MemoryDigitalObject}s according to the given JSON representation of an array of objects. 
     * 
     * @param jsonReader  the source of the JSON representation of an array
     * @return a list of {@code MemoryDigitalObject}s matching the JSON
     */
    public static List<MemoryDigitalObject> createDigitalObjectsFromJson(JsonReader jsonReader) throws RepositoryException, IOException {
        List<MemoryDigitalObject> res = new ArrayList<MemoryDigitalObject>();
        jsonReader.beginArray();
        while (jsonReader.hasNext()) {
            res.add(createDigitalObjectFromJson(jsonReader));
        }
        jsonReader.endArray();
        return res;
    }

    /**
     * Creates a list of {@link MemoryDigitalObject}s according to the given JSON representation of an array of objects. 
     * 
     * @param json  the source of the JSON representation of an array
     * @return a list of {@code MemoryDigitalObject}s matching the JSON
     */
    public static List<MemoryDigitalObject> createDigitalObjectsFromJson(String json) throws RepositoryException, IOException {
        try {
            return createDigitalObjectsFromJson(new JsonReader(new StringReader(json)));
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }
    
    private static void loadRemainingJsonObjectIntoDigitalObject(DigitalObject dobj, JsonReader jsonReader) throws RepositoryException, IOException {
        Map<String, String> atts = null;
        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            if ("attributes".equals(name)) {
                atts = readAttributes(jsonReader);
            } else if ("elements".equals(name)) {
                loadElementsIntoDigitalObject(dobj, jsonReader);
            }
        }
        jsonReader.endObject();
        if (atts == null) atts = new HashMap<String, String>();
        setAttributesExactly(dobj, atts);
    }
    
    private static Map<String, String> readAttributes(JsonReader jsonReader) throws IOException {
        jsonReader.beginObject();
        Map<String, String> attributes = new HashMap<String, String>();
        while (jsonReader.hasNext()) {
            attributes.put(jsonReader.nextName(), jsonReader.nextString());
        }
        jsonReader.endObject();
        return attributes;
    }

    private static void loadElementsIntoDigitalObject(DigitalObject dobj, JsonReader jsonReader) throws RepositoryException, IOException {
        jsonReader.beginObject();
        List<String> elNames = new ArrayList<String>();
        while (jsonReader.hasNext()) {
            String elName = jsonReader.nextName();
            elNames.add(elName);
            final DataElement el = dobj.getOrCreateDataElement(elName);
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                if ("dataAsString".equals(name)) {
                    el.write(new ByteArrayInputStream(Util.encodeString(jsonReader.nextString())));
                } else if ("dataAsBase64".equals(name)) {
                    el.write(new ByteArrayInputStream(Base64.decodeBase64(jsonReader.nextString())));
                } else if ("dataArr".equals(name)) {
                    writeDataArrIntoDataElement(el, jsonReader);
                } else if ("attributes".equals(name)) {
                    setAttributesExactly(el, readAttributes(jsonReader));
                }
            }
            jsonReader.endObject();
        }
        jsonReader.endObject();
        deleteOtherDataElements(dobj, elNames);
    }

    private static void writeDataArrIntoDataElement(final DataElement el, JsonReader jsonReader) throws IOException, RepositoryException {
        final PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream(in);
        Future<Void> future;
        try {
            future = ExecServHolder.execServ.submit(new Callable<Void>() {
                public Void call() throws RepositoryException, IOException {
                    el.write(in);
                    in.close();
                    return null;
                }
            });
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                out.write(Base64.decodeBase64(jsonReader.nextString()));
            }
            jsonReader.endArray();
        } finally {
            out.close();
        }
        getFutureThrowExceptions(future);
    }

    private static void getFutureThrowExceptions(Future<Void> future) throws RepositoryException, IOException {
        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RepositoryException) {
                throw (RepositoryException)t;
            } else if (t instanceof IOException) {
                throw (IOException)t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            } else if (t instanceof Error) {
                throw (Error)t;
            } else {
                throw new AssertionError(t);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

	private static void deleteOtherDataElements(DigitalObject dobj, Collection<String> names) throws RepositoryException {
	    CloseableIterator<String> iter = dobj.listDataElementNames();
	    try {
	        while(iter.hasNext()) {
	            String name = iter.next();
	            if(!names.contains(name)) dobj.deleteDataElement(name);
	        }
	    }
        catch(UncheckedRepositoryException e) { e.throwCause(); }
        finally { iter.close(); }
	}
	
	private static void setAttributesExactly(DigitalObject dobj,Map<String,String> attributes) throws RepositoryException {
	    CloseableIterator<Map.Entry<String,String>> iter = dobj.listAttributes();
	    List<String> attributesToDelete = new ArrayList<String>();
	    Map<String,String> attributesToSet = new HashMap<String,String>(attributes);
	    try {
	        while(iter.hasNext()) {
	            Map.Entry<String,String> entry = iter.next();
	            String key = entry.getKey();
	            if(!attributesToSet.containsKey(key)) attributesToDelete.add(key);
	            else {
	                String value = entry.getValue();
	                if(attributesToSet.get(key).equals(value)) attributesToSet.remove(key); 
	            }
	        }
	    }
	    catch(UncheckedRepositoryException e) { e.throwCause(); }
	    finally { iter.close(); }
	    if(!attributesToDelete.isEmpty()) dobj.deleteAttributes(attributesToDelete);
	    if(!attributesToSet.isEmpty()) dobj.setAttributes(attributesToSet);
	}

	private static void setAttributesExactly(DataElement el,Map<String,String> attributes) throws RepositoryException {
        CloseableIterator<Map.Entry<String,String>> iter = el.listAttributes();
        List<String> attributesToDelete = new ArrayList<String>();
        Map<String,String> attributesToSet = new HashMap<String,String>(attributes);
        try {
            while(iter.hasNext()) {
                Map.Entry<String,String> entry = iter.next();
                String key = entry.getKey();
                if(!attributesToSet.containsKey(key)) attributesToDelete.add(key);
                else {
                    String value = entry.getValue();
                    if(attributesToSet.get(key).equals(value)) attributesToSet.remove(key); 
                }
            }
        }
        catch(UncheckedRepositoryException e) { e.throwCause(); }
        finally { iter.close(); }
        attributes.remove("internal.size");
        if(!attributesToDelete.isEmpty()) el.deleteAttributes(attributesToDelete);
        if(!attributesToSet.isEmpty()) el.setAttributes(attributesToSet);
	}
	
	/**
	 * Writes a JSON representation of the given digital object.  If {@code elementsToInclude}
	 * is not null, then only the named elements will include data; other elements will
	 * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
	 * 
	 * @param jsonWriter  the {@code JsonWriter} for the JSON output
	 * @param dobj  the object to serialize
	 * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
	 */
	public static void writeJson(JsonWriter jsonWriter, DigitalObject dobj, Collection<String> elementsToInclude) throws RepositoryException, IOException {
	    jsonWriter.beginObject();
	    jsonWriter.name("id");
	    jsonWriter.value(dobj.getHandle());
	    writeAttributes(jsonWriter, dobj.getAttributes());
	    jsonWriter.name("elements");
	    jsonWriter.beginObject();
	    CloseableIterator<DataElement> iter = dobj.listDataElements();
	    try {
	        while (iter.hasNext()) {
	            DataElement el = iter.next();
	            boolean includeData = elementsToInclude == null || elementsToInclude.contains(el.getName());
	            jsonWriter.name(el.getName());
	            jsonWriter.beginObject();
	            Map<String, String> atts = el.getAttributes();
	            if (!includeData) {
	                atts = new HashMap<String, String>(atts);
	                atts.put(DATA_NOT_PRESENT, "1");
	            }
	            writeAttributes(jsonWriter, atts);
	            if (includeData) {
	                writeData(jsonWriter, el);
	            }
	            jsonWriter.endObject();
	        }
	    } catch (UncheckedRepositoryException e) {
	        e.throwCause();
	    } finally {
	        iter.close();
	    }
	    jsonWriter.endObject();
	    jsonWriter.endObject();
	}

    /**
     * Writes a JSON representation of the given digital object.  All data
     * elements will include data.
     * 
     * @param jsonWriter  the {@code JsonWriter} for the JSON output
     * @param dobj  the object to serialize
     */
	public static void writeJson(JsonWriter jsonWriter, DigitalObject dobj) throws RepositoryException, IOException {
	    writeJson(jsonWriter, dobj, null);
	}
	
    /**
     * Writes a JSON representation of the given digital object.  If {@code elementsToInclude}
     * is not null, then only the named elements will include data; other elements will
     * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
     * 
     * @param dobj  the object to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     * @return the JSON output as a {@code String}
     */
	public static String toJson(DigitalObject dobj, Collection<String> elementsToInclude) throws RepositoryException {
	    StringWriter sw = new StringWriter();
	    JsonWriter jsonWriter = new JsonWriter(sw);
	    try {
	        writeJson(jsonWriter, dobj, elementsToInclude);
	    } catch (IOException e) {
	        throw new InternalException(e);
	    } finally {
	        try { jsonWriter.close(); } catch (IOException e) { }
	    }
	    return sw.toString();
	}

    /**
     * Writes a JSON representation of the given digital object.  All data elements
     * will include data.
     * 
     * @param dobj  the object to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     */
	public static String toJson(DigitalObject dobj) throws RepositoryException {
	    return toJson(dobj, null);
	}
	
    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given collection.  If {@code elementsToInclude}
     * is not null, then only the named elements will include data; other elements will
     * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
     * 
     * @param jsonWriter  the {@code JsonWriter} for the JSON output (a JSON array)
     * @param dobjs  the objects to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     */
	public static void writeJson(JsonWriter jsonWriter, Collection<DigitalObject> dobjs, Collection<String> elementsToInclude) throws RepositoryException, IOException {
	    jsonWriter.beginArray();
	    for (DigitalObject dobj : dobjs) {
	        writeJson(jsonWriter, dobj, elementsToInclude);
	    }
	    jsonWriter.endArray();
	}

    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given collection.  All data elements
     * will include data.
     * 
     * @param jsonWriter  the {@code JsonWriter} for the JSON output (a JSON array)
     * @param dobjs  the objects to serialize
     */
	public static void writeJson(JsonWriter jsonWriter, Collection<DigitalObject> dobjs) throws RepositoryException, IOException {
	    writeJson(jsonWriter, dobjs, null);
	}
	    
    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given collection.  If {@code elementsToInclude}
     * is not null, then only the named elements will include data; other elements will
     * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
     * 
     * @param dobjs  the objects to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     * @return the JSON output (a JSON array) as a {@code String}
     */
	public static String toJson(Collection<DigitalObject> dobjs, Collection<String> elementsToInclude) throws RepositoryException {
	    StringWriter sw = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(sw);
        try {
	        writeJson(jsonWriter, dobjs, elementsToInclude);
	    } catch (IOException e) {
	        throw new InternalException(e);
        } finally {
            try { jsonWriter.close(); } catch (IOException e) { }
	    }
	    return sw.toString();
	}

    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given collection.  All data elements will
     * include data.
     * 
     * @param dobjs  the objects to serialize
     * @return the JSON output (a JSON array) as a {@code String}
     */
	public static String toJson(Collection<DigitalObject> dobjs) throws RepositoryException {
	    return toJson(dobjs, null);
	}

    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given repository.  If {@code elementsToInclude}
     * is not null, then only the named elements will include data; other elements will
     * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
     * 
     * @param jsonWriter  the {@code JsonWriter} for the JSON output (a JSON array)
     * @param repo  the repository to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     */
	public static void writeJson(JsonWriter jsonWriter, Repository repo, Collection<String> elementsToInclude) throws RepositoryException, IOException {
	    jsonWriter.beginArray();
	    CloseableIterator<DigitalObject> iter = repo.listObjects();
	    try {
	        for (DigitalObject dobj : CollectionUtil.forEach(iter)) {
	            writeJson(jsonWriter, dobj, elementsToInclude);
	        }
	    } catch (UncheckedRepositoryException e) {
	        e.throwCause();
	    } finally {
	        iter.close();
	    }
	    jsonWriter.endArray();
	}

    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given repository.  All data elements
     * will include data.
     * 
     * @param jsonWriter  the {@code JsonWriter} for the JSON output (a JSON array)
     * @param repo  the repository to serialize
     */
	public static void writeJson(JsonWriter jsonWriter, Repository repo) throws RepositoryException, IOException {
	    writeJson(jsonWriter, repo, null);
	}
	      
    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given repository.  If {@code elementsToInclude}
     * is not null, then only the named elements will include data; other elements will
     * have attribute {@code internal.data_not_present} set to 1 in the JSON output.
     * 
     * @param repo  the repository to serialize
     * @param elementsToInclude  a collection of data element names for which data should be included; if null, all data elements will include data.
     * @return the JSON output (a JSON array) as a {@code String}
     */
	public static String toJson(Repository repo, Collection<String> elementsToInclude) throws RepositoryException {
	    StringWriter sw = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(sw);
	    try {
	        writeJson(jsonWriter, repo, elementsToInclude);
	    } catch (IOException e) {
	        throw new InternalException(e);
        } finally {
            try { jsonWriter.close(); } catch (IOException e) { }
	    }
	    return sw.toString();
	}

    /**
     * Writes a JSON representation which is an array of the representations 
     * of the digital objects in the given repository.  All data elements will
     * include data.
     * 
     * @param repo  the repository to serialize
     * @return the JSON output (a JSON array) as a {@code String}
     */
	public static String toJson(Repository repo) throws RepositoryException {
	    return toJson(repo, null);
	}

    private static void writeAttributes(JsonWriter jsonWriter, Map<String, String> atts) throws IOException {
        jsonWriter.name("attributes");
        jsonWriter.beginObject();
        for (Map.Entry<String, String> entry : atts.entrySet()) {
            jsonWriter.name(entry.getKey());
            jsonWriter.value(entry.getValue());
        }
        jsonWriter.endObject();
    }
    
    private static void writeData(JsonWriter jsonWriter, DataElement el) throws IOException, RepositoryException {
        jsonWriter.name("dataArr");
        jsonWriter.beginArray();
        InputStream in = el.read();
        try {
            byte[] buf = new byte[4095];
            int r;
            while ((r = in.read(buf)) > 0) {
                byte[] smallBuf;
                if (r == 4095) smallBuf = buf;
                else smallBuf = Arrays.copyOfRange(buf, 0, r);
                String val = Base64.encodeBase64String(smallBuf);
                jsonWriter.value(val);
            }
        } finally {
            in.close();
        }
        jsonWriter.endArray();
    }
}
