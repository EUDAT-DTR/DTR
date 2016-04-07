/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.*;

/**
 * This class encapsulates the connection to a digital object server.
 * A connection comprises a multiplexed set of channels, each channel being
 * a pair of input/output streams, with one channel being used for controlling
 * the connection (opening new channels, negotiating authentication, etc).
 * The protocol is flexible due to the use of simple text control messages,
 * connection parameter negotiation (protocol version, encryption method, etc).
 */
public class DOConnection {
  public static final int CONTROL_CHANNEL_ID = 0;
  
  public static final boolean DEFAULT_DEBUG_BYTES = false;
  public static final boolean DEFAULT_DEBUG_CONTROL = false;
  public static final boolean DEFAULT_DEBUG = false;
  public static final boolean DEFAULT_DEBUG_BLOCKING = false;
  
  public static long MAX_BYTES_IN_BLOCKING_WINDOW = 500000;
  public static long MIN_BYTES_IN_BLOCKING_WINDOW = 200000;
  public boolean DEBUG = DEFAULT_DEBUG;
  public boolean DEBUG_CONTROL = DEFAULT_DEBUG_CONTROL;
  public boolean DEBUG_BYTES = DEFAULT_DEBUG_BYTES;
  public boolean DEBUG_BLOCKING = DEFAULT_DEBUG_BLOCKING;
  
  public static final int DEFAULT_WRITE_BUFFER_SIZE = 100000;
  public static final int DEFAULT_READ_BUFFER_SIZE = 100000;
  
  static final String OBJECT_ID_HEADER = "object_id";
  static final String SERVER_ID_HEADER = "server_id";
  static final String MY_ID_HEADER = "my_id";
  static final String ENTITY_ID_HEADER = "entity_id";
  static final String SETUP_ENCRYPTION_FLAG = "setup_encryption";
  
  private static final String OPEN_CHANNEL_COMMAND = "openchannel";
  private static final String CLOSE_CHANNEL_COMMAND = "closechannel";
  private static final String BLOCK_CHANNEL_COMMAND = "block";
  private static final String UNBLOCK_CHANNEL_COMMAND = "unblock";
  private static final int PROTO_MAJOR_VERSION = 1;
  private static final int PROTO_MINOR_VERSION = 4;
  private static final String CLOSE_STREAM_COMMAND = "closestream";
  private static final String INPUT_STREAM_ID = "input";
  private static final String OUTPUT_STREAM_ID = "output";

  private volatile long maxWait = 60000; // sixty seconds

  protected static final String AUTHENTICATE_COMMAND = "authenticate";
  public static final String SUCCESS_RESPONSE_CODE = "success";
  public static final String ERROR_RESPONSE_CODE = "error";
  
  private static final Long RESOLVER_LOCK = new Long(12340124l);
  private static Resolver resolver = null;
  private static AtomicInteger nextConnectionID = new AtomicInteger(1);
  
  private static ExecutorService connectionMonitorExecutorService = Executors.newCachedThreadPool(new ThreadFactory() {
      ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();
      
      public Thread newThread(Runnable r) {
          Thread t = defaultThreadFactory.newThread(r);
          t.setDaemon(true);
          return t;
      }
  });
  
  static {
    try {
      Security.addProvider((Provider)Class.
                           forName("org.bouncycastle.jce.provider.BouncyCastleProvider").
                           newInstance());
      //Security.removeProvider("BC");
    } catch (Exception e) {
//      e.printStackTrace(System.err);
    }
  }
  
  protected final DOAuthentication auth;
  
  private DOConnectionMonitor connectionMonitor = null; // reset in close
  private ExecutorService blockingUnblockingExecutor = null; // reset in close
  private volatile ConnectionEncryption encryption = null; // reset in clear
  private Socket socket =  null; // reset in connectAs
  private DataInputStream sockIn = null; // reset in connectAs
  DataOutputStream sockOut = null; // reset in connectAs
  ChannelOutputStream controlOut; // reset in connectAs
  private int MAX_BUFFER_CACHE = 15;
  
  private HeaderSet controlMsg = new HeaderSet(); // the most recent control message, use is essentially local
  private ConcurrentHashMap<Integer,StreamPair> openChannels = new ConcurrentHashMap<Integer,StreamPair>(); // reset in clear
  private AtomicInteger requestID = new AtomicInteger(0); // counter for calculating the next request ID; no harm in not ever resetting
  private final ConcurrentHashMap<String,MsgExchange> responseTable = new ConcurrentHashMap<String,MsgExchange>(); // reset in clear
  
  private int protocolVersion = PROTO_MAJOR_VERSION; // reset in connectAs...
  private int minorProtocolVersion = PROTO_MINOR_VERSION; // reset in connectAs...

  private int thisConnectionID = 0; // only used for debugging
  
  private DOConnectionListener listener; // independent of connections
  private AtomicInteger nextChannel = new AtomicInteger(1); // no harm in not ever resetting
  private boolean connectedAsClient = false; // reset in connectAs...
  private boolean connectedAsServer = false; // reset in connectAs...
  private final boolean preferSSLConnections;
  
  /** the connection to which client auth will be forwarded */
  private DOConnection proxyAuthConnection = null; //unused
  
  private LinkedList bufferCache = new LinkedList(); // cleared when socket disconnected (only failure of not clearing is excess memory use)
  
  private long lastChunkWritten = System.currentTimeMillis(); // unused
  private long lastChunkRead = System.currentTimeMillis(); // unused
  
  public DOConnection(DOAuthentication authentication) {
    this.auth = authentication;
    this.thisConnectionID = nextConnectionID.getAndIncrement();
    
    DEBUG_CONTROL = System.getProperty("do.debug_control", "false").equalsIgnoreCase("true");
    DEBUG_BYTES = System.getProperty("do.debug_bytes", "false").equalsIgnoreCase("true");
    DEBUG_BLOCKING = System.getProperty("do.debug_blocking", "false").equalsIgnoreCase("true");
    preferSSLConnections = System.getProperty("do.prefer_ssl", "false").equalsIgnoreCase("true");
    
    if(System.getProperty("do.debug_async", "false").equalsIgnoreCase("true")) {
      Thread debugThread = new Thread(new ChannelDebugger(), "Channel Debugger");
      debugThread.setDaemon(true);
      debugThread.start();
    }
  }
  
