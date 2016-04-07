/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.DataElement;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.cnri.repository.util.RepositoryJsonSerializerV3.DOView.ElementView;
import net.handle.hdllib.Util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * 
 * Provides methods for serializing an arbitrary DigitalDbject to a JSON string and back again. 
 * You can either serialize a DigitalObject a list of DigitalObjects or an entire repository.
 * Also provides a method for directly loading a repositroy from a url that returns a JSON string.
 *
 * @deprecated Use {@link RepositoryJsonSerializer}.
 */
@Deprecated
public class RepositoryJsonSerializerV3 {
	
	/**
	 * Loads a Repository from a URL given that the URL returns a correctly formatted JSON string.
	 * @param repository The Repositroy you want to load the objects into.
	 * @param url The URL that will return the JSON
	 * @throws IOException
	 * @throws RepositoryException
	 * @throws CreationException
	 */
	public static void loadRepositoryFromURL(Repository repository, String url) throws IOException, RepositoryException, CreationException {
		String json = HttpClientUtil.getJSONFromURL(url);
		List<DOView> objects = RepositoryJsonSerializerV3.fromJSON(json);
		RepositoryJsonSerializerV3.loadDOViewsIntoRepository(objects, repository);
	}
	
	public static List<DOView> getDOViewsFromURL(String url) throws IOException {
		String json = HttpClientUtil.getJSONFromURL(url);
		return RepositoryJsonSerializerV3.fromJSON(json);		
	}
	
	public static String getJSONFromURL(String url) throws IOException {
        return HttpClientUtil.getJSONFromURL(url);
    }
	
	public static void loadDOViewsIntoRepository(List<DOView> views, Repository repository) throws RepositoryException, CreationException {
	    if(views==null) return;
		for (DOView view : views) {
			loadDOViewIntoRepository(view, repository);
		}
	}
	
	public static MemoryDigitalObject createDigitalObjectFromJson(String json) throws RepositoryException {
	    Gson gsonInstance = new Gson();
	    DOView doView = gsonInstance.fromJson(json, DOView.class);
	    MemoryDigitalObject dobj = new MemoryDigitalObject(null, doView.getId());
	    loadDOViewIntoDigitalObject(doView, dobj, true);
	    return dobj;
	}
	
	public static List<MemoryDigitalObject> createDigitalObjectsFromJson(String json) throws RepositoryException {
	    List<MemoryDigitalObject> result = new ArrayList<MemoryDigitalObject>();
	    List<DOView> doViews = fromJSON(json);
	    for (DOView doView : doViews) {
	        MemoryDigitalObject dobj = new MemoryDigitalObject(null, doView.getId());
	        loadDOViewIntoDigitalObject(doView, dobj, true);
	        result.add(dobj);
	    }
	    return result;
	}
	
	public static List<MemoryDigitalObject> createDigitalObjectsFromDOViews(List<DOView> doViews) throws RepositoryException {
	    List<MemoryDigitalObject> result = new ArrayList<MemoryDigitalObject>();
	    for (DOView doView : doViews) {
	        MemoryDigitalObject dobj = new MemoryDigitalObject(null, doView.getId());
	        loadDOViewIntoDigitalObject(doView, dobj, true);
	        result.add(dobj);
	    }
	    return result;
	}
	
	public static MemoryDigitalObject createDigitalObjectFromDOView(DOView doView) throws RepositoryException {
	    MemoryDigitalObject dobj = new MemoryDigitalObject(null, doView.getId());
	    loadDOViewIntoDigitalObject(doView, dobj, true);
	    return dobj;
	}
	
	public static boolean loadJsonIntoDigitalObject(String json, DigitalObject dobj) throws RepositoryException {
	    Gson gsonInstance = new Gson();
	    DOView doView = gsonInstance.fromJson(json, DOView.class);
	    return loadDOViewIntoDigitalObject(doView, dobj, false);
	}
	
