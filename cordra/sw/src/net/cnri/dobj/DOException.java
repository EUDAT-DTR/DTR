/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.net.*;
import java.io.*;


/** Exception used in most digital object components.  */
public class DOException 
  extends java.io.IOException
{
  public static final int REPOSITORY_AUTHENTICATION_ERROR = 100;
  public static final int NETWORK_ERROR = 101;
  public static final int PROTOCOL_ERROR = 102;
  public static final int NO_SUCH_OBJECT_ERROR = 103;
  public static final int UNABLE_TO_LOCATE_OBJECT_ERROR = 104;
  public static final int CRYPTO_ERROR = 105;
  public static final int PERMISSION_DENIED_ERROR = 106;
  public static final int STORAGE_ERROR = 107;
  public static final int OBJECT_ALREADY_EXISTS = 108;
  public static final int INTERNAL_ERROR = 109;
  public static final int OPERATION_NOT_AVAILABLE = 110;
  public static final int APPLICATION_ERROR = 111;
  public static final int REPLICATION_ERROR = 112;
  public static final int SERVER_ERROR = 113;
  public static final int NO_SUCH_ELEMENT_ERROR = 114;
  
  public static final int REPLICATION_ITEM_OUT_OF_DATE = 113;
  
  private int code;
  private String message = null;
  
  public DOException(int code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }

  public DOException(int code, String message, Throwable t) {
      super(message,t);
      this.code = code;
      this.message = message;
  }

  public String toString() {
    return String.valueOf(code) + '(' + lookupCode(code) + "): "+message;
  }
  
  public String getMessage() {
    return message;
  }
  
  public int getErrorCode() {
    return this.code;
  }
  
  public static final String lookupCode(int errorCode) {
    switch(errorCode) {
      case REPOSITORY_AUTHENTICATION_ERROR:
        return "REPOSITORY_AUTHENTICATION_ERROR";
      case NETWORK_ERROR:
        return "NETWORK_ERROR";
      case PROTOCOL_ERROR:
        return "PROTOCOL_ERROR";
      case NO_SUCH_OBJECT_ERROR:
        return "NO_SUCH_OBJECT_ERROR";
      case UNABLE_TO_LOCATE_OBJECT_ERROR:
        return "UNABLE_TO_LOCATE_OBJECT_ERROR";
      case OPERATION_NOT_AVAILABLE:
        return "OPERATION_NOT_AVAILABLE";
      case CRYPTO_ERROR:
        return "CRYPTO_ERROR";
      case PERMISSION_DENIED_ERROR:
        return "PERMISSION_DENIED_ERROR";
      case STORAGE_ERROR:
        return "STORAGE_ERROR";
      case OBJECT_ALREADY_EXISTS:
        return "OBJECT_ALREADY_EXISTS";
      case INTERNAL_ERROR:
        return "INTERNAL_ERROR";
      case APPLICATION_ERROR:
        return "APPLICATION_ERROR";
      case SERVER_ERROR:
        return "SERVER_ERROR";
      default:
        return "unknown error code: "+errorCode;
    }
  }
	
  
  private static String getFriendlyMessageForCode(int responseCode) {
    switch(responseCode) {
      case REPOSITORY_AUTHENTICATION_ERROR:
        return "The identity of the server could not be established.";
      case NETWORK_ERROR:
        return "There was a low-level network or communications error.";
      case PROTOCOL_ERROR:
        return "There was an error in the protocol when talking to the client or server.";
      case NO_SUCH_OBJECT_ERROR:
        return "The object being addressed does not exist on the server being contacted.";
      case UNABLE_TO_LOCATE_OBJECT_ERROR:
        return "The given object could not be located.  If the given object was recently created, you may\nneed to allow some time for its identifier to propagate through the naming system.";
      case CRYPTO_ERROR:
        return "There was an error encrypting, decrypting, or setting up an encrypted connection.";
      case PERMISSION_DENIED_ERROR:
        return "You, or some software operating on your behalf was denied permission to\nperform the operation.";
      case STORAGE_ERROR:
        return "There was an error accessing the storage system on the repository";
      case OBJECT_ALREADY_EXISTS:
        return "The object could not be created because an object already exists\nwith the specified identifier.";
      case OPERATION_NOT_AVAILABLE:
        return "The given operation is not available for the specified object.";
      case INTERNAL_ERROR:
        return "An error internal to the client or repository systems has occurred.";
      case APPLICATION_ERROR:
        return "An application-level error occurred";
      case SERVER_ERROR:
        return "The server returned an invalid response";
      default:
        return "An unidentified error ("+responseCode+") has occurred";
    }
  }
  
  public static String getFriendlyMessageForException(Exception e) {
    if(e instanceof DOException) {
      return getFriendlyMessageForCode(((DOException)e).getErrorCode())+"\n\n"+e;
    } else if(e instanceof java.net.ConnectException) {
      return "There was an error trying to connect to the server.  The server may not have been running or perhaps it is blocked by a firewall: \n"+e;
    } else if(e instanceof NoRouteToHostException) {
      return "There was an error trying to connect to the server.  There doesn't seem to be any way to get to the server from this location: \n"+e;
    } else if(e instanceof BindException) {
      return "There was an error attempting to listen to a network port.  It is possible that you don't have permission to listen to the particular port, or perhaps another program is already listening to the port: \n"+e;
    } else if(e instanceof UnknownHostException) {
      return "There was an error resolving the host name to an IP address.  Blame DNS: \n"+e;
    } else if(e instanceof EOFException) {
      return "The end of a stream was unexpectedly reached: \n"+e;
    } else if(e instanceof FileNotFoundException) {
      return "The specified file was not found: \n"+e;
    } else if(e instanceof MalformedURLException) {
      return "The format of the specified URL was invalid: \n"+e;
    } else {
      return String.valueOf(e);
    }
      
  }
  
  
}
