/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.filesystem;

import java.io.*;
import java.util.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;

public class FilesystemDigitalObject extends AbstractDigitalObject implements DigitalObject {
    public static final String ATTRIBUTES_FILE_NAME = "attributes";
    
    private final FilesystemRepository repo;
    private final File dir;
    private final String handle;
    
    private Map<String,String> attributes;    
    Map<String,Map<String,String>> elAttributes;
    
	public FilesystemDigitalObject(FilesystemRepository repo, File dir, String handle) throws RepositoryException {
	    this.repo = repo;
        this.dir = dir;
        this.handle = handle;
        loadAttributes();
    }
	
	@Override
	public Repository getRepository() {
	    return repo;
	}

	@Override
	public String getHandle() {
	    return handle;
	}

	@Override
	public void delete() {
	    for(File file : dir.listFiles()) {
	        file.delete();
	    }
	    dir.delete();
	}

	synchronized void loadAttributes() throws RepositoryException {
	    if(attributes!=null) return;
	    attributes = new HashMap<String,String>();
        elAttributes = new HashMap<String,Map<String,String>>();
        
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = factory.newSAXParser();
            File file = new File(dir,ATTRIBUTES_FILE_NAME);
            DefaultHandler handler = new DefaultHandler() {
            	
            	boolean isElement = false;
            	String elementName = "";
            	
            	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            		if(qName.equalsIgnoreCase("att")) {
            			if (isElement) {
            				elAttributes.get(elementName).put(atts.getValue("","name"),atts.getValue("","value"));
            			} else {
            				attributes.put(atts.getValue("","name"),atts.getValue("","value"));
            			}
            		}
            		if(qName.equalsIgnoreCase("el")) {
            			isElement = true;
            			elementName = atts.getValue("","id");
                        elAttributes.put(elementName, new HashMap<String,String>());
            		}
            	}
            	
            	public void characters(char[] ch, int start, int length) throws SAXException {
            		//do nothing
            	}
            	
            	public void endElement(String uri, String localName, String qName) throws SAXException {
            		if(qName.equalsIgnoreCase("el")) {
            			isElement = false;
            			elementName = "";
            		}
            	}
            };
            