	private static boolean loadDOViewIntoDigitalObject(DOView view, DigitalObject dobj, boolean isNew) throws RepositoryException {
        if (isNew) {
            dobj.setAttributes(view.getAttributes());
        }
        else {
            setAttributesExactly(dobj,view.getAttributes());
//            Collection<String> names = new HashSet<String>();
//            for (DOView.ElementView elementView : view.getElements().values()) {
//                names.add(elementView.getElementId());
//            }
//            deleteOtherDataElements(dobj,names);
            deleteOtherDataElements(dobj,view.getElements().keySet());
        }
        //for (DOView.ElementView elementView : view.getElements().values()) {
        for (String elementName : view.getElements().keySet()) {
        	DOView.ElementView elementView = view.getElements().get(elementName);
        
        	DataElement element = dobj.getDataElement(elementName); 
	        boolean created = element == null;
	        if (element == null) {
	            element = dobj.createDataElement(elementName);
	        }
	        Map<String,String> atts = elementView.getAttributes();
	        if(!created && elementView.isDataPresent()) {
	            String originalDataNotPresent = element.getAttribute(ElementView.DATA_NOT_PRESENT);
	            if(originalDataNotPresent==null) atts.remove(ElementView.DATA_NOT_PRESENT);
	            else atts.put(ElementView.DATA_NOT_PRESENT,originalDataNotPresent);
	        }
	        setAttributesExactly(element,atts);
	        if (elementView.isDataPresent()) {
	            //TODO if there is data in the incoming ElementView and in the element, should we overwrite the data in the element?
	            //should probably pass in an option to overwrite or not.
	            writeDataToElement(elementView.getData(), element);
	        }
	    }
        dobj.setAttribute(Repositories.INTERNAL_CREATED,view.getAttributes().get(Repositories.INTERNAL_CREATED));
        dobj.setAttribute(Repositories.INTERNAL_MODIFIED,view.getAttributes().get(Repositories.INTERNAL_MODIFIED));
	    return true;
	}
	
	
	public static boolean loadDOViewIntoRepository(DOView view, Repository repository) throws RepositoryException, CreationException {
		DigitalObject dobj = repository.getDigitalObject(view.getId());
		boolean isNew = dobj==null;
		if(isNew) dobj = repository.createDigitalObject(view.getId());
		return loadDOViewIntoDigitalObject(view, dobj, isNew);
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
        removeInternalSizeAttribute(attributesToSet);
        if(!attributesToDelete.isEmpty()) el.deleteAttributes(attributesToDelete);
        if(!attributesToSet.isEmpty()) el.setAttributes(attributesToSet);
	}
	
	private static void removeInternalSizeAttribute(Map<String, String> attributes) {
		if (attributes.get("internal.size") != null) {
			attributes.remove("internal.size");
		}
	}

