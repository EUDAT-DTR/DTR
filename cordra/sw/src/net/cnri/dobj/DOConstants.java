/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

/**
 * Constants for use with the Digital Object Registry and associated
 * systems.
 */
public interface DOConstants {
  public static final String ANONYMOUS_ID = "1037/anon";
  
  public static final String LIST_OPERATIONS_OP_ID = "1037/0";
  public static final String CREATE_OBJ_OP_ID = "1037/1";
  public static final String ADD_TYPE_OP_ID = "1037/2";
  public static final String REMOVE_TYPE_OP_ID = "1037/3";
  public static final String HAS_TYPE_OP_ID = "1037/4";
  public static final String GET_DATA_OP_ID = "1037/5";
  public static final String STORE_DATA_OP_ID = "1037/6";
  public static final String DELETE_DATA_OP_ID = "1037/7";
  public static final String LIST_DATA_OP_ID = "1037/8";
  public static final String LIST_TYPES_OP_ID = "1037/9";
  public static final String LIST_OBJECTS_OP_ID = "1037/10";
  public static final String GET_CREDENTIALS_OP_ID = "1037/42";
  public static final String STORE_CREDENTIAL_OP_ID = "1037/43";
  public static final String GET_REPO_TXNS_OP_ID = "1037/44";
  public static final String DELETE_OBJ_OP_ID = "1037/45";
  public static final String DOES_OBJ_EXIST_OP_ID = "1037/46";
  public static final String PUSH_REPO_TXN_OP_ID = "1037/47";
  public static final String GET_SERIALIZED_FORM_OP_ID = "1037/48";
  public static final String ADD_RELATIONSHIPS_OP_ID = "1037/53";
  
  public static final String AUDIT_QUERY_OP_ID = "1037/11";
  public static final String AUDIT_GET_OP_ID = "1037/12";
  public static final String INJECT_KNOWBOT_OP_ID = "1037/13";

  public static final String SEARCH_OP_ID = "1037/search";
  public static final String REINDEX_OBJECT_ID = "1037/reindex";
  public static final String INDEX_UP_TO_DATE_ID = "1037/indexUpToDate";
  
  public static final String GRANT_KEY_OP_ID = "1037/52";
  public static final String OBJECT_NAME_HDL_TYPE = "CNRI.OBJECT_NAME";
  public static final String OBJECT_SERVER_HDL_TYPE = "CNRI.OBJECT_SERVER";
  public static final String OBJECT_SVRINFO_HDL_TYPE = "CNRI.OBJECT_SERVER_INFO";
  public static final String RIGHTS_DELEGATION_OBJECT_HDL_TYPE = "CNRI.RIGHTS_DELEGATION_OBJECT";
  
  public static final String SET_ATTRIBUTES_OP_ID = "1037/49";
  public static final String GET_ATTRIBUTES_OP_ID = "1037/50";
  public static final String DEL_ATTRIBUTES_OP_ID = "1037/51";
  
  public static final String CHECK_AUTHORIZATION_OP_ID = "1037/54";
  
  public static final String CHECK_DELEGATE_OP_ID = "1037/55";
  public static final String LIST_DELEGATORS_OP_ID = "1037/57";  
  
  public static final String DATE_FORMAT_MDYHMS = "yyyyMMdd-HH:mm:ss:SSS";
  
  public static final String OBJECT_ATTS_MSGTYPE = "objatts";
  public static final String ELEMENT_ATTS_MSGTYPE = "elementatts";
  
  public static final String PARAM_ATTRIBUTES = "att";
  public static final String PARAM_ELEMENT_ID = "elementid";
  
  public static final String EM_COMMAND_GET_VERSION = "1037/14";
  public static final String EM_COMMAND_VERIFY_DO = "1037/15";
  public static final String EM_COMMAND_LIST_DO = "1037/16";
  public static final String EM_COMMAND_GET_DISSEMINATION = "1037/17";
  public static final String EM_COMMAND_GET_KEY_METADATA = "1037/18";
  public static final String EM_COMMAND_LIST_DISSEMINATORS = "1037/19";
  public static final String EM_COMMAND_LIST_DATASTREAMS = "1037/20";
  public static final String EM_COMMAND_GET_TYPE_SIGNATURE = "1037/21";
  public static final String EM_COMMAND_GET_SERVLET = "1037/22";
  public static final String EM_COMMAND_GET_DATASTREAM_KEY_METADATA = "1037/23";
  public static final String EM_COMMAND_GET_DATASTREAM_BYTES = "1037/24";
  public static final String EM_COMMAND_CREATE_DO = "1037/25";
  public static final String EM_COMMAND_DELETE_DO = "1037/26";
  public static final String EM_COMMAND_CREATE_DATASTREAM = "1037/27";
  public static final String EM_COMMAND_DELETE_DATASTREAM = "1037/28";
  public static final String EM_COMMAND_CREATE_DISSEMINATOR = "1037/29";
  public static final String EM_COMMAND_DELETE_DISSEMINATOR = "1037/30";
  public static final String EM_COMMAND_SET_EXECUTABLE = "1037/31";
  public static final String EM_COMMAND_GET_EXECUTABLE = "1037/32";
  public static final String EM_COMMAND_GET_DISSEMINATOR_METADATA = "1037/33";
  public static final String EM_COMMAND_LIST_ATTACHMENTS = "1037/34";
  public static final String EM_COMMAND_SET_ATTACHMENTS = "1037/35";
  public static final String EM_COMMAND_DELETE_ATTACHMENTS = "1037/36";
  public static final String EM_COMMAND_SET_DATASTREAM_BYTES = "1037/37";
  public static final String EM_COMMAND_SET_READ_REQUEST = "1037/38"; // NEED TO IMPLEMENT
  public static final String EM_COMMAND_GET_READ_REQUEST = "1037/39"; // NEED TO IMPLEMENT
  public static final String EM_COMMAND_SET_DO_POLICIES = "1037/40";
  public static final String EM_COMMAND_LIST_DO_POLICIES = "1037/41";
  
  public static final String CLIENT_AUTH_TYPE_HSPUBKEY = "hspubkey";
  public static final String CLIENT_AUTH_TYPE_HSSECKEY = "hsseckey";
  
  
  public static final String OWNER_ATTRIBUTE = "publisher";
  public static final String CREATOR_ATTRIBUTE = "creator";
  public static final String TITLE_ATTRIBUTE = "title";
  public static final String FILE_NAME_ATTRIBUTE = "filename";
  public static final String LANGUAGE_ATTRIBUTE = "language";
  public static final String MIME_TYPE_ATTRIBUTE = "mimetype";
  public static final String ABSTRACT_ATTRIBUTE = "abstract";
  public static final String IS_PART_OF_ATTRIBUTE = "ispartof";
  public static final String REFERENCES_ATTRIBUTE = "references";
  public static final String NOTES_ATTRIBUTE = "notes";
  public static final String FOLDER_ATTRIBUTE = "folder";
  public static final String DATE_CREATED_ATTRIBUTE = "internal.created";
  public static final String DATE_MODIFIED_ATTRIBUTE = "internal.modified";
  public static final String SIZE_ATTRIBUTE = "internal.size";
  
  public static final String CONTENT_ELEMENT_ID = "content";
  public static final String RIGHTS_ELEMENT_ID = "internal.rights";
  public static final String REPO_RIGHTS_ELEMENT_ID = "internal.default_rights";
  
  public static final int DEFAULT_CLIENT_CERT_EXPIRATION_DAYS = 365 * 25;
  
}

