/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.txnlog.*;
import net.cnri.apps.doserver.operators.*;
import net.cnri.dobj.*;
import net.cnri.dobj.delegation.DelegationClient;
import net.cnri.knowbots.station.*;
import net.cnri.simplexml.*;
import net.cnri.util.GrowBeforeQueueThreadPoolExecutor;
import net.cnri.util.IOForwarder;
import net.cnri.util.StreamTable;
import net.cnri.util.StreamVector;
import net.cnri.util.StringEncodingException;
import net.cnri.util.StringUtils;
import net.cnri.util.ThreadSafeDateFormat;
import net.handle.hdllib.*;
import net.handle.server.servletcontainer.AutoSelfSignedKeyManager;
import net.handle.server.servletcontainer.EmbeddedJetty;
import net.handle.server.servletcontainer.EmbeddedJettyConfig;
import net.handle.server.servletcontainer.EmbeddedJettyConfig.ConnectorConfig;
import net.handle.util.X509HSCertificateGenerator;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;

import javax.net.ssl.*;

import org.eclipse.jetty.servlet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class runs a server object that accepts and handles incoming requests for operations on digital objects. Agents in an embedded knowbot service
 * station are used to determine access control as well as to perform operations on objects in the server.
 */
public class Main {
    static Logger logger; // initialized late so that we can look for logback config files

    public static final String SERVER_INFO_FILE = "server_info.xml";
    public static final String PRIVATE_KEY_FILE = "privatekey";
    public static final String PUBLIC_KEY_FILE = "publickey";
    public static final String STORED_PASSWORDS_FILE = "password.dct";
    public static final int PUBLIC_KEY_HDL_IDX = 300;
    public static final int DEFAULT_PORT = 9900;
    public static final int DEFAULT_HTTP_PORT = 0; // 8800;
    public static final int DEFAULT_SSL_PORT = 0;
    public static final int DEFAULT_HTTPS_PORT = 0; // 8443
    public static final int DOP_LISTEN_BACKLOG = 5;
    public static final int DOPSSL_LISTEN_BACKLOG = 5;

    private static final int NUM_PRELOADED_CONNECTION_HANDLERS = 20;
    private static final int NUM_PRELOADED_OP_HANDLERS = 40;
    private static final int MAX_CONNECTION_HANDLERS = 200;
    private static final int MAX_OP_HANDLERS = 400;

    private static final boolean DEFAULT_REGISTER_IDS = false;
    private static final boolean DEFAULT_ENABLE_AUDIT_LOG = false;
    private static final boolean DEFAULT_DO_PUSH_REPLICATION = false;
    private static final boolean DEFAULT_DO_PULL_REPLICATION = true;

    public static final String OWNER_ATTRIBUTES_KEY = "owner_attributes";
    public static final String AUTH_DELEGATES_KEY = "authorization_delegate_attributes";
    public static final String DO_PUSH_REPLICATION_KEY = "enable_push_replication";
    public static final String DO_PULL_REPLICATION_KEY = "enable_pull_replication";
    public static final String ENABLE_AUDIT_LOGS_KEY = "enable_audit_logs";
    public static final String REGISTER_HANDLES_KEY = "register_handles";
    public static final String AUTO_OBJECT_PREFIX_KEY = "handle_prefix";
    public static final String SERVER_ADMIN_KEY = "server_admin";
    public static final String PORT_KEY = "port";
    public static final String BIND_KEY = "listen_address";
    public static final String ADDRESS_KEY = "public_address";
    public static final String REDIRECT_STDERR_KEY = "redirect_stderr";
    public static final String LOG_ACCESSES_KEY = "log_accesses";
    public static final String AUTO_DISCOVER_DELEGATION_KEY = "auto_discover_delegation";
    public static final String BYPASS_DELEGATION_HANDLE_LOOKUP_KEY = "bypass_delegation_handle_lookup";
    public static final String LOG_SAVE_INTERVAL_KEY = "log_save_interval";
    public static final String MONTHLY = "Monthly";
    public static final String WEEKLY = "Weekly";
    public static final String DAILY = "Daily";
    public static final String NEVER = "Never";
    public static final String HTTP_PORT_KEY = "http_port";
    public static final String SSL_PORT_KEY = "ssl_port";
    public static final String HTTPS_PORT_KEY = "https_port";
    public static final String STORAGE_CLASS_KEY = "storage_class";
    public static final String STORAGE_DIR_KEY = "storage_directory";
    public static final String SERVER_ID_KEY = "serverid";
    public static final String SERVICE_ID_KEY = "serviceid";
    public static final String[] HTTPS_KEY_STORE_FILE_NAMES = { "https.jks", "https.keystore", "https.key" };
    public static final String LISTEN_IP_KEY = "listen_addr";
    public static final String AUTO_SET_PERMISSIONS_FOR_NEW_OBJECTS = "auto_set_permissions";
    private static final String KNOWBOT_ELEMENT_PREFIX = "internal.preload_knowbot_";
    public static final String VERSION_ATT = "internal.version";

    private static Main runningMain = null;
    private Main thisServer = null;
    private boolean auditLogEnabled = DEFAULT_ENABLE_AUDIT_LOG;
    private PKAuthentication myID = null;
    private PublicKey myPublicKey = null;
    private PrivateKey myPrivateKey = null;
    private X509Certificate certificate = null;
    private PublicKeyAuthenticationInfo hdlID = null;
    private Storage storage = null;
    private ExecutionStation knowbotStation = null;
    private Configuration configuration = null;
    private AuditLog auditLog = null;
    private Authorizer authorizer = null;
    private File baseDir;
    private String serverID = null;
    private String serverAdmin = null;
    private String localServerID = null;
    private String[] ownerAttributes = null; // attribute keys that specify an owner/administrator of the DO
    private String[] authorizationDelegateAttributes; // attribute keys that specify an object that grants additional rights
    private DOClient doClient = null;
    private DelegationClient delegationClient = null;
    private ReplicationManager replicator = null;
    private StreamTable serverConfig = new StreamTable();
    private final StreamTable storedPasswords = new StreamTable();
    private StreamTable nextIDConfig = new StreamTable();
    private File nextIDFile = null;
    private ServerLog accessLogger = null;
    private ThreadSafeDateFormat dateFmt = null;
    private InetAddress listenAddress = null;
    private SSLServerSocketFactory sslSocketFactory = null;
    private ServerSocket socket = null;
    // private Server httpServer = null;
    private EmbeddedJetty embeddedJetty = null;
    private SSLServerSocket sslSocket = null;
    private volatile LuceneIndexer indexer = null;

    private volatile boolean keepServing = false;
    private volatile boolean restart = false;
    private static String[] args;

    private ExecutorService connPool = null;
    private ExecutorService opHandlerPool = null;

    private static final void printUsageAndExit() {
        System.err.println("usage: java net.cnri.apps.doserver.Main <serverdir>");
        System.err.println("   or: java net.cnri.apps.doserver.Main -setup <serverdir> [-knowbotid=<knowbot name> -knowbotloc=<path to knowbot>]");
        System.err.println("");
        System.exit(1);
    }

    private static class Options {
        boolean setupMode = false;
        File serverDir = null;
        String knowbotID = null;
        String knowbotLoc = null;

        static Options parseOptions(String argv[]) {
            Options options = new Options();
            options.parse(argv);
            return options;
        }