	private static void writeDataToElement(byte[] data, DataElement element) throws RepositoryException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		try {
			element.write(in, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}	
	
	/**
	 * Given a Repository this method will return a JSON String that represents all the object in the Repository.
	 * @param repository
	 */
	public static String toJSON(Repository repository) {
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		List<DOView> all = getAllObjectViews(repository);
		return gsonInstance.toJson(all);
	}	
	
	/**
	 * Given a Repository this method will return a JSON String that represents all the object in the Repository.
	 * Only elements explicitly included in the in elementsToInclude List will be included in the JSON
	 * @param repository
	 * @param elementsToInclude A list of Strings that are the names of the elements to be included in the JSON
	 */	
	public static String toJSON(Repository repository, List<String> elementsToInclude) {
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		List<DOView> all = getAllObjectViews(repository, elementsToInclude);
		return gsonInstance.toJson(all);
	}	
	
	
	private static List<DOView> getAllObjectViews(Repository repo) {
		List<DOView> result = new ArrayList<DOView>();
		try {
		    CloseableIterator<DigitalObject> iter = repo.listObjects();
		    try {
		        for(DigitalObject dobj : CollectionUtil.forEach(iter)) {
		            DOView doView = new DOView(dobj);
		            result.add(doView);
		        }
		    } catch(UncheckedRepositoryException e) {
		        e.throwCause();
		    } finally {
		        iter.close();
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	private static List<DOView> getAllObjectViews(Repository repo, List<String> elementsToInclude) {
		List<DOView> result = new ArrayList<DOView>();
		try {
			Iterator<DigitalObject> iter = repo.listObjects();
			while (iter.hasNext()) {
				DigitalObject dobj = iter.next();
				DOView doView = new DOView(dobj, elementsToInclude);
				result.add(doView);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}	
	
	/**
	 * Given a single DigitalObject this method will return a JSON String that represents that object.
	 */
	public static String toJSON(DigitalObject dobj) {
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		DOView doView = new DOView(dobj);
		return gsonInstance.toJson(doView);
	}
	
	/**
	 * Given a Repository this method will return a JSON String that the object in the Repository.
	 * Only elements explicitly included in the in elementsToInclude List will be included in the JSON
	 * @param elementsToInclude A list of Strings that are the names of the elements to be included in the JSON
	 */		
	public static String toJSON(DigitalObject dobj, List<String> elementsToInclude) {
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		DOView doView = new DOView(dobj, elementsToInclude);
		return gsonInstance.toJson(doView);
	}	
	
	//TODO Streamed writing of DigitalObject to JSON is not yet finished
	public static void writeJSONStream(OutputStream out, List<DigitalObject> objects) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.setIndent("  ");
        writer.beginArray();
        Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
        for (DigitalObject dobj : objects) {
        	gsonInstance.toJson(new DOView(dobj), DOView.class, writer);
        }
        writer.endArray();
        writer.close();
	}
	
    public static List<DOView> readJsonStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        List<DOView> views = new ArrayList<DOView>();
        reader.beginArray();
        Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
        while (reader.hasNext()) {
            DOView view = gsonInstance.fromJson(reader, DOView.class);
            views.add(view);
        }
        reader.endArray();
        reader.close();
        return views;
    }

    public static List<DOView> toDOViews(List<DigitalObject> objects) {
        List<DOView> doViews = new ArrayList<DOView>();
        for (DigitalObject dobj : objects) {
            doViews.add(new DOView(dobj));
        }
        return doViews;
    }
    
    public static List<DOView> toDOViews(List<DigitalObject> objects, List<String> elementsToInclude) {
        if (elementsToInclude == null) {
            elementsToInclude = new ArrayList<String>();
        }
        List<DOView> doViews = new ArrayList<DOView>();
        for (DigitalObject dobj : objects) {
            doViews.add(new DOView(dobj, elementsToInclude));
        }
        return doViews;
    }
    
	/**
	 * Given a list of DigitalObjects this method will return a JSON String that represents those objects.
	 */
	public static String toJSON(List<DigitalObject> objects) {
		List<DOView> doViews = new ArrayList<DOView>();
		for (DigitalObject dobj : objects) {
			doViews.add(new DOView(dobj));
		}
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		return gsonInstance.toJson(doViews);
	}
	
	/**
	 * Given a list of DigitalObjects this method will return a JSON String that represents those objects.
	 * Only elements explicitly included in the in elementsToInclude List will be included in the JSON
	 * @param elementsToInclude A list of Strings that are the names of the elements to be included in the JSON
	 */	
	public static String toJSON(List<DigitalObject> objects, List<String> elementsToInclude) {
		if (elementsToInclude == null) {
			elementsToInclude = new ArrayList<String>();
		}
		List<DOView> doViews = new ArrayList<DOView>();
		for (DigitalObject dobj : objects) {
			doViews.add(new DOView(dobj, elementsToInclude));
		}
		Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
		return gsonInstance.toJson(doViews);
	}	
	
	public static List<DOView> fromJSON(String json) {
		Gson gsonInstance = new Gson();
		Type collectionType = new TypeToken<Collection<DOView>>(){}.getType();
		return gsonInstance.fromJson(json, collectionType);
	}
	
	/**
	 * The DOView object loads all attributes and elements of a digital object into into a java object. 
	 * This java object can then be parsed by gson into a json string.
	 */
	public static class DOView {
		private String id;
		private Map<String, String> attributes = new HashMap<String, String>();
		private Map<String, ElementView> elements = new HashMap<String, ElementView>();
		
		public DOView(DigitalObject dobj) { //if constructed without the includes list all data elements are included
			id = dobj.getHandle();
			extractAttributesFrom(dobj);
			try {
				extractElementsFrom(dobj);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public DOView(DigitalObject dobj, List<String> elementsToInclude) { //TODO if includes is null no data elements are included
			id = dobj.getHandle();
			extractAttributesFrom(dobj);
			try {
				extractElementsFrom(dobj, elementsToInclude);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}	
		
		public String getId() { return id; }
		
		public Map<String, String> getAttributes() { return attributes; }
		
		public Map<String, ElementView> getElements() { return elements; }	
		
		private void extractAttributesFrom(DigitalObject dobj) {
			try {
				Map<String, String> atts = dobj.getAttributes();
				for (String key : atts.keySet()) {
					attributes.put(key, atts.get(key));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}		
		}		
		
		private void extractElementsFrom(DigitalObject dobj) throws IOException, RepositoryException {
			CloseableIterator<DataElement> iter = dobj.listDataElements();
			try {
				while (iter.hasNext()) {
					DataElement element = iter.next();
					ElementView ev = new ElementView(element, true);
					elements.put(element.getName(), ev);			
				}
			} catch (UncheckedRepositoryException e) {
				e.throwCause();
			}
			finally {
				iter.close();
			}
		}
		
		private void extractElementsFrom(DigitalObject dobj, List<String> elementsToInclude) throws IOException, RepositoryException {
			CloseableIterator<DataElement> iter = dobj.listDataElements();
			try {	
				while (iter.hasNext()) {
					DataElement element = iter.next();
					ElementView ev = null;
					if (elementsToInclude.contains(element.getName())) {
						ev = new ElementView(element, true); 
					} else {
						ev = new ElementView(element, false); //if you ain't on the list you ain't comin' in.
					}
					elements.put(element.getName(), ev);
				}
			} catch (UncheckedRepositoryException e) {
				e.throwCause();
			}
			finally {
				iter.close();
			}
		}	
		
		public static class ElementView {
			public static final String DATA_NOT_PRESENT = "internal.data_not_present";
//			private String elementID;
			//private byte[] data;
            private Map<String, String> attributes = new HashMap<String, String>();
			private String dataAsString; // only for deserializing from V2
			private String dataAsBase64;
			
			public ElementView(DataElement element, boolean includeData) throws RepositoryException {
//				elementID = element.getName();
				extractAttributesFrom(element);
				try {
					extractDataFrom(element, includeData);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
//			public String getElementId() { return elementID; }
			
			public byte[] getData() {
			    if (dataAsBase64 != null) {
			        return Base64.decodeBase64(dataAsBase64);
			    } else if (dataAsString != null) {
			        return Util.encodeString(dataAsString);
			    } else {
			        return new byte[0];
			    }
			}
			
			@Deprecated
			public byte[] getDataStringAsBytes() {
			    return getData();
			}
			
			public String getDataString() {
			    if (dataAsString != null) return dataAsString;
			    return Util.decodeString(getData()); 
			}
			
			public Map<String, String> getAttributes() { return attributes; }
			
			private void extractAttributesFrom(DataElement element) {
				try {
					Map<String, String> atts = element.getAttributes();
					for (String key : atts.keySet()) {
						attributes.put(key, atts.get(key));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}			

			private void extractDataFrom(DataElement element, boolean includeData) throws IOException, RepositoryException {
				if ("1".equals(element.getAttribute(DATA_NOT_PRESENT))) { // this element does not contain the data
					return;
				} 
				if (includeData) {
					InputStream in = element.read();
					//data = bytesFromInputStream(in);
					//in = element.read();
					try {
					    dataAsBase64 = Base64.encodeBase64String(bytesFromInputStream(in));
					} finally {
					    in.close();
					}
					attributes.remove(DATA_NOT_PRESENT);
				} else {
					attributes.put(DATA_NOT_PRESENT, "1");
				}
			}
						
			private static byte[] bytesFromInputStream(InputStream in) throws IOException {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				int nRead;
				byte[] data = new byte[4096];
				while ((nRead = in.read(data)) > 0) {
				  buffer.write(data, 0, nRead);
				}
				return buffer.toByteArray();
			}
			
			public boolean isDataPresent() {
				if ("1".equals(attributes.get(DATA_NOT_PRESENT))) { // this element does not contain the data
					return false;
				} else {
					return true;
				}
			}
			
		}
	}
}