            saxParser.parse(file, handler);    // specify handler
        }
        catch(ParserConfigurationException e) {
        	throw new InternalException(e);
        }
        catch(SAXException e) {
            throw new InternalException(e);
        }
        catch(IOException e) {
            throw new InternalException(e);
        }
	}
	
	public static void writeEmptyAttributesFile(File f, String handle) throws IOException {
	    
	    Map<String, String> initialAttributes = new HashMap<String, String>();
	    String now = String.valueOf(System.currentTimeMillis());
	    initialAttributes.put(Repositories.INTERNAL_CREATED, now);
	    initialAttributes.put(Repositories.INTERNAL_MODIFIED, now);
	    
		FileOutputStream fstream = new FileOutputStream(f);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fstream, "UTF-8"), 8192);
		bw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		bw.append("<do id=" + escQuote(handle) + ">\n");
		writeAttributesTo(bw, initialAttributes);
		bw.append("</do>\n");
		bw.flush();
		bw.close();		
	}

	//as all the attributes and element attributes are stored in a single XML file we need to write the entire file every time 
	//a set or delete operation is called on this object
	synchronized void writeAttributesFile() throws RepositoryException {
	    String now = String.valueOf(System.currentTimeMillis());
	    attributes.put(Repositories.INTERNAL_MODIFIED, now);
		try {
			File attributesFile = new File(dir,ATTRIBUTES_FILE_NAME);
			FileOutputStream fstream = new FileOutputStream(attributesFile);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fstream, "UTF-8"), 8192);
			bw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			bw.append("<do id=" + escQuote(getHandle()) + ">\n");
			writeAttributesTo(bw, attributes);
			for (String elKey : elAttributes.keySet()) {
				Map<String, String> element = elAttributes.get(elKey);
				bw.append("<el id="+ escQuote(elKey) +">\n");
				writeAttributesTo(bw, element);
				bw.append("</el>\n");
			}
			bw.append("</do>\n");
			bw.flush();
			bw.close();
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}
	
	private static void writeAttributesTo(BufferedWriter bw, Map<String, String> atts) throws IOException {
		for (String key : atts.keySet()) {
			bw.append(xmlAttLineFor(key, atts.get(key)));
		}		
	}
	
	private static String xmlAttLineFor(String name, String value) {
		return "<att name="+ escQuote(name) + " value=" + escQuote(value) +"/>\n";
	}
	
	private static String escQuote(String s) {
		return "\""+escape(s)+"\"";
	}
	
	public static String escape(CharSequence s) {
		StringBuilder buf = new StringBuilder();
		int len = s.length();
		for(int i = 0; i < len; i++) {
			char c = s.charAt(i);
			if(c=='&') buf.append("&amp;");
			else if(c=='<') buf.append("&lt;");
			else if(c=='>') buf.append("&gt;");
			else if(c=='"') buf.append("&quot;");
			else if(c=='\'') buf.append("&apos;");
			else if(c==0x9) buf.append("&#x9;");
			else if(c==0xD) buf.append("&#xD;");
			else if(c==0xA) buf.append("&#xA;"); // escape newlines in attributes
			else buf.append(c);
		}
		return buf.toString();
	}	
	
	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
	    loadAttributes();
	    return getCopyOfAttributes();
	}
	
	private Map<String, String> getCopyOfAttributes() {
		return new HashMap<String, String>(attributes);
	}

	@Override
	public CloseableIterator<Map.Entry<String, String>> listAttributes() throws RepositoryException {
	    loadAttributes();
	    return new CloseableIteratorFromIterator<Map.Entry<String,String>>(getCopyOfAttributes().entrySet().iterator());
	}

	@Override
	public String getAttribute(String name) throws RepositoryException {
        loadAttributes();
        return attributes.get(name);
	}

	@Override
	public void setAttributes(Map<String, String> atts) throws RepositoryException {
		for (String name : atts.keySet()) {
			String value = atts.get(name);
			if(value==null) this.attributes.remove(name);
			else this.attributes.put(name, value);
		}
		writeAttributesFile();
	}

	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
		if(value==null) this.attributes.remove(name);
		else this.attributes.put(name, value);
		writeAttributesFile();
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		for (String name : names) {
			attributes.remove(name);
		}
		writeAttributesFile();
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		attributes.remove(name);
		writeAttributesFile();
	}

	@Override
	public boolean verifyDataElement(String name) throws RepositoryException {
        loadAttributes();
        return elAttributes.containsKey(name);
//        return new File(dir,FilesystemRepository.encodeFilename(name)).exists();
	}

	@Override
	public DataElement createDataElement(String name) throws CreationException, RepositoryException {
        if(name==null) throw new NullPointerException();
        File file = new File(dir,FilesystemRepository.encodeFilename(name));
        if(file.isDirectory()) throw new InternalException(name + " is a directory");
        if(file.exists()) {
            if(verifyDataElement(name)) throw new CreationException();
            else throw new InternalException(name + " already exists");
        }
        try {
            file.createNewFile();
        }
        catch(IOException e) {
            throw new InternalException(e);
        }
        elAttributes.put(name, new HashMap<String,String>());
        writeAttributesFile();
        return new FilesystemDataElement(this,file,name);
	}

	@Override
	public DataElement getDataElement(String name) throws RepositoryException {
        if(name==null) throw new NullPointerException();
        Map<String,String> elAtts = elAttributes.get(name);
        if(elAtts==null) return null;
        File file = new File(dir,FilesystemRepository.encodeFilename(name));
        if(!file.exists() || file.isDirectory()) throw new InternalException("File for data element " + name + " is missing");
        return new FilesystemDataElement(this,file,name);
	}

	@Override
	public void deleteDataElement(String name) throws RepositoryException {
        if(name==null) throw new NullPointerException();
        File file = new File(dir,FilesystemRepository.encodeFilename(name));
        file.delete();
        elAttributes.remove(name);
        writeAttributesFile();
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
	    loadAttributes();
	    return new CloseableIteratorFromIterator<String>(elAttributes.keySet().iterator());
	}
}