  /** Return the major protocol version of this connection if the connection
    * handshake has already taken place. */
  public int getProtocolMajorVersion() {
    return protocolVersion;
  }
  
  /** Return the minor protocol version of this connection if the connection
    * handshake has already taken place. */
  public int getProtocolMinorVersion() {
    return minorProtocolVersion;
  }
  
  /**
   * Return the authentication object that was provided in the constructor.
   */
  protected final DOAuthentication getAuth() {
    return this.auth;
  }
  
  /** Return the identifier that we are using to identify ourself */
  public final String getAuthID() {
    return getAuth().getID();
  }

  /** Reset all information to restart a connection */
  private void clear() {
      this.controlMsg.removeAllHeaders();
      this.openChannels.clear();
      this.encryption = null;
      this.responseTable.clear();
      // cachedPubKeys?
  }
  
  /**
   * Sets up the server side of the connection on the given socket.
   */
  protected synchronized void connectAsServer(Socket serverSocket) 
    throws DOException, IOException
  {
    connectedAsClient = false;
    connectedAsServer = false;
    if(serverSocket instanceof SSLSocket) {
      ((SSLSocket)serverSocket).setWantClientAuth(true);
    }
    this.socket = serverSocket;
    this.socket.setKeepAlive(true);
    this.socket.setTcpNoDelay(true);
    this.sockIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    this.sockOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    this.controlOut = new ChannelOutputStream(CONTROL_CHANNEL_ID);
    
    clear();
    
    // read the initial parameters
    // this is where we get the version of the protocol that the client is using
    HeaderSet request = new HeaderSet();
    request.readHeaders(sockIn);
    
    if(DEBUG || DEBUG_CONTROL) {
      System.err.println("connection from: "+
                         socket.getInetAddress().getHostAddress()+
                         ": "+request);
    }
    HeaderSet response = new HeaderSet("response");
    String protocol = request.getStringHeader("protocol", "");
    if(!protocol.equalsIgnoreCase("dop")) {
      response.addHeader("status", "ERROR");
      response.addHeader("code", DOException.PROTOCOL_ERROR);
      response.addHeader("message", "Unknown protocol: '"+protocol+"'");
      response.writeHeaders(this.sockOut);
      this.sockOut.flush();
      throw new DOException(DOException.PROTOCOL_ERROR, 
                            "Unknown protocol: '"+protocol+"'");
    }
    
    protocolVersion = request.getIntHeader("protocol_major_version", 0);
    minorProtocolVersion = request.getIntHeader("protocol_minor_version",0);

    // use their protocol or ours?
    if(protocolVersion > PROTO_MAJOR_VERSION) {
      // use our version, they are too far ahead
      protocolVersion = PROTO_MAJOR_VERSION;
      minorProtocolVersion = PROTO_MINOR_VERSION;
    } else if(protocolVersion == PROTO_MAJOR_VERSION) {
      // same major version, use the lesser of the minor versions
      minorProtocolVersion = Math.min(minorProtocolVersion, PROTO_MINOR_VERSION);
    } else { // protocolVersion < PROTO_MINOR_VERSION
      // we are ahead of them - use their older version
    }
    
    // write the response headers, indicating our version and any other
    // parameters
    response.addHeader("status", "OK");
    response.addHeader("protocol_major_version", protocolVersion);
    response.addHeader("protocol_minor_version", minorProtocolVersion);
    response.writeHeaders(this.sockOut);
    this.sockOut.flush();
    
    //System.err.println("DOP Server Socket: TCP_NODELAY=" + socket.getTcpNoDelay());

    connectedAsServer = true;
    startConnectionThread();
  }
  
  
  /** 
   * Locates and connects to a server for the service identified
   * by serverHandle.
   */
  protected synchronized void connectAsClient(DOServerInfo server)
    throws DOException
  {
    if(server==null) throw new NullPointerException();
    try {
      try { close(); } catch (IOException e) { e.printStackTrace(); }
      // make the connection
      this.socket = null;
      connectedAsServer = false;
      connectedAsClient = false;
      InetAddress mappedAddr = getResolver().getResolver().getConfiguration().
        mapLocalAddress(InetAddress.getByName(server.getHostAddress()));
      if(DEBUG || DEBUG_CONTROL) {
        System.err.println("connecting to: "+mappedAddr.getHostAddress()+
                           ":"+server.getPort());
      }
      
      clear();
      
      int sslPort = server.getSSLPort();
      if(sslPort>0 && preferSSLConnections) {
        try {
          SSLContext sslContext = SSLContext.getInstance("TLS");
          KeyManager kms[] = null;
          if(auth instanceof PKAuthentication) {
            kms = new KeyManager[] { new DOSSLKeyManager((PKAuthentication)auth) };
          }
          sslContext.init(kms, new TrustManager[] { new DOSSLTrustManager() }, 
                          ConnectionEncryption.getRandom());
          
          Socket baseSocket = new Socket(mappedAddr, sslPort);
          baseSocket.setKeepAlive(true);
          SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().
            createSocket(baseSocket, server.getHostAddress(), sslPort, true); // autoClose
          this.socket = sslSocket;
          
        } catch (Exception e) {
          System.err.println("Error establishing TLS connection: "+e+"; falling back to standard DOP");
          e.printStackTrace(System.err);
        }
      }
      
      if(this.socket==null) {
        this.socket = new Socket(mappedAddr, server.getPort());
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
      }
      
      this.sockIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
      this.sockOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
      this.controlOut = new ChannelOutputStream(CONTROL_CHANNEL_ID);
      
      // exchange the initial parameters
      HeaderSet request = new HeaderSet("init");
      request.addHeader("protocol", "dop");
      request.addHeader("protocol_major_version", protocolVersion);
      request.addHeader("protocol_minor_version", minorProtocolVersion);
      request.writeHeaders(this.sockOut);
      this.sockOut.flush();
      
      HeaderSet response = new HeaderSet();
      response.readHeaders(sockIn);
      
      protocolVersion = response.getIntHeader("protocol_major_version",
                                              protocolVersion);
      minorProtocolVersion = response.getIntHeader("protocol_minor_version",
                                                   minorProtocolVersion);
      
      if(protocolVersion>PROTO_MAJOR_VERSION) {
        throw new DOException(DOException.PROTOCOL_ERROR,
                              "Server responded with incompatible version: "+
                              protocolVersion);
      }
      
      connectedAsClient = true;
      
      //System.err.println("DOP Client Socket: TCP_NODELAY=" + socket.getTcpNoDelay());
      
      startConnectionThread();
    } catch (IOException e) {
//      e.printStackTrace(System.err);
        DOException ex = new DOException(DOException.NETWORK_ERROR, 
                "Error communicating with server "+server.getServerID()+
                " of "+server.getServiceID()+": "+e);
        ex.initCause(e);
        throw ex;
    }
  }
  
