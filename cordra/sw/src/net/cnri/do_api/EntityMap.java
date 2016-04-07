/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.do_api;

import net.cnri.simplexml.*;

import java.io.*;
import java.util.*;


/** An EntityMap stores a mapping from identifiers to local, human readable names. */
public class EntityMap {
  public static final String ADDRESS_BOOK_ELEMENT_ID = "pdo.id_map";
  public static final String NAME_ATTRIBUTE = "name";
  public static final String TYPE_ATTRIBUTE = "name";
  public static final String ID_ATTRIBUTE = "id";
  public static final String ENTITY_LIST = "entities";
  
  private XTag entities;
  private ArrayList entries;
  private XTag entryList;
  
  public EntityMap() {
    entities = new XTag("entities");
    entryList = new XTag(ENTITY_LIST);
    entities.addSubTag(entryList);
    entries = new ArrayList();
  }
  
  /** Get the number of known entities in the mapping/list */
  public int getNumEntities() {
    return entries.size();
  }
  
  /** Return a java.util.List containing the EntityMap.Entity objects in the list. */
  public List getEntities() {
    return Collections.unmodifiableList(entries);
  }
  
  /** Return the address book entity at the given index */
  public Entity getEntity(int entityIndex) {
    return (Entity)entries.get(entityIndex);
  }
  
  /** Return the name that is associated with the given ID.  If no name is associated
    * with the given ID then return the given value. */
  public String getEntityName(String entityID, String defaultVal) {
    Entity entity = getEntityForID(entityID);
    System.err.println(" entity ID: "+entityID+" yields: "+entity);
    if(entity!=null) return entity.getAttribute(NAME_ATTRIBUTE, defaultVal);
    return defaultVal;
  }
  
  /** Return a label that can be used to describe the entity having the given ID.
    * If no name is associated with the given ID then return the given entityID. */
  public String getEntityLabel(String entityID) {
    Entity entity = getEntityForID(entityID);
    if(entity!=null) return entity.toString();
    return entityID;
  }
  
  /** Return the entity with the given ID or null if no entity has that ID. */
  public synchronized Entity getEntityForID(String entityID) {
    for(Iterator it=entries.iterator(); it.hasNext(); ) {
      Entity entity = (Entity)it.next();
      if(entity.hasID(entityID)) return entity;
    }
    return null;
  }
  
  public synchronized void addEntity(Entity newEntity) {
    if(newEntity==null) throw new NullPointerException();
    String id = newEntity.getAttribute(ID_ATTRIBUTE, null);
    if(id==null) throw new NullPointerException();
    Entity oldEntity = getEntityForID(id);
    if(oldEntity!=null) throw new RuntimeException("An item already exists with ID: "+id);
    entryList.addSubTag(newEntity.tag);
    entries.add(newEntity);
  }
  
  /** Remove the given entity from the list */
  public synchronized void removeEntity(Entity entityToRemove) {
    if(entityToRemove==null) return;
    entryList.removeSubTag(entityToRemove.tag);
    entries.remove(entityToRemove);
  }
  
  
  /** Load the entity map from the given DigitalObject */
  public void loadFromObject(DigitalObject obj) 
    throws Exception
  {
    InputStream in = null;
    try {
      if(!obj.verifyDataElement(ADDRESS_BOOK_ELEMENT_ID)) return;
      
      DataElement element = obj.getDataElement(ADDRESS_BOOK_ELEMENT_ID);

      in = element.read();
      XTag entities = new XParser().parse(new InputStreamReader(in, "UTF8"), false);
      ArrayList entries = new ArrayList();
      
      XTag entryList = entities.getSubTag(ENTITY_LIST);
      if(entryList!=null) {
        for(int i=0; i<entryList.getSubTagCount(); i++) {
          entries.add(new Entity(entryList.getSubTag(i)));
        }
      } else {
        entryList = new XTag(ENTITY_LIST);
        entities.addSubTag(entryList);
      }
      
      synchronized (this) {
        this.entities = entities;
        this.entryList = entryList;
        this.entries = entries;
      }
    } finally {
      try { in.close(); } catch (Exception e) {}
    }
  }
  
  
  public synchronized void saveToObject(DigitalObject obj) 
    throws Exception
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    entities.write(bout);
    DataElement element = obj.getDataElement(ADDRESS_BOOK_ELEMENT_ID);
    element.write(new ByteArrayInputStream(bout.toByteArray()));
  }
  
  
  public class Entity {
    XTag tag;
    
    Entity(XTag tag) {
      this.tag = tag;
    }
    
    public Entity() {
      tag = new XTag("entity");
    }
    
    /** Returns true if this entity has the given ID */
    public boolean hasID(String id) {
      String idVal = getAttribute(ID_ATTRIBUTE, null);
      if(idVal!=null && idVal.equals(id)) return true;
      return false;
    }
    
    /** Return the identifier (handle) for this entity */
    public String getID() {
      return getAttribute(ID_ATTRIBUTE, null);
    }
    
    /** Sets the value that is associated with the given attribute within this entity */
    public void setAttribute(String attributeName, String attributeVal) {
      if(attributeName.equals(ID_ATTRIBUTE)) {
        String oldID = tag.getAttribute(ID_ATTRIBUTE);
        if(oldID!=null && !oldID.equals(attributeVal)) {
          throw new RuntimeException("Cannot change ID attribute for entities");
        }
      }
      tag.setAttribute(attributeName, attributeVal);
    }
    
    /** Gets the value that is associated with the given attribute within this 
     *  entity or defaultVal if there is no value associated with the given
     *  attribute. */
    public String getAttribute(String attributeName, String defaultVal) {
      return tag.getAttribute(attributeName, defaultVal);
    }
    
    public String toString() {
      return tag.getAttribute(NAME_ATTRIBUTE, tag.getAttribute(ID_ATTRIBUTE, "???")) +
      " ("+tag.getAttribute(ID_ATTRIBUTE, "???")+")";
    }
  }
  
  
}
