/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.cnri.repository.networked.NetworkedRepository;
import net.cnri.util.StringUtils;

/**
 * Factory and utility methods for the Repository class.
 */
public class Repositories {
    /** @deprecated Prefer {@link Constants#ELEMENT_ATTRIBUTE_DATA_NOT_PRESENT} */
    @Deprecated
    public static final String SYNC_UNAVAILABLE = "internal.data_not_present";
    
    public static final String INTERNAL_CREATED = "internal.created";
    public static final String INTERNAL_MODIFIED = "internal.modified";

    private Repositories() {}

    public static Repository getNetworkedRepository(String repoHandle, String host, int port, byte[] pubKeyBytes, String userHandle, byte[] userPassword) throws RepositoryException {
        return new NetworkedRepository(repoHandle, host, port, pubKeyBytes, userHandle, userPassword);
    }

    /**
     * Copies the digital object and the specified data elements.  Network intensive.
     * @param dobj Digital object to be copied.
     * @param repo Destination Repository.
     * @param elementsToInclude list of the element names to be included in the copy.
     * @param copyAll If set to true all elements will be copied.
     * @return A reference to the DigitalObject in the destination repository.
     */
    public static DigitalObject copy(DigitalObject dobj,Repository repo,List<String> elementsToInclude, boolean copyAll) throws IOException, RepositoryException, CreationException {
        DigitalObject copydobj = null;
        try {
            if(elementsToInclude==null) elementsToInclude = Collections.emptyList();
            Map<String, String> attributes = dobj.getAttributes();
            String handle = dobj.getHandle();

            boolean created = false;
            copydobj = repo.getDigitalObject(handle);
            if (copydobj==null) {
                created = true;
                copydobj = repo.createDigitalObject(handle);
            } else {
                List<String> attributesToDelete = new ArrayList<String>();
                CloseableIterator<Map.Entry<String,String>> attIter = copydobj.listAttributes();
                try {
                    while(attIter.hasNext()) {
                        String key = attIter.next().getKey();
                        if (attributes.get(key) == null) {
                            attributesToDelete.add(key);
                        }
                    }
                }
                finally {
                    attIter.close();
                }
                copydobj.deleteAttributes(attributesToDelete);
                for(DataElement elem : copydobj.getDataElements()) {
                    if(!dobj.verifyDataElement(elem.getName())) {
                        elem.delete();
                    }
                }
            }

            copydobj.setAttributes(attributes);

            CloseableIterator<DataElement> elementIter = dobj.listDataElements();
            try {
                while (elementIter.hasNext()){
                    DataElement element = elementIter.next();
                    String name = element.getName();
                    Map<String, String> elementAtts = element.getAttributes();
                    DataElement copyDE = null;
                    if(!created) copyDE = copydobj.getDataElement(name);
                    if (copyDE==null) {
                        copyDE = copydobj.createDataElement(name);
                    } 
                    else {
                        List<String> attributesToDelete = new ArrayList<String>();
                        CloseableIterator<Map.Entry<String,String>> attIter = copyDE.listAttributes();
                        try {
                            while(attIter.hasNext()) {
                                String key = attIter.next().getKey();
                                if (attributes.get(key) == null) {
                                    attributesToDelete.add(key);
                                }
                            }
                        }
                        finally {
                            attIter.close();
                        }
                        copyDE.deleteAttributes(attributesToDelete);
                    }
                    copyDE.setAttributes(elementAtts);

                    if(copyAll == true || elementsToInclude.contains(element.getName())) {
                        copyDE.write(element.read(), false);
                    }
                    else {
                        copyDE.setAttribute(Constants.ELEMENT_ATTRIBUTE_DATA_NOT_PRESENT, "1");
                        copyDE.write(new ByteArrayInputStream(new byte[0]),false);
                    }
                }
            }
            finally {
                elementIter.close();
            }
        }
        catch(UncheckedRepositoryException e) {
            e.throwCause();
        }
        return copydobj;
    }
    
	
    /**
     * Copies the digital object and the specified data elements.  Lesser network usage.
     * @param elementsToInclude list of the element names to be included in the copy.
     */
    @Deprecated
    public static DigitalObject copy(Map<String,String> searchResult,Repository remoteRepo,Repository repo,List<String> elementsToInclude, boolean copyAll) throws IOException, RepositoryException, CreationException {
        if(elementsToInclude==null) elementsToInclude = Collections.emptyList();
        Map<String, String> attributes = new HashMap<String, String>();
        Map<String, String> elAttValues = new HashMap<String, String>();
        Map<String, Map<String, String>> elAttMap = new HashMap<String, Map<String,String>>();
  
        for(Map.Entry<String,String> map : searchResult.entrySet()) {
            String key = map.getKey();
            String value = map.getValue();
            if (key.startsWith("field:objatt_")) {
                String trimmedKey = key.substring(13);
                attributes.put(trimmedKey, value);
            }
        }
        for(Map.Entry<String,String> map : searchResult.entrySet()) {
            String key = map.getKey();
            String value = map.getValue();
            if(key.startsWith("field:elatt_")) {
                String trimmedKey = key.substring(12);
                int underscore = trimmedKey.indexOf("_");
                String nameToken = trimmedKey.substring(0,underscore);
                String elName = StringUtils.decodeURLIgnorePlus(nameToken);
                String elAtt = trimmedKey.substring(underscore+1);
                elAttValues.put(elAtt, value);
                elAttMap.put(elName, elAttValues);
            }
        }
        DigitalObject dobj = null;
        DataElement element = null;
        String handle = searchResult.get("objectid");
        dobj = repo.getDigitalObject(handle);
        if (dobj==null) {
            dobj = repo.createDigitalObject(handle);
        } else {
            Map<String, String> copyAtts = dobj.getAttributes();
            for(String key : copyAtts.keySet()) {
                if (!attributes.containsKey(key)) {
                    dobj.deleteAttribute(key);
                }
            }
            CloseableIterator<DataElement> copyElementIter = dobj.listDataElements();
            try {
                while(copyElementIter.hasNext()) {
                    DataElement elem = copyElementIter.next();
                    if(!elAttMap.containsKey(elem.getName())) {
                        elem.delete();
                    }
                }
            }
            finally {
                copyElementIter.close();
            }
        }
        dobj.setAttributes(attributes);
        for(String elName : elAttMap.keySet()) {
            if (!dobj.verifyDataElement(elName)) {
                element = dobj.createDataElement(elName);
            } 
            else {
                element = dobj.getDataElement(elName);

                Map<String, String> elementAtts = element.getAttributes();
                for(String elKey: elementAtts.keySet()){
                    if(!elAttMap.get(elName).containsKey(elKey)) {
                        element.deleteAttribute(elKey);
                    }
                }	
            }
            if(copyAll == true || elementsToInclude.contains(elName)) {
                DigitalObject copydobj = remoteRepo.getDigitalObject(handle);
                DataElement datael = copydobj.getDataElement(elName);
                element.write(datael.read(), false);
            }
            else {
                element.setAttribute(Constants.ELEMENT_ATTRIBUTE_DATA_NOT_PRESENT, "1");
                element.write(new ByteArrayInputStream(new byte[0]),false);
            }
        }
        for(Entry<String, Map<String, String>> mappy : elAttMap.entrySet()) {
            DataElement daty = dobj.getDataElement(mappy.getKey());
            daty.setAttributes(mappy.getValue());
        }
        return dobj;
    }
}