  /** Returns true iff this connection is encrypted in some way. */
  public boolean isEncrypted() {
    if(this.encryption!=null) return true;
    
    if(socket instanceof SSLSocket) {
      SSLSocket sslSock = (SSLSocket)socket;
      String cipherSuite = sslSock.getSession().getCipherSuite();
      if(cipherSuite==null) return false;
      if(cipherSuite.trim().equalsIgnoreCase("none")) return false;
      System.err.println("SSL socket encryption: "+cipherSuite);
      return true;
    }
    return false;
  }
  
  protected void setEncryption(ConnectionEncryption encryptor) {
      this.encryption = encryptor;
  }
  
  public long getProtocolTimeout() {
      return maxWait;
  }

  public void setProtocolTimeout(long maxWait) {
      this.maxWait = maxWait;
  }
  
  /**
   * Returns true iff the connection is still open
   */
  public boolean isOpen() {
    return (connectedAsClient || connectedAsServer) && socket.isConnected();
  }
  
  /**
   * Sets the listener that will be notified when new channels are opened. 
   */
  public void setListener(DOConnectionListener newListener) {
    listener = newListener;
  }
  
  public void close() 
    throws IOException
  {
    socketDisconnected();
    if (socket != null) socket.close();
  }
  
  private synchronized void socketDisconnected() {
    if(DEBUG) System.err.println("disconnected");
    connectedAsServer = false;
    connectedAsClient = false;
    if(connectionMonitor!=null) {
      connectionMonitor.stopMonitoring();
      connectionMonitor = null;
    }
    if(blockingUnblockingExecutor!=null) blockingUnblockingExecutor.shutdown();
    socketWasDisconnected();
    
    synchronized(bufferCache) {
      bufferCache.clear();
//      System.gc();
    }
  }
  
  /**
   * This method is called after a socket has been disconnected.  It can
   * be overridden by subclasses (DOClientConnection and DOServerConnection)
   * that wish to be notified when a connection is broken.
   */
  protected void socketWasDisconnected() {
  }
  
