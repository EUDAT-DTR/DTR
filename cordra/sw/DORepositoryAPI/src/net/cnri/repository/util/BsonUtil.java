/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.cnri.repository.memory.MemoryRepository;

import org.bson.BSON;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DBDecoder;
import com.mongodb.DefaultDBDecoder;

public class BsonUtil {

	public static byte[] toBson(DigitalObject dobj) throws RepositoryException {
		DBObject dbObject = digitalObjectToDBObject(dobj);
		byte[] result = BSON.encode(dbObject);
		return result;
	}
	
	public static DBObject digitalObjectToDBObject(DigitalObject dobj) throws RepositoryException {
		BasicDBObject result = new BasicDBObject();
		result.put("id", dobj.getHandle());
		BasicDBObject attributesBSON = new BasicDBObject(dobj.getAttributes());
		result.put("attributes", attributesBSON);
		List<DataElement> dataElements = dobj.getDataElements();
		Map<String, BasicDBObject> elementsMapBSON = new HashMap<String, BasicDBObject>();
		for (DataElement element : dataElements) {
			BasicDBObject elementBSON = elementToDBObject(element);
			elementsMapBSON.put(element.getName(), elementBSON);
		}
		BasicDBObject elementsBSON = new BasicDBObject(elementsMapBSON);
		result.put("elements", elementsBSON);
		return result;
	}
	
	public static BasicDBObject elementToDBObject(DataElement element) throws RepositoryException{
		BasicDBObject elementBSON = new BasicDBObject();
		elementBSON.put("attributes", element.getAttributes());
		byte[] bytes = toByteArray(element.read());
		elementBSON.put("data", bytes);		
		return elementBSON;
	}
	
	public static byte[] toByteArray(InputStream is) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int nRead;
		byte[] data = new byte[16384];
		try {
			while ((nRead = is.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				buffer.flush();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return buffer.toByteArray();
	}
	
	public static DigitalObject fromBson(byte[] bsonBytes) throws RepositoryException {
		DBDecoder decoder = DefaultDBDecoder.FACTORY.create();
		DBCollection nullDBCollection = null;
		DBObject dbObject = decoder.decode(bsonBytes, nullDBCollection);
		DigitalObject result = dbObjectToDigitalObject(dbObject);
		return result;
	}
	
	public static DigitalObject dbObjectToDigitalObject(DBObject objectBSON) throws RepositoryException {
		String handle = (String) objectBSON.get("id");
		MemoryRepository mRepo = new MemoryRepository();
		DigitalObject result = new MemoryDigitalObject(mRepo, handle);
		@SuppressWarnings("unchecked")
		Map<String, String> attributes = (Map<String, String>) objectBSON.get("attributes");
		result.setAttributes(attributes);
        @SuppressWarnings("unchecked")
		Map<String, BasicDBObject> elementsMapBSON = (Map<String, BasicDBObject>) objectBSON.get("elements");
		for (String elementName : elementsMapBSON.keySet()) {
			BasicDBObject elementBSON = elementsMapBSON.get(elementName);
			DataElement dataElement = result.createDataElement(elementName);
	        @SuppressWarnings("unchecked")
			Map<String, String> elementAttributes = (Map<String, String>) elementBSON.get("attributes");
			dataElement.setAttributes(elementAttributes);
			byte[] bytes = (byte[]) elementBSON.get("data");
			try {
				dataElement.write(new ByteArrayInputStream(bytes), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public static void writeObjectToFile(DigitalObject dobj, File f) throws FileNotFoundException, RepositoryException {
		byte[] objectAsBson = toBson(dobj);
		FileOutputStream out = new FileOutputStream(f);
		try {
			out.write(objectAsBson);
		} catch (IOException e) {
			try {
				out.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}
	
	public static DigitalObject readObjectFromFile(File file) throws IOException, RepositoryException {
		byte [] objectAsBsonBytes = new byte[(int)file.length()];
		DataInputStream dis = new DataInputStream(new FileInputStream(file));
		dis.readFully(objectAsBsonBytes);
		dis.close();
		return fromBson(objectAsBsonBytes);
	}
}