        void parse(String argv[]) {
            // look for setup and knowbot flags
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                if (arg.startsWith("-")) {
                    if (arg.equals("-setup")) {
                        setupMode = true;
                    }
                } else if (setupMode && arg.startsWith("-knowbotid=")) {
                    knowbotID = StringUtils.fieldIndex(arg, '=', 1);
                } else if (setupMode && arg.startsWith("-knowbotfile=")) {
                    knowbotLoc = StringUtils.fieldIndex(arg, '=', 1);
                } else if (serverDir == null) {
                    serverDir = new File(arg);
                } else {
                    printUsageAndExit();
                }
            }
            if (serverDir == null || (knowbotID == null && knowbotLoc != null) || (knowbotID != null && knowbotLoc == null)) printUsageAndExit();
        }
    }

    public static void main(String argv[]) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.out.println("DO Repository Server Software version " + Version.version);
        args = argv;
        Options options = Options.parseOptions(argv);
        if (options.serverDir.isDirectory()) {
            ClassLoader loader = new ServerClassLoader(options.serverDir);
            Thread.currentThread().setContextClassLoader(loader);
            Class realMain = loader.loadClass(Main.class.getName());
            realMain.getMethod("realMain", String[].class).invoke(null, (Object) argv);
        } else {
            realMain(argv);
        }
    }

    public static void realMain(String argv[]) throws Exception {
        Options options = Options.parseOptions(argv);
        bootstrapLogging(options.serverDir);

        // Setup, if setup flag is set
        if (options.setupMode) {
            setup(options);
            return;
        }

        File serverDir = options.serverDir;
        File privKeyFile = new File(serverDir, PRIVATE_KEY_FILE);
        PrivateKey svrKey = getPrivateKeyAfterAskingForPassphraseIfNeeded(privKeyFile);

        // load the server...
        logger.debug("*Loading server");
        Main runner = new Main(svrKey, serverDir, null);
        runningMain = runner;
        runner.serveRequests();
    }

    private static void bootstrapLogging(File serverDir) {
        System.setProperty("doserver.dir", serverDir.getAbsolutePath());
        // File configFile = new File(serverDir,"logback.groovy");
        // if(!configFile.exists()) {
        // configFile = new File(serverDir,"logback-test.xml");
        // if(!configFile.exists()) {
        // configFile = new File(serverDir,"logback.xml");
        // }
        // }
        // if(configFile.exists()) {
        // System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY,configFile.getAbsolutePath());
        // }

        // The following can be used with Logback to cause it to use a logback.xml in the repo directory
        // ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
        // if(iLoggerFactory instanceof LoggerContext) {
        // // using Logback; reconfigure using the current classloader
        // LoggerContext loggerContext = (LoggerContext)iLoggerFactory;
        // loggerContext.reset();
        // try {
        // Class<?> logbackContextInitializerClass =
        // Thread.currentThread().getContextClassLoader().loadClass(LogbackContextInitializer.class.getName());
        // Object logbackContextInitializer = logbackContextInitializerClass.getConstructor(LoggerContext.class).newInstance(loggerContext);
        // logbackContextInitializerClass.getMethod("autoConfig").invoke(logbackContextInitializer);
        // }
        // catch(Exception e) {
        // e.printStackTrace(System.err);
        // BasicConfigurator.configure(loggerContext);
        // }
        // }
        logger = LoggerFactory.getLogger(Main.class);
        logger.info("DO Repository Server Software version {}", Version.version);
    }

    private static PrivateKey getPrivateKeyAfterAskingForPassphraseIfNeeded(File privKeyFile) throws Exception {
        // Check if privatekey is accessible
        if (!privKeyFile.exists() || !privKeyFile.canRead()) {
            logger.error("Error:  cannot read private key file: " + privKeyFile.getAbsolutePath());
            System.exit(1);
        }

        // Setup input and output Streams
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(privKeyFile);

        byte buf[] = new byte[1024];
        int r;
        while ((r = fin.read(buf)) >= 0)
            bout.write(buf, 0, r);
        fin.close();
        buf = bout.toByteArray();
        byte secKey[] = null;
        if (Util.requiresSecretKey(buf)) {
            logger.debug("\nReading the private key...");
            secKey = Util.getPassphrase("Enter the passphrase to decrypt the private key in " + privKeyFile.getAbsolutePath());
        } else {
            logger.debug("*Does not Require Secret Key");
        }
        buf = Util.decrypt(buf, secKey);
        PrivateKey svrKey = Util.getPrivateKeyFromBytes(buf, 0);
        return svrKey;
    }

    private static void setup(Options options) throws Exception {
        File serverDir = options.serverDir;
        File privKeyFile = new File(serverDir, PRIVATE_KEY_FILE);
        File pubKeyFile = new File(serverDir, PUBLIC_KEY_FILE);

        SetupHelper setup = new SetupHelper();

        if (!serverDir.exists()) serverDir.mkdirs();

        System.out.println("\nTo configure your new or existing object server," + "\nplease answer the questions which follow; default" + "\nanswers, shown in [square brackets] when available," + "\ncan be chosen by pressing Enter.");

        boolean generateKeys = true;
        if (privKeyFile.exists() && pubKeyFile.exists()) {
            // there is already a public and private key... ask the user if we should re-use them
            generateKeys = setup.getBoolean("Keys already exist.  Generate new ones? ", false);
        }

        if (generateKeys) {
            setup.generateKeys(pubKeyFile, privKeyFile, "Server Authentication");
        }

        PrivateKey svrKey = getPrivateKeyAfterAskingForPassphraseIfNeeded(privKeyFile);

        // load the server...
        logger.debug("*Loading server");
        Main runner = new Main(svrKey, serverDir, setup);

        // if loading a knowbot in setup mode...
        if (options.knowbotID != null && options.knowbotLoc != null) {
            File knowbotFile = new File(options.knowbotLoc);
            if (knowbotFile.exists() && knowbotFile.canRead()) {
                runner.addOperator(options.knowbotID, new FileInputStream(knowbotFile));
            }
        }
    }

    public Main(PrivateKey serverKey, File baseDir, SetupHelper setup) throws Exception {
        this.baseDir = baseDir;
        this.thisServer = this;
        this.myPrivateKey = serverKey;
        this.myPublicKey = Util.getPublicKeyFromFile(new File(baseDir, PUBLIC_KEY_FILE).getAbsolutePath());
        readConfig();
        this.serverID = serverConfig.getStr(SERVICE_ID_KEY, null);
        this.certificate = buildCertificate(this.serverID, this.myPublicKey, this.myPrivateKey);
        readStoredPasswords();
        readNextID();
        this.localServerID = serverConfig.getStr(SERVER_ID_KEY, "");
        this.dateFmt = new ThreadSafeDateFormat("yyyyMMdd HH:mm:ss:SSS");

        initializeLoggers();

        if (setup != null) {
            setup(serverKey, setup);
            return;
        } // end entry of server configuration

        initializeAdminInfo();
        initializeStorage();
        DOConnection.setResolver(new DOStorageResolver(DOConnection.getResolver(), thisServer));
        initializeAuthentication(serverKey);
        initializeKnowbots(serverKey);
        sslSocketFactory = DOServerConnection.getSecureServerSocketFactory(this.myID, this.myPublicKey);
        this.authorizer = new Authorizer(this);
        this.doClient = new DOClient(this.myID);
        boolean bypassDelegationHandleLookup = serverConfig.getBoolean(BYPASS_DELEGATION_HANDLE_LOOKUP_KEY, false);
        boolean autoDiscoverDelegation = serverConfig.getBoolean(AUTO_DISCOVER_DELEGATION_KEY, false);
        this.delegationClient = new DelegationClient(doClient, bypassDelegationHandleLookup, bypassDelegationHandleLookup ? serverID : null, autoDiscoverDelegation);
        initializeReplicator();
        this.configuration = new Configuration(this, this.storage, serverKey); // initial operations are set up in here
        // shutdown nicely on ^C
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    shutdown();
                } catch (Exception e) {
                    logger.error("Error while shutting down server...", e);
                }
            }
        });

        new ServerMonitor().start();
    }

    private void setup(PrivateKey serverKey, SetupHelper setup) throws Exception, IOException, FileNotFoundException {
        String description = setup.getServerDescription();
        InetAddress externalAddr = setup.getExternalAddress();
        InetAddress internalAddr = setup.getInternalAddress();
        int port = setup.getListenPort();
        int sslPort = setup.getSSLPort();
        int httpPort = setup.getHTTPPort();
        int httpsPort = setup.getHTTPSPort();
        String adminStr = setup.responseToPrompt("Enter the handle for the server admin, " + "who will be allowed to perform any operation " + "on this server");
        if (adminStr != null && adminStr.trim().length() <= 0) adminStr = null;

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(new File(baseDir, PUBLIC_KEY_FILE));
        try {
            byte buf[] = new byte[1024];
            int r;
            while ((r = fin.read(buf)) >= 0)
                bout.write(buf, 0, r);
        } finally {
            fin.close();
        }
        byte pubKeyBytes[] = bout.toByteArray();

        while (true) {
            // boolean hosted = setup.getHosted();
            boolean hosted = false;
            if (hosted) {
                serverID = null;
                localServerID = "1";
            } else {
                serverID = setup.getUniqueServiceID();
                localServerID = setup.getUniqueServerID();
                if (!setup.verifyID(serverID, localServerID)) continue;
            }

            serverID = setup.registerServer(serverID, localServerID, pubKeyBytes, externalAddr, port, sslPort, httpPort, httpsPort, description, new File(baseDir, "serverinfo.xml"), new File(baseDir, PUBLIC_KEY_FILE), new File(baseDir,
                            PRIVATE_KEY_FILE), serverKey);
            break;
        }
        serverConfig.put(REDIRECT_STDERR_KEY, setup.getRedirectStdErr());
        serverConfig.put(LOG_ACCESSES_KEY, setup.getLogAccesses());
        serverConfig.put(LOG_SAVE_INTERVAL_KEY, setup.getLogSaveInterval());
        serverConfig.put(SERVICE_ID_KEY, serverID);
        serverConfig.put(SERVER_ID_KEY, localServerID);
        serverConfig.put(PORT_KEY, port);
        serverConfig.put(HTTP_PORT_KEY, httpPort);
        serverConfig.put(HTTPS_PORT_KEY, httpsPort);
        serverConfig.put(SSL_PORT_KEY, sslPort);
        serverConfig.put(BIND_KEY, internalAddr.getHostAddress());
        serverConfig.put(ADDRESS_KEY, externalAddr.getHostAddress());
        serverConfig.put(AUTO_OBJECT_PREFIX_KEY, setup.getNewObjectPrefix(serverID, localServerID));
        serverConfig.put(REGISTER_HANDLES_KEY, false);
        serverConfig.put(DO_PULL_REPLICATION_KEY, false);
        if (adminStr != null) serverConfig.put(SERVER_ADMIN_KEY, adminStr);
        File configFile = new File(baseDir, "config.dct");
        try {
            serverConfig.writeToFile(configFile);
        } catch (Exception e) {
            System.out.println("Error saving config file (" + configFile.getAbsolutePath() + "): " + e);
            throw e;
        }
        // end registration of new server ID
        // build the default configuration for the server
        XTag newConfig = new XTag("REPOCONFIG");
        XTag knowbotList = new XTag("KNOWBOTS");
        newConfig.addSubTag(knowbotList);
        File knowbotDir = new File(baseDir, "knowbots");
        setupKnowbotDir(knowbotDir);
    }

    private File readConfig() throws Exception {
        File configFile = new File(baseDir, "config.dct");

        // read the server-specific (as opposed to site-specific) configuration
        try {
            serverConfig.clear();
            if (configFile.exists()) serverConfig.readFromFile(configFile);
        } catch (Exception e) {
            logger.error("Error loadig config file", e);
            throw e;
        }
        logger.debug("Read from Config File");
        return configFile;
    }

    private void readStoredPasswords() {
        // read the stored passwords
        try {
            File storedPasswordFile = new File(baseDir, STORED_PASSWORDS_FILE);
            storedPasswords.clear();
            if (storedPasswordFile.exists()) storedPasswords.readFromFile(storedPasswordFile);
        } catch (Exception e) {
            logger.error("Error loading stored passwords", e);
        }
    }

    private void saveStoredPasswords() throws Exception {
        File storedPasswordFile = new File(baseDir, STORED_PASSWORDS_FILE);
        try {
            storedPasswords.writeToFile(storedPasswordFile);
        } catch (Exception e) {
            System.out.println("Error saving config file (" + storedPasswordFile.getAbsolutePath() + "): " + e);
            throw e;
        }
    }

    private void readNextID() throws StringEncodingException, IOException {
        nextIDFile = new File(baseDir, "next_id.dct");
        nextIDConfig.clear();
        if (nextIDFile.exists()) {
            nextIDConfig.readFromFile(nextIDFile);
        }
    }

    private void initializeAdminInfo() {
        this.serverAdmin = serverConfig.getStr(SERVER_ADMIN_KEY, null);

        // get the list of attributes that identify an owner/administrator for the object
        StreamVector ownerAtts = (StreamVector) serverConfig.get(OWNER_ATTRIBUTES_KEY);
        if (ownerAtts != null) {
            ownerAttributes = new String[ownerAtts.size()];
            int ownerIdx = 0;
            for (Object att : ownerAtts) {
                ownerAttributes[ownerIdx++] = (String) att;
            }
        } else {
            ownerAttributes = null;
        }

        StreamVector authDelAtts = (StreamVector) serverConfig.get(AUTH_DELEGATES_KEY);
        if (authDelAtts != null) {
            authorizationDelegateAttributes = new String[authDelAtts.size()];
            int ownerIdx = 0;
            for (Object att : authDelAtts) {
                authorizationDelegateAttributes[ownerIdx++] = (String) att;
            }
        } else {
            authorizationDelegateAttributes = null;
        }
    }

    private void initializeStorage() throws InstantiationException, IllegalAccessException, ClassNotFoundException, DOException, Exception {
        // initialize the storage and create the repository object if it doesn't already exist
        String storageClass = serverConfig.getStr(STORAGE_CLASS_KEY, "net.cnri.apps.doserver.HashDirectoryStorage");
        String storageDir = serverConfig.getStr(STORAGE_DIR_KEY, "storage");
        this.storage = (Storage) Class.forName(storageClass).newInstance();
        this.storage.initWithDirectory(this, new File(baseDir, storageDir));

        File txnDir = new File(baseDir, "txns");
        this.storage.initTransactionQueue(txnDir);

        if (!storage.doesObjectExist(serverID)) {
            storage.createObject(serverID, null, true);
        }

        // set version attribute
        HeaderSet atts = storage.getAttributes(serverID, null, null);
        String version = atts.getStringHeader(VERSION_ATT, null);
        if (!Version.version.equals(version)) {
            atts = new HeaderSet();
            atts.addHeader(VERSION_ATT, Version.version);
            storage.setAttributes(serverID, null, atts, false, 0);
        }
    }

    private void initializeAuthentication(PrivateKey serverKey) throws DOException, Exception, UnsupportedEncodingException {
        // initialize our authentication which is used for communicating with clients and
        // other servers
        this.myID = new PKAuthentication(serverID, serverKey);
        this.myID.setAutoRetrieveCredentials(false);

        // load my credentials directly from storage...
        StorageProxy myStorage = new ConcreteStorageProxy(storage, serverID, serverID, null);
        InputStream credIn = myStorage.getDataElement(StandardOperations.OBJ_CREDENTIALS_ELEMENT);

        if (credIn != null) {
            XTag xmlCertList;
            try {
                XParser xmlParser = new XParser();
                xmlCertList = xmlParser.parse(new InputStreamReader(credIn, "UTF8"), false);
            } finally {
                credIn.close();
            }
            ArrayList certs = new ArrayList();
            for (int i = 0; i < xmlCertList.getSubTagCount(); i++) {
                XTag subtag = xmlCertList.getSubTag(i);
                if (subtag.getName().equalsIgnoreCase("certificate")) {
                    X509Certificate cert = null;
                    try {
                        InputStream certIn = new ByteArrayInputStream(Util.encodeHexString(subtag.getStrSubTag("x509", "")));
                        cert = (X509Certificate) StandardOperations.getCertFactory().generateCertificate(certIn);
                        cert.checkValidity();
                        certs.add(cert);
                    } catch (Exception e) {
                        logger.error("Error reading cert", e);
                        continue;
                    }
                }
            }
            this.myID.setCredentials((java.security.cert.Certificate[]) certs.toArray(new java.security.cert.Certificate[certs.size()]));
        }

        this.hdlID = new PublicKeyAuthenticationInfo(Util.encodeString(serverID), PUBLIC_KEY_HDL_IDX, serverKey);
    }

    private void initializeKnowbots(PrivateKey serverKey) throws StringEncodingException, IOException, Exception {
        // initialize the knowbots that handle operations
        File knowbotDir = new File(baseDir, "knowbots");
        if (!knowbotDir.exists()) {
            setupKnowbotDir(knowbotDir);
        }

        SecurityManager originalSecurityManager = System.getSecurityManager();
        this.knowbotStation = new ExecutionStation(knowbotDir, "hdl:" + serverID, serverKey);
        System.setSecurityManager(originalSecurityManager);
    }

    public void setupKnowbotDir(File knowbotDir) throws StringEncodingException, IOException {
        if (!knowbotDir.exists()) {
            knowbotDir.mkdirs();
        }
        StreamTable kbConfig = new StreamTable();
        kbConfig.put("station_uri", "hdl:" + serverID);
        StreamVector trustedIDs = new StreamVector();
        trustedIDs.addElement("hdl:" + serverID);
        kbConfig.put("trusted_uris", trustedIDs);
        kbConfig.writeToFile(new File(knowbotDir, "config.dct"));
        // reset the permissions
        new File(knowbotDir, "permissions").delete();
    }

    private void initializeLoggers() throws Exception {
        // load up the loggers!
        File logDir = new File(baseDir, "logs");
        this.auditLog = new AuditLog(this, logDir);
        String logSaveIntervalStr = serverConfig.getStr(LOG_SAVE_INTERVAL_KEY, MONTHLY);
        int logSaveInterval;
        if (NEVER.equalsIgnoreCase(logSaveIntervalStr))
            logSaveInterval = ServerLog.ROTATE_NEVER;
        else if (DAILY.equalsIgnoreCase(logSaveIntervalStr))
            logSaveInterval = ServerLog.ROTATE_DAILY;
        else if (WEEKLY.equalsIgnoreCase(logSaveIntervalStr))
            logSaveInterval = ServerLog.ROTATE_WEEKLY;
        else logSaveInterval = ServerLog.ROTATE_MONTHLY;
        if (serverConfig.getBoolean(LOG_ACCESSES_KEY, true)) {
            this.accessLogger = new ServerLog(logDir, "access.log", false, logSaveInterval);
        }

        if (serverConfig.getBoolean(REDIRECT_STDERR_KEY, true)) {
            System.setErr(new PrintStream(new LoggerOutputStream(), true, "UTF-8"));
        }
        this.auditLogEnabled = serverConfig.getBoolean(ENABLE_AUDIT_LOGS_KEY, DEFAULT_ENABLE_AUDIT_LOG);
    }

    private void initializeReplicator() throws Exception {
        if (this.localServerID != null) {
            this.replicator = new ReplicationManager(this, storage);
        }
        if (this.replicator != null && serverConfig.getBoolean(DO_PULL_REPLICATION_KEY, DEFAULT_DO_PULL_REPLICATION)) {
            this.replicator.doPullReplication();
        }
        if (this.replicator != null && serverConfig.getBoolean(DO_PUSH_REPLICATION_KEY, DEFAULT_DO_PUSH_REPLICATION)) {
            this.replicator.doPushReplication();
        }
    }

    private X509Certificate buildCertificate(String id, PublicKey pubKey, PrivateKey privKey) {
        X509Certificate cert = null;
        File certFile = new File(getBaseFolder(), "serverCertificate.pem");
        if (certFile.exists()) {
            try {
                cert = X509HSCertificateGenerator.readCertAsPem(new InputStreamReader(new FileInputStream(certFile), "UTF-8"));
            } catch (Exception e) {
                e.printStackTrace();
                cert = null;
            }
        }
        if (cert != null) return cert;
        try {
            AutoSelfSignedKeyManager keyManager = new AutoSelfSignedKeyManager(id, pubKey, privKey);
            cert = keyManager.getCertificate();
            try {
                X509HSCertificateGenerator.writeCertAsPem(new OutputStreamWriter(new FileOutputStream(certFile), "UTF-8"), cert);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return cert;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Returns true if newly created objects should have all access permissions for */
    public boolean getAutoSetPermissionsForNewObjects() {
        return serverConfig.getBoolean(AUTO_SET_PERMISSIONS_FOR_NEW_OBJECTS, false);
    }

    /** Return delegation client that is used to verify group membership */
    public DelegationClient getDelegationClient() {
        return delegationClient;
    }

    public String[] getOwnerAttributes() {
        return ownerAttributes;
    }

    public String[] getAuthorizationDelegateAttributes() {
        return authorizationDelegateAttributes;
    }

    private class ServerMonitor extends Thread {
        File keepRunningFile;

        ServerMonitor() throws Exception {
            setDaemon(true);
            setName("Server Monitor");

            keepRunningFile = new File(baseDir, "delete_this_to_stop_server");
            keepRunningFile.createNewFile();
        }

        public void run() {
            while (!shutdown) {
                try {
                    if (!keepRunningFile.exists()) {
                        shutdown();
                        System.exit(0);
                    }

                    Thread.sleep(1000);
                } catch (Throwable t) {
                }
            }
            if (!restart) {
                keepRunningFile.delete();
            }
        }
    }

    public StreamTable getConfig() {
        return serverConfig;
    }
    
    /** Writes a new serverConfig StreamTable to config.dct. Not that this does not change the running configuration of the server
     * it is necessart to restart the server for the new configuration to take effect.**/
    public void writeNewConfigToFile(StreamTable newServerConfig) throws Exception {
        File configDctFile = new File(baseDir, "config.dct");
        try {
            newServerConfig.writeToFile(configDctFile);
        } catch (Exception e) {
            System.out.println("Error saving config file (" + configDctFile.getAbsolutePath() + "): " + e);
            throw e;
        }
    }

    /** Get an iterator over the set of server configuration keys */
    public Iterator getConfigKeys() {
        return serverConfig.keySet().iterator();
    }

    /** Get the value from the configuration table that is associated with the given key */
    public String getConfigVal(String configKey) {
        return serverConfig.getStr(configKey);
    }

    /**
     * Get the value from the configuration table that is associated with the given key. If no value is associated with the given key then return
     * defaultValue
     */
    public String getConfigVal(String configKey, String defaultValue) {
        return serverConfig.getStr(configKey, defaultValue);
    }

    /** Associate the given value with the given key in the configuration table */
    public void setConfigVal(String configKey, String configVal) {
        serverConfig.put(configKey, configVal);
    }

    /** Get password stored in a local file rather than the handle system */
    public String getStoredPasswordForUser(String userID) {
        return storedPasswords.getStr(userID);
    }

    /** Sets the password for the specified user in a local file */
    public void setStoredPasswordForUser(String user, String password) throws Exception {
        storedPasswords.put(user, password);
        saveStoredPasswords();
    }

    /**
     * Return the folder under which all of this server's configuration and other components are located.
     */
    public File getBaseFolder() {
        return this.baseDir;
    }

    /** Add the given knowbot to the set of operators for this repository */
    /*
     * public final void addOperatorElement(String knowbotElement, InputStream knowbotStream, Signature serverSig) throws Exception { // start a
     * thread to sign the agent and stream it to an output stream that // will be forwarded to the storage. PipedOutputStream pout = new
     * PipedOutputStream(); PipedInputStream pin = new PipedInputStream(pout); AgentSignerThread ast = new AgentSignerThread("hdl:"+serverID,
     * serverSig, opsBotIn, pout); ast.start(); storage.storeDataElement(serverID, knowbotElement, pin, true, false); }
     */

    public void addOperator(String knowbotID, InputStream knowbotLoc) throws Exception {
        this.configuration.addKnowbot(KNOWBOT_ELEMENT_PREFIX + knowbotID, knowbotLoc);
    }

    /**
     * Returns the identifier for this server
     */
    public final String getServerID() {
        return serverID;
    }

    /** Returns a direct reference to the object storage */
    public Storage getStorage() {
        return storage;
    }

    /**
     * Gets the next object ID in the sequence. This object ID should not already exist.
     */
    synchronized public final String getNextObjectID() throws Exception {
        String prefix = serverConfig.getStr(AUTO_OBJECT_PREFIX_KEY, null);
        if (prefix == null) prefix = getServerID() + "." + localServerID + "-";

        long nextNum = nextIDConfig.getLong("nextid", 0);
        String nextObjID = null;
        while (true) {
            nextObjID = prefix + nextNum;
            if (!storage.doesObjectExist(nextObjID)) break;
            nextNum++;
        }

        nextIDConfig.put("nextid", String.valueOf(nextNum + 1));
        nextIDConfig.writeToFile(nextIDFile);
        return prefix + nextNum;
    }

    /** Returns the transaction queue for this server */
    public final AbstractTransactionQueue getTxnQueue() {
        return storage.getTransactionQueue();
    }

    /**
     * Returns true if the given DOServerInfo object refers to this server.
     */
    public final boolean isThisServer(String aLocalServerID) throws Exception {
        if (aLocalServerID == null) return false;
        return aLocalServerID.equals(localServerID);
    }

    /**
     * Returns the configuration object for this server
     */
    final Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Returns the Signature object that can be used to sign data that is endorsed by this repository.
     */
    public Signature getSigner() {
        return knowbotStation.getSigner();
    }

    /** Return the knowbot/operator registry in which operators and other services are registered */
    public CollaborationHub getServiceRegistry() {
        return knowbotStation.getCollaborationHub();
    }

    /**
     * Set the given key-value mapping in the server-level table that knowbots can use to communicate.
     */
    public void setKnowbotMapping(String key, Object value) {
        this.knowbotStation.getCollaborationHub().registerService(key, key, value, null);
    }

    /**
     * Returns the DOAuthentication that identifies this server.
     */
    public DOAuthentication getAuth() {
        return this.myID;
    }

    /** Return the Authorizer that determines which operations are allowed by whom */
    public Authorizer getAuthorizer() {
        return this.authorizer;
    }

    /**
     * Return the server administrator (ie super-user) who can perform any operation on any object. Returns null if
     */
    public String getServerAdmin() {
        return serverAdmin;
    }

    /** Returns the public key of this server */
    public PublicKey getPublicKey() {
        return myPublicKey;
    }
    
    /** Returns the private key of this server */
    public PrivateKey getPrivateKey() {
        return myPrivateKey;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    /**
     * Removes all currently running knowbots. This is usually followed by calls to loadServiceBot(InputStream) to load replacement knowbots.
     */
    void clearServiceBots() {
        java.util.Enumeration agents = this.knowbotStation.getCurrentAgents();
        while (agents.hasMoreElements()) {
            try {
                Object agent = agents.nextElement();
                ((KnowbotWrapper) agent).stop();
            } catch (Throwable t) {
                // logger.error("Error stopping agent",t);
            }
        }
        if (!builtInKnowbotsLoaded) {
            // register the built-in audit operator
            this.knowbotStation.getCollaborationHub().registerService("Audit Operator", "net.cnri.apps.doserver.audit", new AuditHookImpl(auditLog), null);

            // register the external interface to allow remote admin operations
            this.knowbotStation.getCollaborationHub().registerService("External Admin Interface", "net.cnri.apps.doserver.admin", new ExternalInterface(this), null);
            this.knowbotStation.getCollaborationHub().registerService("Standard Operations", "net.cnri.apps.doserver.standardops", new StandardOperations(this, this.delegationClient), null);
            this.knowbotStation.getCollaborationHub().registerService("Group Operations", "net.cnri.apps.doserver.groupops", new ListElementDelegationOperations(), null);

            String indexBuilderClassName = getConfigVal(LuceneIndexer.INDEX_BUILDER_CLASS_KEY);
            try {
                if (indexBuilderClassName != null) {
                    IndexBuilder indexBuilder = (IndexBuilder) Class.forName(indexBuilderClassName, true, Thread.currentThread().getContextClassLoader()).getConstructor().newInstance();
                    registerIndexBuilder(indexBuilder);
                }
            } catch (Exception e) {
                logger.error("Error loading repository indexer", e);
            }

            if (replicator != null) {
                // register the replication operations for other servers to replicate this one
                DOOperation replOperator = replicator.getOperator();
                if (replOperator != null) {
                    this.knowbotStation.getCollaborationHub().registerService("Replication Hooks", "net.cnri.apps.doserver.replication", replOperator, null);
                }
            }
            builtInKnowbotsLoaded = true;
        }
    }

    private boolean builtInKnowbotsLoaded = false;

    public void registerDefaultIndexBuilder() {
        try {
            IndexBuilder indexBuilder = new DefaultIndexBuilder();
            registerIndexBuilder(indexBuilder);
        } catch (Exception e) {
            logger.error("Error loading repository indexer", e);
        }
    }

    public void registerIndexBuilder(IndexBuilder indexBuilder) throws Exception {
        String serviceName = "Lucene Indexer";
        String ontology = "net.cnri.knowbots.lucene.Main";
        if (isKnowbotExists(serviceName, ontology, LuceneIndexer.class)) {
            throw new Exception("Cannot registerIndexBuilder. Knowbox " + serviceName + " already exists.");
        } else {
            indexer = new LuceneIndexer(this, indexBuilder);
            this.knowbotStation.getCollaborationHub().registerService(serviceName, ontology, indexer, null);
        }
    }

    public boolean isIndexBuilderRegistered() {
        String serviceName = "Lucene Indexer";
        String ontology = "net.cnri.knowbots.lucene.Main";
        return isKnowbotExists(serviceName, ontology, LuceneIndexer.class);
    }

    private boolean isKnowbotExists(String serviceName, String ontology, Class objectClass) {
        CollaborationHub hub = this.knowbotStation.getCollaborationHub();
        Object[] results = hub.lookupEntity(serviceName, ontology, objectClass);
        if (results.length > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates the given handle, if necessary, and sets the object server for it to this object server.
     */
    public void registerHandle(String newHandle, String objectName) throws DOException {
        if (!serverConfig.getBoolean(REGISTER_HANDLES_KEY, DEFAULT_REGISTER_IDS)) return;

        byte repoHandle[] = Util.encodeString(myID.getID());
        Resolver resolver = DOClient.getResolver();
        HandleValue values[] = null;
        try {
            values = resolver.resolveHandle(newHandle);
        } catch (HandleException e) {
            // avoid stupid exception being thrown in resolveHandle when a
            // handle not found message is received
            if (e.getCode() != AbstractResponse.RC_HANDLE_NOT_FOUND) throw new DOException(DOException.INTERNAL_ERROR, "Unable to locate handle: " + e);
        }

        byte hdlBytes[] = Util.encodeString(newHandle);
        if (values == null) { // handle does not exist?
            AdminRecord adminRec = new AdminRecord(hdlID.getUserIdHandle(), hdlID.getUserIdIndex(), false, // add handle
                            true, // delete handle
                            false, // add NA
                            false, // delete NA
                            true, // read value
                            true, // modify value
                            true, // remove value
                            true, // add value
                            true, // modify admin
                            true, // remove admin
                            true, // add admin
                            false // list handles
            );

            HandleValue hdlValues[] = null;
            if (objectName == null) {
                hdlValues = new HandleValue[] { new HandleValue(100, Common.ADMIN_TYPE, null), new HandleValue(1, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), repoHandle) };
            } else {
                hdlValues = new HandleValue[] { new HandleValue(100, Common.ADMIN_TYPE, null), new HandleValue(1, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), repoHandle),
                                new HandleValue(2, Util.encodeString(DOConstants.OBJECT_NAME_HDL_TYPE), Util.encodeString(objectName)) };
            }
            hdlValues[0].setData(Encoder.encodeAdminRecord(adminRec));
            CreateHandleRequest createRq = new CreateHandleRequest(hdlBytes, hdlValues, hdlID);
            try {
                AbstractResponse rs = resolver.getResolver().processRequest(createRq);
                if (rs != null && rs.responseCode == AbstractMessage.RC_SUCCESS) {
                    return;
                } else {
                    throw new DOException(DOException.INTERNAL_ERROR, "Error registering handle: " + rs);
                }
            } catch (HandleException e) {
                throw new DOException(DOException.INTERNAL_ERROR, "Unable to register handle: " + e);
            }
        } else { // handle exists, modify if necessary/possible

            byte objSvrType[] = Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE);
            // check to see if it already points to us...
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && values[i].hasType(objSvrType)) {
                    if (Util.decodeString(values[i].getData()).equalsIgnoreCase(myID.getID())) {
                        // we are already listed as a registry server for this object
                        return;
                    }
                }
            }

            int newIndex = 0;
            boolean duplicateIndex = true;
            while (duplicateIndex) {
                newIndex++;
                boolean foundDupe = false;
                for (int i = 0; i < values.length; i++) {
                    if (values[i].getIndex() == newIndex) foundDupe = true;
                }
                if (!foundDupe) duplicateIndex = false;
            }

            // it doesn't already point to us... add a handle value to refer to this server
            HandleValue hdlValues[] = { new HandleValue(newIndex, Util.encodeString(DOConstants.OBJECT_SERVER_HDL_TYPE), repoHandle) };
            AddValueRequest addRq = new AddValueRequest(hdlBytes, hdlValues, hdlID);
            try {
                AbstractResponse rs = resolver.getResolver().processRequest(addRq);
                if (rs != null && rs.responseCode == AbstractMessage.RC_SUCCESS) {
                    return;
                } else {
                    throw new DOException(DOException.INTERNAL_ERROR, "Error updating handle: " + rs);
                }
            } catch (HandleException e) {
                throw new DOException(DOException.INTERNAL_ERROR, "Unable to register handle: " + e);
            }
        }
    }

    /**
     * Load a knowbot from the given input stream. This knowbot is simply loaded into the service station and can be used to authenticate users,
     * authorize users, or perform operations on digital objects.
     */
    void loadServiceBot(InputStream in) throws Exception {
        if (in == null) throw new NullPointerException();
        this.knowbotStation.acceptAgent(in);
    }

    // -------------------------------------------------------------------------------
    public void serveRequests() {
        if (connPool != null) {
            // TODO: Clear existing handler pool
        }

        // get the specific IP address to bind to, if any.
        String bindAddressStr = serverConfig.getStr(BIND_KEY);

        logger.debug("Starting DOP connection handlers: ");
        connPool = new GrowBeforeQueueThreadPoolExecutor(NUM_PRELOADED_CONNECTION_HANDLERS, MAX_CONNECTION_HANDLERS, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
        logger.debug("Done.");

        logger.debug("Starting BOP operation handlers: ");
        opHandlerPool = new GrowBeforeQueueThreadPoolExecutor(NUM_PRELOADED_OP_HANDLERS, MAX_OP_HANDLERS, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>());
        logger.debug("Done.");

        Thread dopListener = null;
        Thread sslListener = null;

        try {
            keepServing = true;

            // setup the DOP listener socket
            if (bindAddressStr == null) {
                listenAddress = null;
            } else {
                listenAddress = InetAddress.getByName(String.valueOf(bindAddressStr));
            }

            int dopPort = serverConfig.getInt(PORT_KEY, DEFAULT_PORT);
            socket = null;
            if (dopPort > 0) {
                logger.info("Initializing DOP interface on port " + dopPort);
                System.out.println("Initializing DOP interface on port " + dopPort);
                if (listenAddress != null) {
                    socket = new ServerSocket(dopPort, DOP_LISTEN_BACKLOG, listenAddress);
                } else {
                    socket = new ServerSocket(dopPort, DOP_LISTEN_BACKLOG);
                }
                dopListener = new DOPListener();
                dopListener.start();
            }

            // setup the DOP-over-SSL listener socket
            int dopSSLPort = serverConfig.getInt(SSL_PORT_KEY, DEFAULT_SSL_PORT);
            sslSocket = null;
            if (dopSSLPort > 0) {
                logger.info("Initializing DOP-SSL interface on port " + dopSSLPort);
                System.out.println("Initializing DOP-SSL interface on port " + dopSSLPort);
                if (listenAddress != null) {
                    sslSocket = (SSLServerSocket) sslSocketFactory.createServerSocket(dopSSLPort, DOPSSL_LISTEN_BACKLOG, listenAddress);
                } else {
                    sslSocket = (SSLServerSocket) sslSocketFactory.createServerSocket(dopSSLPort, DOPSSL_LISTEN_BACKLOG);
                }
                sslListener = new SSLListener();
                sslListener.start();
            }
            startEmbeddedJettyHttpServer();
            if (!isIndexBuilderRegistered() && !serverConfig.getBoolean("disable_indexing", false)) {
                registerDefaultIndexBuilder();
            }

            System.out.println("Startup complete.");
            if (dopListener != null) dopListener.join();
            if (sslListener != null) sslListener.join();
            if (embeddedJetty != null) embeddedJetty.join();
        } catch (Exception e) {
            logger.error("Exception in serveRequests", e);
        } finally {
            keepServing = false;
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                }
                socket = null;
            }
            if (sslSocket != null) {
                try {
                    sslSocket.close();
                } catch (Exception e) {
                }
                sslSocket = null;
            }
            if (embeddedJetty != null) {
                try {
                    embeddedJetty.stopHttpServer();
                } catch (Exception e) {
                }
                embeddedJetty = null;
            }
            if (restart) {
                try {
                    restart();
                } catch (IOException e) {
                    System.err.println("Error restarting server: " + e);
                }
            }
        }
    }

    private void startEmbeddedJettyHttpServer() {
        EmbeddedJettyConfig jettyConfig = new EmbeddedJettyConfig();
        jettyConfig.setEnableDefaultHttpConfig(serverConfig.getBoolean("http_enable_default_config", true));
        jettyConfig.addContextAttribute("net.cnri.apps.doserver.Main", this);
        jettyConfig.addSystemClass("net.cnri.apps.doserver.");
        jettyConfig.addSystemClass("net.cnri.dobj.");
        jettyConfig.addSystemClass("net.cnri.do_api.");
        jettyConfig.addSystemClass("net.handle.hdllib.");
        jettyConfig.addSystemClass("com.google.gson.");
        jettyConfig.setBaseDir(baseDir);
        jettyConfig.setResolver(DOClient.getResolver().getResolver());
        int httpPort = serverConfig.getInt(HTTP_PORT_KEY, DEFAULT_HTTP_PORT);
        int httpsPort = serverConfig.getInt(HTTPS_PORT_KEY, DEFAULT_HTTPS_PORT);
        if (httpPort > 0 || httpsPort > 0) {
            ConnectorConfig httpConnectorConfig = null;
            if (httpPort > 0) {
                System.out.println("Initializing HTTP interface on port " + httpPort);
                httpConnectorConfig = new ConnectorConfig();
                httpConnectorConfig.setHttps(false);
                httpConnectorConfig.setListenAddress(listenAddress);
                httpConnectorConfig.setPort(httpPort);
                httpConnectorConfig.setHttpOnly(true);
            }
            ConnectorConfig httpsConnectorConfig = null;
            if (httpsPort > 0) {
                System.out.println("Initializing HTTPS interface on port " + httpsPort);
                httpsConnectorConfig = new ConnectorConfig();
                httpsConnectorConfig.setHttps(true);
                httpsConnectorConfig.setListenAddress(listenAddress);
                httpsConnectorConfig.setPort(httpsPort);
                if (httpConnectorConfig != null) httpConnectorConfig.setRedirectPort(httpsPort);
                boolean useSelfSignedCert = serverConfig.getBoolean("https_default_self_signed_cert", serverConfig.getStr("https_keystore_file", null) == null);
                if (useSelfSignedCert) {
                    httpsConnectorConfig.setHttpsUseSelfSignedCert(true);
                    if (certificate != null) httpsConnectorConfig.setHttpsCertificate(certificate);
                    httpsConnectorConfig.setHttpsId(this.myID.getID());
                    httpsConnectorConfig.setHttpsPubKey(this.myPublicKey);
                    httpsConnectorConfig.setHttpsPrivKey(this.myPrivateKey);
                } else {
                    httpsConnectorConfig.setHttpsUseSelfSignedCert(false);
                    httpsConnectorConfig.setHttpsKeyStorePassword(serverConfig.getStr("https_keystore_password", null));
                    httpsConnectorConfig.setHttpsKeyPassword(serverConfig.getStr("https_key_password", null));
                    httpsConnectorConfig.setHttpsKeyStoreFile(serverConfig.getStr("https_keystore_file", null));
                    httpsConnectorConfig.setHttpsAlias(serverConfig.getStr("https_alias", null));
                }
                httpsConnectorConfig.setHttpsClientAuth(serverConfig.getStr("https_client_auth", "false"));
            }
            if (httpConnectorConfig != null) jettyConfig.addConnector(httpConnectorConfig);
            if (httpsConnectorConfig != null) jettyConfig.addConnector(httpsConnectorConfig);
            if (serverConfig.getBoolean("http_enable_root_web_app", true)) {
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                HTTPConnectionHandler httpHandler = new HTTPConnectionHandler(thisServer);
                httpHandler.setupServletHolder(context);
                context.addFilter(org.eclipse.jetty.servlets.GzipFilter.class, "/*", null);
                jettyConfig.addDefaultHandler(context);
            }
            embeddedJetty = new EmbeddedJetty(jettyConfig);
            try {
                embeddedJetty.setUpHttpServer();
                embeddedJetty.startPriorityDeploymentManager();
                embeddedJetty.startHttpServer();
            } catch (Exception e) {
                logger.error("Error starting Jetty servlet container", e);
                e.printStackTrace(System.out);
                System.exit(1);
            }
        }
    }

    public void addWarToHttpServer(File war, String contextPath) throws Exception {
        embeddedJetty.addWebApp(war, contextPath);
    }

    private class DOPListener extends Thread {
        public void run() {
            while (keepServing && socket != null) {
                try {
                    Socket clientSock = socket.accept();
                    connPool.execute(new ConnectionHandler(clientSock));
                } catch (Exception e) {
                    if (keepServing) logger.error("Error accepting DOP connection", e);
                }
            }
        }
    }

    private class SSLListener extends Thread {

        public void run() {
            while (keepServing && sslSocket != null) {
                try {
                    Socket clientSock = sslSocket.accept();
                    connPool.execute(new ConnectionHandler(clientSock));
                } catch (Exception e) {
                    if (keepServing) {
                        logger.error("Error accepting DOP/SSL connection", e);
                    }
                }
            }
        }
    }

    public boolean isListening() {
        try {
            if (socket != null) return socket.isBound();
        } catch (Exception e) {
            logger.error("error checking socket for listen status", e);
        }
        return false;
    }

    private boolean shutdown;

    public static void shutdown(String[] args) throws Exception {
        runningMain.shutdown();
    }

    public synchronized void shutdown() throws Exception {
        if (shutdown) return;
        logger.info("Shutting down...");
        stopServing();

        if (replicator != null) replicator.shutdown();
        replicator = null;

        java.util.Enumeration en = knowbotStation.getCurrentAgents();
        while (en.hasMoreElements()) {
            try {
                Object agent = en.nextElement();
                ((KnowbotWrapper) agent).stop();
            } catch (Throwable t) {
                logger.error("Error stopping agent", t);
            }
        }
        if (indexer != null) {
            indexer.shutdown();
        }

        storage.getTransactionQueue().shutdown();
        storage.close();

        if (accessLogger != null) accessLogger.shutdown();
        shutdown = true;
    }

    public synchronized void shutdownAndRestart() throws Exception {
        if (restart) {
            return;
        } else {
            restart = true;
            shutdown();
        }
    }

    public boolean isRestarting() {
        return restart;
    }

    private void restart() throws IOException {
        String startServerCommand = getStartServerCommand();
        System.out.println("running: " + startServerCommand);
        Runtime.getRuntime().exec(startServerCommand);
    }

    private String getStartServerCommand() {
        StringBuilder cmd = new StringBuilder();
        cmd.append(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java ");
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            cmd.append(jvmArg + " ");
        }
        cmd.append("-cp ").append(ManagementFactory.getRuntimeMXBean().getClassPath()).append(" ");
        cmd.append(Main.class.getName()).append(" ");
        for (String arg : args) {
            cmd.append(arg).append(" ");
        }
        String command = cmd.toString();
        return command;
    }

    public synchronized void stopServing() {
        keepServing = false;
        if (embeddedJetty != null) {
            try {
                embeddedJetty.stopHttpServer();
            } catch (Exception e) {
            }
            embeddedJetty = null;
        }
        if (connPool != null) {
            connPool.shutdown();
        }
        if (opHandlerPool != null) {
            opHandlerPool.shutdown();
        }
        try {
            opHandlerPool.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            // ignore
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
            }
            socket = null;
        }
        if (sslSocket != null) {
            try {
                sslSocket.close();
            } catch (Exception e) {
            }
            sslSocket = null;
        }
    }

    /**
     * This method can be called by an outside object when this repository is run in embedded mode. No built-in authentication is performed and the
     * operation is invoked with the caller set to the server itself.
     */
    public void performOperation(String objectID, String operationID, HeaderSet params, InputStream in, OutputStream out) throws Exception {
        if (params == null) params = new HeaderSet();
        performOperation(new RequestContext(null, getServerID(), operationID, objectID, params), in, out);
    }

    public void performOperation(DOServerOperationContext context, InputStream in, OutputStream out) throws Exception {
        StorageProxy storage = context.getStorage();
        if (!storage.doesObjectExist()) {
            Authorizer.AuthorizationInfo auth = authorizer.forwardingIsAllowed(context, null);
            boolean canForward = auth.canForwardOperation();
            // logger.log(context.getLogEntry(dateFmt, canForward));
            if (!canForward) {
                HeaderSet response = new HeaderSet("response");
                response.addHeader("status", "error");
                response.addHeader("message", "Object " + context.getTargetObjectID() + " not located here");
                response.addHeader("code", DOException.NO_SUCH_OBJECT_ERROR);
                response.writeHeaders(out);
                out.close();
                return;
            }

            performRemoteOperation(context, auth, in, out);
        } else {
            performLocalOperation(context, false, in, out);
        }
    }

    /**
     * Forward the operation to the repository that is responsible for the object, if the client is permitted to do so.
     */
    public void performRemoteOperation(DOServerOperationContext context, Authorizer.AuthorizationInfo auth, InputStream in, OutputStream out) throws Exception {
        DOClient fwdClient = auth.useRepoID() ? doClient : context.getDOClientWithForwardedAuthentication(context.getCallerID());

        StreamPair io = null;
        try {
            // attempt to perform the operation using the
            io = fwdClient.performOperation(auth.getEndpointRepositoryID(), context.getTargetObjectID(), context.getOperationID(), context.getOperationHeaders());
        } catch (DOException e) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", e.getMessage());
            response.addHeader("code", e.getErrorCode());
            response.writeHeaders(out);
            out.close();
            return;
        }

        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "success");
        response.writeHeaders(out);
        out.flush();

        // spawn another thread to deal with the outbound bytes
        // TODO: Replace this with an non-blocking write, perhaps using Channels
        Thread outboundForwarder = new OutboundForwarder(in, io.getOutputStream());
        outboundForwarder.start();

        // forward the incoming bytes in this thread
        IOForwarder.forwardStream(io.getInputStream(), out);
        out.close();

        // wait for the outbound bytes to finish
        outboundForwarder.join();
        return;
    }

    private class OutboundForwarder extends Thread {
        private InputStream in;
        private OutputStream out;

        OutboundForwarder(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        public void run() {
            try {
                IOForwarder.forwardStream(in, out);
                out.close();
            } catch (Throwable t) {
                logger.error("error reading forwarded stream", t);
                try {
                    out.close();
                } catch (Throwable t2) {
                }
            }
        }
    }

    /**
     * Invoke the given operation in the repository after checking that the client has permission to invoke the given operation
     */
    public void performLocalOperation(DOServerOperationContext context, boolean skipAuthorization, InputStream in, OutputStream out) throws Exception {
        boolean authorized = skipAuthorization || authorizer.operationIsAllowed(context);
        if (accessLogger != null) accessLogger.log(context.getLogEntry(dateFmt, authorized));
        if (!authorized) {
            in.close();
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", "operation not allowed");
            response.addHeader("code", DOException.PERMISSION_DENIED_ERROR);
            response.writeHeaders(out);
            out.close();
            return;
        }

        String opID = context.getOperationID();
        Object operators[] = knowbotStation.getCollaborationHub().lookupEntity(null, null, DOOperation.class);
        if (opID.equalsIgnoreCase(DOConstants.LIST_OPERATIONS_OP_ID)) {
            in.close();
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "success");
            response.writeHeaders(out);

            OutputStreamWriter w = new OutputStreamWriter(out, "UTF8");
            Hashtable alreadySentOps = new Hashtable();
            try {
                w.write(DOConstants.LIST_OPERATIONS_OP_ID);
                w.write("\n");
                for (int i = 0; operators != null && i < operators.length; i++) {
                    if (operators[i] == null) continue;
                    String ops[] = ((DOOperation) operators[i]).listOperations(context);
                    if (ops == null) continue;
                    for (int j = 0; j < ops.length; j++) {
                        if (ops[j] == null) continue;
                        if (!alreadySentOps.containsKey(ops[j].toLowerCase())) {
                            w.write(ops[j]);
                            w.write("\n");
                            alreadySentOps.put(ops[j].toLowerCase(), "");
                        }
                    }
                }
            } finally {
                w.flush();
                out.close();
            }
        } else {
            // find a knowbot to handle this operation
            // a standard operation is being invoked... find an operator to handle it
            DOOperation op = null;
            for (int i = 0; operators != null && i < operators.length; i++) {
                DOOperation tmpOp = (DOOperation) operators[i];
                if (tmpOp.canHandleOperation(context)) {
                    op = tmpOp;
                    break;
                }
            }

            if (op == null) {
                HeaderSet response = new HeaderSet("response");
                response.addHeader("status", "error");
                response.addHeader("message", "Operation '" + opID + "' Not Available");
                response.addHeader("code", DOException.OPERATION_NOT_AVAILABLE);
                response.writeHeaders(out);
                out.close();
                return;
            }

            op.performOperation(context, in, out);
        }
    }

    private class ConnectionHandler implements Runnable, DOConnectionListener {
        private final Socket socket;
        private DOServerConnection doServer = null;

        ConnectionHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                Thread.currentThread().setName("Connection Handler: " + socket.getInetAddress().getHostAddress());
                this.doServer = new DOServerConnection(myID, socket, this, Main.this.delegationClient);

                // The connection handler thread now just ends after the connection is set up. The DOConnectionManager takes over.
                // while (doServer.isOpen()) {
                // // TODO: set a time-out for connections that have not had
                // // activity in a certain time period to be closed
                // Thread.sleep(10000);
                // }
            } catch (Exception e) {
                logger.error("protocol/connection error; closing connection", e);
                try {
                    if (doServer != null) doServer.close();
                } catch (Throwable t) {
                }
                doServer = null;
                try {
                    if (socket != null) socket.close();
                } catch (Throwable t) {
                    logger.error("Error closing server socket", t);
                }
            } finally {
                Thread.currentThread().setName("Unused Connection Handler");
            }
        }

        /**
         * Acquires an OperationRunner from the pool to process an operation over the given newly created channel.
         */
        public void channelCreated(StreamPair pair) {
            // get a handler to take care of the operation on the new channel
            opHandlerPool.execute(new OperationRunner(doServer, pair));
        }
    }

    /**
     * This class is responsible for converting the incoming request message in an operation invocation.
     */
    private class OperationRunner implements Runnable {
        private final DOServerConnection doServer;
        private final StreamPair pair;

        public OperationRunner(DOServerConnection doServer, StreamPair pair) {
            this.doServer = doServer;
            this.pair = pair;
        }

        public void run() {
            // perform the operation!
            InputStream in = null;
            OutputStream out = null;
            RequestContext reqContext = null;
            try {
                Thread.currentThread().setName("Operation Handler: initializing");

                in = pair.getInputStream();
                out = pair.getOutputStream();
                HeaderSet headers = new HeaderSet();
                headers.readHeaders(in);

                Thread.currentThread().setName("Operation Handler: " + headers);
                String operationID = headers.getStringHeader("operationid", "");
                String callerID = headers.getStringHeader("callerid", "");
                String objectID = headers.getStringHeader("objectid", "");
                HeaderSet parameters = headers.getHeaderSubset("params");
                if (auditLogEnabled) {
                    StreamPair auditPair = auditLog.recordOperation(headers, in, out);
                    in = auditPair.getInputStream();
                    out = auditPair.getOutputStream();
                }
                reqContext = new RequestContext(doServer, callerID, operationID, objectID, parameters);
                Thread.currentThread().setName("Operation Handler: " + reqContext);
                pair.setName(String.valueOf(reqContext));

                performOperation(reqContext, in, out);
            } catch (Exception e) {
                logger.error("Exception in processOperation", e);
            } finally {
                try {
                    out.close();
                } catch (Throwable e) {
                }
                try {
                    in.close();
                } catch (Throwable e) {
                }
                try {
                    if (pair != null) pair.close();
                } catch (Throwable t) {
                }
                Thread.currentThread().setName("Unused Operation Handler");
            }
        }
    }

    private class RequestContext extends DOServerOperationContext {
        private String callerID;
        private String opID;
        private String objectID;
        private HeaderSet params;
        private ConcreteStorageProxy storageProxy;
        private DOServerConnection doServer;
        private StringBuffer logEntryStr = new StringBuffer();

        private RequestContext(DOServerConnection doServer, String callerID, String opID, String objectID, HeaderSet params) {
            this.callerID = callerID;
            this.opID = opID;
            this.objectID = objectID;
            this.params = params == null ? new HeaderSet() : params;
            this.doServer = doServer;
            this.storageProxy = null;
        }

        public InetAddress getClientAddress() {
            if (doServer == null) return null;
            return doServer.getSocket().getInetAddress();
        }

        /**
         * Requests verification of the callers identity. Returns true iff the caller's identity has been verified.
         */
        public synchronized boolean authenticateCaller() {
            if (doServer == null) return true;
            if (callerID.equals(doServer.getAuthID())) return true;
            boolean authenticated = false;
            try {
                PublicKey storedPublicKey = null;
                if (callerID.equals(serverID)) storedPublicKey = myPublicKey;
                authenticated = doServer.authenticateClient(callerID, getStoredPasswordForUser(callerID), storedPublicKey);
            } catch (DOException e) {
                logger.error("Error authenticating", e);
            }
            return authenticated;
        }

        /**
         * Returns a list of unverified IDs that the client claims as credentials. Note: These IDs are not yet verified and the caller should call
         * authenticateCredential() with any credential IDs for whom they assign any meaning. Note2: This should only be called after
         * authenticateClient returns.
         */
        public String[] getCredentialIDs() {
            if (doServer == null) return null;
            return doServer.getCredentialIDs();
        }

        /**
         * Verify that this client has been granted a credential by the identified entity. The given credentialID is expected to have come from the
         * list returned by getCredentialIDs(). Returns true iff a verified credential from credentialID was granted to this client.
         */
        public boolean authenticateCredential(String credentialID) {
            if (doServer == null) return true;
            return doServer.authenticateCredential(credentialID);
        }

        /**
         * Performs the specified operation with the identity of the caller. If the serverID is null then the DO client that performs this request
         * will resolve the objectID to find the server.
         */
        public void performOperation(String serverID, String objectID, String operationID, HeaderSet params, InputStream input, OutputStream output) throws DOException {
            RequestContext derivedCtx = new RequestContext(doServer, callerID, operationID, objectID, params);
            try {
                thisServer.performOperation(derivedCtx, input, output);
            } catch (Exception e) {
                logger.error("Exception in performOperation", e);
                if (e instanceof DOException) throw (DOException) e;
                throw new DOException(DOException.SERVER_ERROR, "Error invoking derived operation: " + e);
            }
        }

        /**
         * Returns the identity of the caller.
         */
        public String getCallerID() {
            return callerID;
        }

        /**
         * Returns the operation that the caller attempted to invoke.
         */
        public String getOperationID() {
            return opID;
        }

        /**
         * Returns the object on which the caller is invoke the operation.
         */
        public String getTargetObjectID() {
            return objectID;
        }

        /**
         * Returns the identity of this server.
         */
        public String getServerID() {
            return serverID;
        }

        /**
         * Returns the set of headers that were included with the operation request
         */
        public HeaderSet getOperationHeaders() {
            return params;
        }

        /**
         * Returns on object that allows operators to access the storage system for the current object.
         */
        public StorageProxy getStorage() {
            // only construct the storage proxy if it is requested
            synchronized (this) {
                if (storageProxy == null) {
                    HeaderSet txnMetadata = new HeaderSet();
                    txnMetadata.addHeader("callerid", callerID);
                    txnMetadata.addHeader("operationid", opID);
                    txnMetadata.addHeader("params", params);
                    storageProxy = new ConcreteStorageProxy(storage, myID.getID(), objectID, txnMetadata);
                }
            }
            return storageProxy;
        }

        /**
         * Inserts an object into the connection-level information table. This causes subsequent calls to getConnectionMapping() on the same
         * connection (but not necessarily the same operation) with the same key to return the given data value.
         */
        public void setConnectionMapping(Object mappingKey, Object mappingData) {
            if (doServer == null) return;
            this.doServer.setConnectionMapping(mappingKey, mappingData);
        }

        /**
         * Returns the object from the connection-level information table that had previously been used as the mappingData parameter for a call to
         * setConnectionMapping with the given mappingKey. If no such object had been inserted into the table then this will return null.
         */
        public Object getConnectionMapping(Object mappingKey) {
            if (doServer == null) return null;
            return this.doServer.getConnectionMapping(mappingKey);
        }

        /**
         * Return a DOClient instance that forwards any authentication challenges back to the DOP client on the other end of this connection.
         */
        public synchronized DOClient getDOClientWithForwardedAuthentication(String clientID) {
            String mappingKey = "doclient-" + clientID;
            DOClient client = (DOClient) doServer.getConnectionMapping(mappingKey);
            if (client != null) return client;

            client = new DOClient(new ProxiedAuthentication(clientID, doServer));
            doServer.setConnectionMapping(mappingKey, client);
            return client;
        }

    }
}