  /** Kicks off the thread that manages a connection */
  private synchronized void startConnectionThread() {
    if(connectionMonitor!=null) {
      connectionMonitor.stopMonitoring();
    }
    final String clientSrvStr = connectedAsClient ? "client" : "server";
    String threadName;
    try {
        threadName = "Connection Monitor for "+
            socket.getInetAddress().getHostAddress()+
            ":"+socket.getLocalPort()+"; id="+thisConnectionID+
            "; "+clientSrvStr;
    } catch (Exception e) {
        threadName = "Connection Monitor for id="+thisConnectionID+
                "; "+clientSrvStr;
    }
    connectionMonitor = new DOConnectionMonitor(threadName);
    if(blockingUnblockingExecutor!=null) blockingUnblockingExecutor.shutdown();
    blockingUnblockingExecutor = new ThreadPoolExecutor(0,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r,"Blocking/unblocking messages for id="+thisConnectionID+"; "+clientSrvStr);
            t.setDaemon(true);
            return t;
        }
    },new ThreadPoolExecutor.DiscardPolicy());
    connectionMonitorExecutorService.execute(connectionMonitor);
  }
  
  
  /** Acquire an unused buffer that can contain at least minSize bytes. */
  private ByteBuffer getBuffer(int minSize) {
    outstandingBuffers++;
    synchronized(bufferCache) {
      // because the list is sorted, we'll get the smallest available
      // buffer that meets our needs
      for(Iterator it=bufferCache.listIterator(0); it.hasNext(); ) {
        ByteBuffer b = (ByteBuffer)it.next();
        if(b.capacity()>=minSize) {
          it.remove();
          b.clear();
          return b;
        }
      }
    }
    return ByteBuffer.allocate(minSize+1000);
  }
  
  private static final Comparator bufferSorter = new Comparator() {
    public int compare(Object o1, Object o2) {
      return ((ByteBuffer)o1).capacity() - ((ByteBuffer)o2).capacity();
    }
    
    public boolean equals(Object obj) {
      return obj==this;
    }
  };
  
  private volatile int outstandingBuffers = 0; // only used for debugging
  
  /** Return the given buffer to the buffer pool */
  private void returnBuffer(ByteBuffer buf) {
    if(buf==null) return;
    // don't cache more than a certain number of ByteBuffers
    outstandingBuffers--;
    if(bufferCache.size() > MAX_BUFFER_CACHE) {
      // System.gc();
      return;
    }
    buf.clear();
    synchronized(bufferCache) {
      bufferCache.add(buf);
      Collections.sort(bufferCache, bufferSorter);
    }
  }
  
  
  /** Sends the specified chunk over the connection.  The contents of the
    * ByteBuffer may be modified as a result of encrypting the bytes in-line. */
  protected void sendChunk(int channelID, ByteBuffer buf)
    throws IOException
  {    
    synchronized(sockOut) {
      sockOut.writeInt(channelID);
      lastChunkWritten = System.currentTimeMillis();
      ConnectionEncryption enc = encryption;
      if(DEBUG_BYTES) System.err.println("sending: "+
                                         "channel="+ channelID+
                                         "; len="+buf.remaining()+
                                         "; conn="+this.hashCode()+
                                         "; bytes="+niceBytes(buf.array()));
      if(enc==null) {
        sockOut.writeInt(buf.remaining());
        sockOut.write(buf.array(), 0, buf.remaining());
      } else {
        try {
          buf = enc.processOutgoingChunk(buf);
          //byte bytes[] = buf.array();
          int buflen = buf.remaining();
          sockOut.writeInt(buflen);
          sockOut.write(buf.array(), 0, buflen);
        } catch (GeneralSecurityException e) {
          throw new IOException("Encryption error: "+e);
        }
      }
      sockOut.flush();
    }
  }
  
  
  private static final String PRINTABLE = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()_+-=[]{}\"';:,./<>?|\\";
  
  static final String niceBytes(byte buf[]) {
    return niceBytes(buf, 32);
  }
  static final String niceBytes(byte buf[], int maxlen) {
    StringBuffer sb = new StringBuffer();
    boolean someChars = false;
    maxlen = Math.min(maxlen, 32);
    for(int i=0; buf!=null && i<maxlen && i<buf.length; i++) {
      if(PRINTABLE.indexOf(buf[i])>=0) {
        sb.append((char)buf[i]);
        someChars = true;
      } else {
        sb.append("(0x");
        sb.append(Integer.toHexString(buf[i]));
        sb.append(')');
      }
    }
    if(someChars)
      return sb.toString();
    else
      return "<binary>";
  }
  
  /** 
   * Sends the specified short message over the control channel and waits for the
   * the response.
   */
  HeaderSet sendControlMessage(HeaderSet msg, boolean waitForResponse)
    throws IOException
  {
    if(waitForResponse && msg.getMessageType().equalsIgnoreCase("response")) {
      throw new IOException("Cannot wait for a response to a response message");
    }
    
    String requestIDStr = null;
    MsgExchange msgExchange = null;
    if(waitForResponse) {
      requestIDStr = "c"+(requestID.getAndIncrement());
      msg.removeHeadersWithKey("_requestid");
      msg.addHeader("_requestid", requestIDStr);
      msgExchange = new MsgExchange(msg);
      responseTable.put(requestIDStr, msgExchange);
    }
    
    if(DEBUG_CONTROL) {
      System.err.println("send:"+thisConnectionID+":control>>"+
                         String.valueOf(msg).trim()+" waiting="+waitForResponse+
                         "; enc="+(encryption!=null));
    }
    
    synchronized(controlOut) {
      msg.writeHeaders(controlOut);
    }
    
    controlOut.flush();
    
    if(waitForResponse) {
      return msgExchange.waitForResponse();
    }
    return null;
  }
  
  /** Sends the specified short message over the control channel */
  protected final void sendControlResponse(HeaderSet response, HeaderSet inResponseTo)
    throws IOException
  {
    response.setMessageType("response");
    response.removeHeadersWithKey("_requestid");
    response.addHeader("_requestid", inResponseTo.getStringHeader("_requestid",""));
    sendControlMessage(response, false);
  }
  
  
  /** Gets the existing channel with the given ID.  If no channel exists with
    * that ID then this will return null. */
  protected StreamPair getExistingChannel(int channelID) {
      return openChannels.get(Integer.valueOf(channelID));
  }
  
  
  /** Returns the socket over which the connection is made */
  public Socket getSocket() {
    return socket;
  }


  /** If the input and output streams are closed, close the entire channel */
  private void inputStreamClosed(int channelID, boolean tellOtherSide) 
    throws IOException
  {
    StreamPair pair = getExistingChannel(channelID);
    if(pair==null) return;
    ChannelInputStream in = (ChannelInputStream)pair.getInputStream();
    if(in.isClosed) return;
    in.closeWithoutNotice();
    
    if(tellOtherSide) {
      HeaderSet request = new HeaderSet(CLOSE_STREAM_COMMAND);
      request.addHeader("channelid", channelID);
      request.addHeader("streamid", OUTPUT_STREAM_ID); // it's the output stream on the other side
      sendControlMessage(request, false);
    }
    
    if(((ChannelOutputStream)pair.getOutputStream()).isClosed) {
      if(channelID==0) {
        if(DEBUG_CONTROL) {
          System.err.println("Closing control channel: "+channelID);
        }
      }
      openChannels.remove(channelID);
    }
  }
  
  
  /** 
   * Mark the output stream as closed, and notify the other side.
   * If the input stream is already closed, remove the channel from
   * the list of channels. */
  private void outputStreamClosed(int channelID, boolean tellOtherSide)
    throws IOException
  {
    StreamPair pair = getExistingChannel(channelID);
    if(pair==null) return;
    ChannelOutputStream out = (ChannelOutputStream)pair.getOutputStream();
    if(out.isClosed) return; // it was already closed
    
    out.closeWithoutNotice();
    
    if(tellOtherSide) {
      HeaderSet request = new HeaderSet(CLOSE_STREAM_COMMAND);
      request.addHeader("channelid", channelID);
      request.addHeader("streamid", INPUT_STREAM_ID); // it's the input stream on the other side
      sendControlMessage(request, false);
    }
    
    if(((ChannelInputStream)pair.getInputStream()).isClosed) {
        openChannels.remove(channelID);
    }      
  }
  
  
  protected StreamPair getNewChannel()
    throws IOException
  {
    // no pair entered, create a new one
    if(!connectedAsClient) return null; // only clients can setup new channels

    int channelID = nextChannel.getAndIncrement();

    StreamPair newPair =  new StreamPair(channelID,
                                         new ChannelInputStream(channelID),
                                         new ChannelOutputStream(channelID));
    
    openChannels.put(Integer.valueOf(channelID),newPair);
    
    // notify the other side of the new channel
    HeaderSet request = new HeaderSet(OPEN_CHANNEL_COMMAND);
    request.addHeader("channelid", channelID);

    HeaderSet response = sendControlMessage(request, true);
    return newPair;
  }
  
  
  /** Encode a String into a UTF8 byte array */
  public static final byte[] encodeUTF8(String str) {
    try {
      return str.getBytes("UTF8");
    } catch (UnsupportedEncodingException e) {}
    return str.getBytes(); // fall back, wrong format, but it will work
  }
  
  
  /** Decode a UTF8 byte array into a String */
  public static final String decodeUTF8(byte buf[], int offset, int len) {
    if(buf==null || buf.length==0) return "";
    try {
      return new String(buf, offset, len, "UTF8");
    } catch (Exception e) {
      System.err.println(e);
    }
    return new String(buf, offset, len);
  }
  
  
  /** Decode a UTF8 byte array into a String */
  public static final String decodeUTF8(byte buf[]) {
    if(buf==null || buf.length==0) return "";
    return decodeUTF8(buf, 0, buf.length);
  }
  
  
  /** Sets the singleton Resolver object */
  public static final void setResolver(Resolver newResolver) {
    synchronized (RESOLVER_LOCK) {
      resolver = newResolver;
    }
  }
  
  /** Returns a singleton Resolver object. */
  public static final Resolver getResolver() {
    if(resolver!=null) return resolver;
    synchronized (RESOLVER_LOCK) {
      if(resolver==null) {
        resolver = new Resolver();
        //resolver.setSessionTracker(new ClientSessionTracker());
        resolver.getResolver().traceMessages = 
          System.getProperty("do.debug_handle", "false").equalsIgnoreCase("true");
      }
    }
    return resolver;
  }
  
  public String toString() {
    Socket s = socket;
    StringBuffer sb = new StringBuffer();
    sb.append("DOConnection ");
    if(connectedAsClient)
      sb.append("client ");
    if(connectedAsServer)
      sb.append("server ");
    sb.append(thisConnectionID);
    sb.append("; ");
    
    if(s!=null) {
      sb.append("; ");
      sb.append(s.getInetAddress().getHostAddress());
      sb.append(":");
      sb.append(s.getPort());
    }
    sb.append("; enc=");
    sb.append(encryption);
    sb.append("; ");
    sb.append(super.toString());
    return sb.toString();
  }
  
  
  /**
   * This class represents a control message exchange that is waiting for a response
   */
  private class MsgExchange {
    private final HeaderSet request;
    private volatile HeaderSet response = null;
    private boolean hasResponse = false;
    long msgSent = 0;
    long responseReceived = 0;
    long responseReturned = 0;
    
    private final CountDownLatch responseSignal = new CountDownLatch(1);
    
    MsgExchange(HeaderSet request) {
      this.request = request;
      this.msgSent = System.currentTimeMillis();
    }
    
    HeaderSet waitForResponse() throws IOException {
        if(response!=null) return this.response;
        try {
            responseSignal.await(maxWait, TimeUnit.MILLISECONDS);
            if(response==null) {
                if(DEBUG_CONTROL) {
                    System.err.println(" waiting for response to "+request);
                }
                if(!isOpen()) {
                    throw new IOException(("Socket closed while waiting for response to "+request).trim());
                }
                else {
                    DOConnection.this.close();
                    throw new IOException(("Timeout while waiting for response to "+ request).trim());
                }
            }
            this.responseReturned = System.currentTimeMillis();
        } catch (Exception e) {
            if(e instanceof IOException) throw (IOException)e;
            throw new IOException(("No response from server to request: "+request).trim());
        }
        return this.response;
    }

    void responseReceived(HeaderSet response) {
        this.response = response;
        this.responseReceived = System.currentTimeMillis();
        this.responseSignal.countDown();
    }

    public String toString() {
      return "req="+request+"; resp="+response;
    }
  }
  
  
  /**
   * This class is used for debugging purposes.  It is a Runnable that occasionally prints
   * the state of the connection.
   */
  private class ChannelDebugger
    implements Runnable
  {
    public void run() {
      while(true) {
        try { Thread.sleep(60000); } catch (Exception e) {}
        System.err.println(" async status <"+DOConnection.this+
                           "; buffersInUse="+outstandingBuffers+"; enc="+encryption+">");
        try {
          for(Iterator it = responseTable.entrySet().iterator(); it.hasNext(); ) {
            System.err.println("  ||"+thisConnectionID+"| unreceived response: "+it.next());
          }
          for(Iterator it = openChannels.entrySet().iterator(); it.hasNext(); ) {
            StreamPair sp = (StreamPair)it.next();
            System.err.println("  conn:"+thisConnectionID+"; channel: "+sp.channelID+
                               ";\n      in=["+sp.getInputStream()+
                               "];\n      out=["+sp.getOutputStream()+"]");
          }
          
        } catch (Exception e) {
          e.printStackTrace(System.err);
        }
        if(!isOpen()) break;
      }
    }
  }
  
  
  /**
   * This class reads chunks from the underlying socket and directs them
   * to the appropriate channels.
   */
  private class DOConnectionMonitor
    implements Runnable
  {
    private final String threadName;  
    private byte readBuf[] = new byte[2048];
    private boolean keepMonitoring = true;
    
    public DOConnectionMonitor(String threadName) {
        this.threadName = threadName;
    }
    
    void stopMonitoring() {
      keepMonitoring = false;
    }

    private StringBuffer controlBuffer = new StringBuffer();
    
    public void run() {
      Thread.currentThread().setName(threadName);
      while(keepMonitoring) {
        try {
          if(sockIn==null || !isOpen()) { // not connected!
            keepMonitoring = false;
            break;
          }
          
          int channelID;
          int chunkLen;
          
          ByteBuffer buf = null;
          
          // synchronized block to prevent encrypted messages from
          // being processed until after encryption has been set
          // this synchronization probably isnt necessary as this is
          // the only thread that reads directly from the socket and
          // control messages (including encryption setup) are run on
          // this thread
          channelID = sockIn.readInt();
          lastChunkRead = System.currentTimeMillis();
          chunkLen = sockIn.readInt();
          buf = getBuffer(chunkLen);
          buf.clear();
          
          int r;
          int n = chunkLen;
          while(n>0 && (r=sockIn.read(readBuf, 0, Math.min(n, readBuf.length)))>=0) {
            buf.put(readBuf, 0, r);
            n -= r;
          }
          
          buf.flip(); // prepare to start reading the buffer
          
          ConnectionEncryption enc = encryption;
          
          if(enc!=null) {
            enc.processIncomingChunk(buf);
            int origLen = chunkLen;
            chunkLen = buf.remaining();
            if(DEBUG_BYTES) System.err.println(" read encrypted:"+
                                               "origlen="+origLen+
                                               "len="+chunkLen+
                                               "; channel="+channelID+
                                               "; conn="+this.hashCode()+
                                               "; bytes="+niceBytes(buf.array()));
          } else {
            if(DEBUG_BYTES) System.err.println(" read:"+
                                               "len="+chunkLen+
                                               "; channel="+channelID+
                                               "; conn="+this.hashCode()+
                                               "; bytes="+niceBytes(buf.array()));
          }
          
          if(channelID==0) {
            // the bytes were meant for the control channel
            // intercept them and process them now, if there is a newline in there
            
            // add the bytes to the control channel buffer
            byte tmpbuf[] = null;
            int tmpoffset = 0;
            if(buf.hasArray()) {
              tmpbuf = buf.array();
              tmpoffset = buf.position();
            } else {
              tmpbuf = new byte[buf.remaining()];
              buf.get(tmpbuf, 0, tmpbuf.length);
            }
            String controlStr = decodeUTF8(tmpbuf, tmpoffset, chunkLen);
            controlBuffer.append(controlStr);
            returnBuffer(buf);
            
            // process control messages until we get a newline
            int nlIdx;
            while((nlIdx = controlBuffer.indexOf("\n")) >= 0) {
              // synchronize to prevent writes while this message is processing
              /////synchronized(controlChannelWriteLock) { 
                String msg = controlBuffer.substring(0, nlIdx);
                if(controlMsg.readHeadersFromString(msg)) {
                  try {
                    processControlMessage(controlMsg);
                  } catch (Exception e) {
                    System.err.println("Error processing control message: "+
                                       controlMsg+"; error: "+e);
                    e.printStackTrace(System.err);
                  }
                }
                controlBuffer.delete(0, nlIdx+1);
              //////}
            }
          } else {
            StreamPair pair = getExistingChannel(channelID);
            if(pair==null) {
                pair =  new StreamPair(channelID,
                        new ChannelInputStream(channelID),
                        new ChannelOutputStream(channelID));
                StreamPair oldPair = openChannels.putIfAbsent(Integer.valueOf(channelID),pair);
                if(oldPair!=null) pair = oldPair;
            }
            ((ChannelInputStream)pair.getInputStream()).addToBuffer(buf);
          }
        } catch (SocketException e) {
          keepMonitoring = false;
        } catch (EOFException e) {
          keepMonitoring = false;
        } catch (Exception e) {
          e.printStackTrace(System.err);
        }
      } 
      try { close(); } catch (IOException e) { e.printStackTrace(); }
      Thread.currentThread().setName("Unused Connection Monitor");
    }
    
    // Note; headers will be re-used; copy if thread-safety required (e.g. for msgExchange.responseReceived)
    private void processControlMessage(HeaderSet headers) 
      throws Exception
    {
      if(DEBUG_CONTROL) {
        System.err.println(""+thisConnectionID+":control<<"+headers+
                           "; enc="+(encryption!=null));
      }
      
      String command = headers.getMessageType();
      if(command.equalsIgnoreCase("response") &&
         headers.getStringHeader("_requestid", null)!=null) {
        String requestID = headers.getStringHeader("_requestid", "");
        MsgExchange msgExchange = (MsgExchange)responseTable.get(requestID);
        if(msgExchange!=null) {
          msgExchange.responseReceived(new HeaderSet(headers));
          responseTable.remove(requestID);
        } else {
          System.err.println("Warning: received unrequested response: "+headers);
        }
      } else if(command.equalsIgnoreCase(CLOSE_STREAM_COMMAND)) {
        int channelID = headers.getIntHeader("channelid", -1);
        if(channelID>0) {
          String streamType = headers.getStringHeader("streamid","");
          if(streamType.equalsIgnoreCase(INPUT_STREAM_ID)) {
            inputStreamClosed(channelID, false);
          } else if(streamType.equalsIgnoreCase(OUTPUT_STREAM_ID)) {
            outputStreamClosed(channelID, false);
          } else {
            System.err.println("Warning: unknown stream type in closestream: "+
                               streamType);
          }
        }
      } else if(command.equalsIgnoreCase(OPEN_CHANNEL_COMMAND)) {
        int newChannelID = headers.getIntHeader("channelid", -1);
        if(newChannelID>0) {
            StreamPair oldPair = getExistingChannel(newChannelID);
            StreamPair newPair = null;
            if(oldPair==null) {
                newPair =  new StreamPair(newChannelID,
                        new ChannelInputStream(newChannelID),
                        new ChannelOutputStream(newChannelID));
                oldPair = openChannels.putIfAbsent(Integer.valueOf(newChannelID),newPair);
            }
            if(oldPair!=null) {
              HeaderSet response = new HeaderSet("response");
              response.addHeader("status", "error");
              response.addHeader("message", "Channel is already open");
              sendControlResponse(response, headers);  // this is thread-safe for headers
            } else {
              // add a new channel and return SUCCESS
              HeaderSet response = new HeaderSet("response");
              response.addHeader("status", "success");
              sendControlResponse(response, headers);  // this is thread-safe for headers
              
              // notify any listeners that the new pair exists
              listener.channelCreated(newPair);
            }
          
        } else {
          HeaderSet response = new HeaderSet("response");
          response.addHeader("status", "error");
          response.addHeader("message", "invalid channel ID: "+newChannelID);
          sendControlResponse(response, headers);  // this is thread-safe for headers
        }
      } else if(command.equalsIgnoreCase(AUTHENTICATE_COMMAND)) {
        handleAuthenticateRequest(new HeaderSet(headers));
      } else if(command.equalsIgnoreCase(BLOCK_CHANNEL_COMMAND)) {
        int channelID = headers.getIntHeader("channelid", -1);
        if(channelID>0) {
          StreamPair channel = getExistingChannel(channelID);
          if(channel!=null) {
            ((ChannelOutputStream)channel.getOutputStream()).block();
          }
        }
      } else if(command.equalsIgnoreCase(UNBLOCK_CHANNEL_COMMAND)) {
        int channelID = headers.getIntHeader("channelid", -1);
        if(channelID>0) {
          StreamPair channel = getExistingChannel(channelID);
          if(channel!=null) {
            ((ChannelOutputStream)channel.getOutputStream()).unblock();
          }
        }
      } else {
        System.err.println("Unknown command: '"+command+"' in message: "+headers);
      }
    }
  }
  
  
  /** Provides proof of the validity of the entity while also helping to 
   *  set up encryption for the connection (if requested).
   */
  private void handleAuthenticateRequest(HeaderSet authRequest)
    throws Exception
  {
    boolean initEncryption = authRequest.getBooleanHeader(SETUP_ENCRYPTION_FLAG, false);
    // we are not able to 
    DOConnection conn = proxyAuthConnection;
    if(conn!=null && !initEncryption) {
      HeaderSet authResponse = conn.sendControlMessage(authRequest, true);
      sendControlResponse(authResponse, authRequest);
      return;
    }

    if(!initEncryption) {
        sendAuthenticateResponse(authRequest,initEncryption);
        return;
    }
    
    // lock the output stream so that nobody can send
    // encrypted messages until after the encryption
    // filter has been set
    synchronized(controlOut) {
        synchronized(sockOut) {
            sendAuthenticateResponse(authRequest,initEncryption);
            return;
        }
    }
  }

  private void sendAuthenticateResponse(HeaderSet authRequest, boolean initEncryption) throws Exception {
      // we are being called on the thread that reads chunks from the
      // socket, so we don't need to synchronize any read attempts

      //if(DEBUG_CONTROL)
      //  System.err.println(" handling authentication request: "+authRequest);


      // get the identity of the caller.  Can be
      // used in the future to return a secret key that is encrypted using
      // the other entitity's RSA public key.
      String requestorID = authRequest.getStringHeader(ENTITY_ID_HEADER, "");
      HeaderSet authResponse = new HeaderSet();
      ConnectionEncryption enc = null;
      if(initEncryption) {
          // get the encryption ready to go and communicate the parameters 
          // to the other side (after encrypting the session key)
          enc = ConnectionEncryption.constructInstance(this, authRequest, authResponse);
      }

      // now we just need to prove our identity by signing the nonce
      if(auth instanceof SecretKeyAuthentication && getProtocolMajorVersion()==1 && getProtocolMinorVersion()<4) {
          ((SecretKeyAuthentication)auth).oldBrokenSignChallenge(authRequest,authResponse);
      }
      else {
          auth.signChallenge(authRequest, authResponse);
      }

      // ...and adding our credentials (if any)
      java.security.cert.Certificate creds[] = auth.getCredentials();
      if(creds!=null) {
          authResponse.addHeader("numcreds", creds.length);
          for(int i=creds.length-1; i>=0; i--) {
              try {
                  authResponse.addHeader("cred"+i, creds[i].getEncoded());
              } catch (Exception e) {
                  System.err.println("Error encoding certificate: "+e);
              }
          }
      }
      sendControlResponse(authResponse, authRequest);

      if(enc!=null) { // all further communication should be encrypted
          setEncryption(enc);
      }
  }
  
  class ChannelOutputStream
    extends java.io.OutputStream 
  {
    volatile boolean isClosed = false;
    volatile boolean isBlocked = false;
    final int channelID;
    private ByteBuffer outBuf; /* only modified once, to null */
    
    public String toString() {
      return "dop-output: id="+channelID+" closed="+isClosed+" blocked="+isBlocked+" outbuf="+outBuf;
    }
    
    ChannelOutputStream(int channelID) {
      this.channelID = channelID;
      this.outBuf = getBuffer(DEFAULT_WRITE_BUFFER_SIZE);
      this.outBuf.clear();
    }
    
    public void flush() 
      throws IOException
    {
      flushChunk();
      sockOut.flush();
    }
    
    private synchronized void flushChunk()
      throws IOException
    {
      if(outBuf==null) return;
      if(outBuf.position()>0) {
        outBuf.flip();
        sendChunk(channelID, outBuf);
        outBuf.clear();
      }
    }
    
    void block() {
      if(DEBUG_BLOCKING) System.err.println("  writer blocking connection "+thisConnectionID+" channel: "+channelID);
      isBlocked = true;
    }
    
    synchronized void unblock() {
      if(isBlocked) {
        isBlocked = false;
        if(DEBUG_BLOCKING) System.err.println("  writer unblocking connection "+thisConnectionID+" channel: "+channelID);
        this.notifyAll(); 
      }
    }

    private synchronized void waitForBlockage() throws IOException {
        if(isBlocked && !isClosed) {
            try {
                long start = System.currentTimeMillis();
                do {
                    this.wait(maxWait);
                } while(isBlocked && !isClosed && System.currentTimeMillis() - start < maxWait);
            } catch (InterruptedException e) {}

            if(isBlocked && !isClosed) {
                DOConnection.this.close();
                throw new IOException("Timeout while waiting for blockage on connection "+thisConnectionID+
                        "."+channelID);
            }
        }
    }
    
    
    public synchronized void write(int b)
    throws IOException
    {
      waitForBlockage();
      if(isClosed || outBuf==null) throw new IOException("Attempted to write to a closed OutputStream");
      
      if(!outBuf.hasRemaining()) {
        flushChunk();
      }
      outBuf.put((byte)b);
    }
    
    
    public synchronized void write(byte buf[], int offset, int len) 
      throws IOException
    {
      if(len<=0) return;
      waitForBlockage();
      if(isClosed || outBuf==null) throw new IOException("Attempted to write to a closed OutputStream");
      
      int remaining;
      while(len>0) { // write some bytes to the channel
        if(!outBuf.hasRemaining()) flushChunk();
        int writeLen = Math.min(len, outBuf.remaining());
        outBuf.put(buf, offset, writeLen);
        offset += writeLen;
        len -= writeLen;
      }
    }
    
    private void returnBuf() {
      if(outBuf==null) return;
      synchronized(this) {
        ByteBuffer oldBuf = outBuf;
        outBuf = null;
        returnBuffer(oldBuf);
      }
    }
    
    /**
     * This closes this end of the stream without notifying the
     * connected stream on the other side of the network.  This is
     * generally called when the other side sends a closestream message.
     */
    public void closeWithoutNotice() {
      this.isClosed = true;
    }
    
    public boolean isClosed() {
      return this.isClosed;
    }
    
    public void close()
      throws IOException
    {
      flush();
      returnBuf();
      outputStreamClosed(channelID, true);
    }

  }

  /** This is a class that reads bytes from a series of buffers.  Other
    * threads can add to the buffers 
    */
  private class ChannelInputStream 
    extends java.io.InputStream
  {
    volatile boolean isClosed = false;
    volatile boolean isBlocked = false;
    volatile IOException unblockException = null;
    private volatile int available;
    private final LinkedList buffers = new LinkedList();
    private ByteBuffer currentBuf = null;
    private HeaderSet unblockMsg = null;
    private HeaderSet blockMsg = null;
    final int channelID;
    
    ChannelInputStream(int channelID) {
      this.channelID = channelID;
    }
    
    public String toString() {
      return "dop-input: id="+channelID+" closed="+isClosed+" blocked="+isBlocked+" inbuf="+currentBuf;
    }
    
    private void unblockIfNeeded() throws IOException {
        if(unblockException!=null) {
            IOException e = unblockException;
            unblockException = null;
            throw e;
        }
        // if we've requested that the other side block their writes, 
        // check to see if we can tell them to start up again
        if(isBlocked && available < MIN_BYTES_IN_BLOCKING_WINDOW) {
            isBlocked = false;
            if(DEBUG_BLOCKING) System.err.println("  reader unblocking connection "+thisConnectionID+" channel: "+channelID);
            HeaderSet newUnblockMessage = unblockMsg;
            if(newUnblockMessage==null) {
                newUnblockMessage = new HeaderSet(UNBLOCK_CHANNEL_COMMAND);
                newUnblockMessage.addHeader("channelid", channelID);
                unblockMsg = newUnblockMessage;
            }
            blockingUnblockingExecutor.execute(new Runnable() { public void run() {
                try {
                    if(!isBlocked) sendControlMessage(unblockMsg, false);
                }
                catch(IOException e) {
                    unblockException = e;
                }
            }});
        }
    }
    
    /** 
      * Sets up the current buffer for reading, waiting for a new buffer
      * to be received, if necessary.  If the end of the stream has been 
      * reached, returns -1.  Otherwise this returns a non-negative integer.
      */
    private int fillBuffer() 
    throws IOException
    {
        if(currentBuf!=null && currentBuf.hasRemaining()) return 0;

        // return the current buffer (if any)
        returnBuffer(currentBuf);
        currentBuf = null;

        // no more bytes... wait for more
        if(buffers.isEmpty()) { 
            if(isClosed || !isOpen()) return -1;
            try {
                long start = System.currentTimeMillis();
                do {
                    buffers.wait(maxWait);
                } while(buffers.isEmpty() && !isClosed && System.currentTimeMillis() - start < maxWait);
            } catch (InterruptedException e) {}

            if(buffers.isEmpty()) {
                if(DEBUG_BLOCKING) {
                    System.err.println("waiting for more bytes on connection "+thisConnectionID+
                            "."+channelID+"; isBlocked="+isBlocked+" isClosed="+isClosed);
                }
                if(isClosed || !isOpen()) {
                    return -1;
                }
                else {
                    DOConnection.this.close();
                    throw new IOException("Timeout while waiting for bytes on connection "+thisConnectionID+
                            "."+channelID+"; isBlocked="+isBlocked+" isClosed="+isClosed);
                }
            }
        }

        currentBuf = (ByteBuffer)buffers.remove(0);
        return 0;
    }
    
    /** Return the number of bytes that are in the buffer waiting to be read. */
    public int available() {
      return available;
    }
    
    public int read()
      throws IOException
    {
      unblockIfNeeded();
      synchronized(buffers) {
        int result = fillBuffer();
        if(result<0) return result;
        available--;
        return 0x000000ff&currentBuf.get();
      }        
    }
    
    public int read(byte buf[], int offset, int len)
      throws IOException
    {
      unblockIfNeeded();
      synchronized(buffers) {
        int result = fillBuffer();
        if(result<0) return result;
        int numbytes = Math.min(len, currentBuf.remaining());
        available -= numbytes;
        currentBuf.get(buf, offset, numbytes);
        return numbytes;
      }        
    }
    
    public int read(byte buf[]) 
      throws IOException
    {
      return read(buf, 0, buf.length);
    }
    
    /** 
      * Adds the specified byte array to the buffer for this stream.  If the buffer
      * has grown beyond MAX_BYTES_IN_BLOCKING_WINDOW then a message will be sent
      * over the control channel asking the other side to block any writes to this 
      * channel until an unblock control message is sent.  This should _only_ be called 
      * from the DOConnection class.
      */
    void addToBuffer(ByteBuffer newBuffer) {
      int previousAvailable;
      synchronized(buffers) {
        previousAvailable = available;
        buffers.add(newBuffer);
        available += newBuffer.remaining();
        buffers.notifyAll();
      }

      if(!isBlocked) {
          int nowAvailable = available;
          int minAvailable = nowAvailable < previousAvailable ? nowAvailable : previousAvailable;
          // if we aren't already 'blocked' and have too many bytes in the input buffer,
          // tell the other side to stop writing until the input buffer has been reduced
          if(minAvailable > MAX_BYTES_IN_BLOCKING_WINDOW) {
              isBlocked = true;
              if(DEBUG_BLOCKING) System.err.println("  reader blocking connection "+thisConnectionID+" channel: "+channelID);
              HeaderSet newBlockMsg = blockMsg;
              if(newBlockMsg==null) {
                  newBlockMsg = new HeaderSet(BLOCK_CHANNEL_COMMAND);
                  newBlockMsg.addHeader("channelid", channelID);
              }
              blockMsg = newBlockMsg;
              blockingUnblockingExecutor.execute(new Runnable() { public void run() {
                  try {
                      if(isBlocked) sendControlMessage(blockMsg, false);
                  } catch (IOException e) {
                      System.err.println("Error sending blocking request: "+e);
                      isBlocked = false;
                  }
              }});
          }
      }
    }
    
    /**
      * This closes this end of the stream without notifying the
     * connected stream on the other side of the network.  This is
     * generally called when the other side sends a closestream message.
     */
    public void closeWithoutNotice() {
      this.isClosed = true;
      synchronized (buffers) {
        buffers.notifyAll(); // tell any readers that the stream is closed
      }
    }
    
    public void close()
      throws IOException
    {
      inputStreamClosed(channelID, true);
    }
    
  }
  
}


