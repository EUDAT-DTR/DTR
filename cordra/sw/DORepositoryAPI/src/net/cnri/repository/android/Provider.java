/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import android.content.ContentResolver;
import android.provider.BaseColumns;

public class Provider {
    public static final String AUTHORITY = "net.cnri.agora.repository.provider";
    
    public static class ObjectColumns implements BaseColumns {
        private ObjectColumns() {}
        
        public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.net.cnri.repository.object";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.net.cnri.repository.object";
        
        public static final String HANDLE = "handle";
        public static final String ELEMENT_ID = "el._id";
        public static final String ELEMENT_NAME = "el.name";
        public static final String ATT_NAME = "att.name";
        public static final String ATT_VALUE = "att.value";
        
        public static final String[] HANDLE_PROJECTION = { HANDLE };
        public static final String[] ID_PROJECTION = { _ID };
        public static final String[] ID_HANDLE_PROJECTION = { _ID, HANDLE };
        public static final String[] ENTIRE_OBJECT_PROJECTION = { ELEMENT_NAME, ATT_NAME, ATT_VALUE, ELEMENT_ID };

        public static final String[] OBJECT_DEFAULT_PROJECTION = new String[]{
            _ID, 
            HANDLE 
        };
        public static final String[] ENTIRE_OBJECT_DEFAULT_PROJECTION = new String[] {
            _ID,
            ELEMENT_NAME,
            ATT_NAME,
            ATT_VALUE,
            ELEMENT_ID
        };

    }

    public static class AttributeColumns implements BaseColumns {
        private AttributeColumns() {}
        
        public static final String ATTRIBUTE_PATH_SEGMENT = "att";
        public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.net.cnri.repository.attribute";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.net.cnri.repository.attribute";
        public static final String EL_CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.net.cnri.agora.repository.element_attribute";

        public static final String ATTRIBUTE_WHERE_NAME = "att.name=?";
        public static final String ELEMENT_ATTRIBUTE_WHERE_NAME = "elatt.name=?";
        
        public static final String DO_ID = "do_id";
        public static final String HANDLE = "handle";
        
        public static final String EL_ID = "el_id";
        
        public static final String NAME = "name";
        public static final String VALUE = "value";
        
        public static final String ELEMENT_NAME = "el.name";
        
        public static final String[] ATTRIBUTE_PROJECTION = { NAME, VALUE, _ID /*, HANDLE, DO_ID*/ };
        public static final String[] ELEMENT_ATTRIBUTE_PROJECTION = { NAME, VALUE, _ID /*, HANDLE, DO_ID, ELEMENT_NAME, EL_ID*/ };
        public static final String[] SEARCH_ATTRIBUTE_PROJECTION = {DO_ID, HANDLE};
        public static final String[] VALUE_PROJECTION = { VALUE, _ID };
        public static final String[] NAME_PROJECTION = { NAME, _ID };
        
        public static final String[] ATTRIBUTE_DEFAULT_PROJECTION = {
            _ID,
//          DO_ID,
//          ObjectColumns.HANDLE,
            NAME,
            VALUE
        };

        public static final String[] ELEMENT_ATTRIBUTE_DEFAULT_PROJECTION = {
            _ID,
//          DO_ID,
//          ObjectColumns.HANDLE,
//          EL_ID,
//          ELEMENT_NAME,
            NAME,
            VALUE
        };
        
        public static final String[] SEARCH_DEFAULT_PROJECTION = {
            DO_ID,
            ObjectColumns.HANDLE
        };
    }
    
    public static class ElementColumns implements BaseColumns {
        private ElementColumns() {}
        
        public static final String ELEMENT_PATH_SEGMENT = "el";
        public static final String CONTENT_DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.net.cnri.repository.element";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.net.cnri.repository.element";

        public static final String ELEMENT_WHERE_NAME = "el.name=?";

        public static final String DO_ID = "do_id";
        public static final String NAME = "name";

        public static final String[] ID_PROJECTION = new String[] { _ID };
        public static final String[] NAME_PROJECTION = new String[] { NAME };
        public static final String[] ID_NAME_PROJECTION = new String[] { _ID, NAME };
        
        public static final String[] DEFAULT_PROJECTION = {
            _ID, /*DO_ID, ObjectColumns.HANDLE,*/ NAME
        };
    }
}
