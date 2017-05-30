/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.*;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.cnri.doregistrytools.registrar.auth.AdminPasswordChecker;
import net.cnri.doregistrytools.registrar.auth.RegistrarAuthenticator;
import net.cnri.doregistrytools.registrar.jsonschema.ServletUtil;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.handle.hdllib.AuthenticationInfo;

public class RegistrarServiceFactory {
    private static Logger logger = LoggerFactory.getLogger(RegistrarServiceFactory.class);
                    
    private static RegistrarService registrar = null;
    private static String defaultAdminPassword = "password";
    
    public synchronized static RegistrarService getRegistrarService(ServletContext context) throws RepositoryException, InvalidException, IOException {
        if (registrar == null) {
            Repository repo = ServletUtil.getRepository(context);
//            File webappsStorage = (File)context.getAttribute("net.handle.server.webapp_storage_directory");
//            File objectDir = new File(webappsStorage, "preload-objects");
//            if (objectDir.exists()) {
//                logger.info("Loading preload-objects");
//                FileSystemDigitalObject.loadAllObjectsFromDir(objectDir, repo);
//            } else {
//                logger.info("No preload-objects found");
//            }
            String serverPrefix = ServletUtil.getServerPrefix(context);
            AuthenticationInfo authInfo = ServletUtil.getHandleAuth(context);
            
            net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
            AdminPasswordChecker adminPasswordChecker = new AdminPasswordChecker(serverMain, defaultAdminPassword);
//            String adminPassword = getAdminPassword(context);
            RegistrarAuthenticator authenticator = new RegistrarAuthenticator(adminPasswordChecker, repo);
            registrar = new RegistrarService(repo, serverPrefix, authInfo, authenticator);
            File repoInitJsonFile = new File(ServletUtil.getBaseDir(context), "repoInit.json");
            if (repoInitJsonFile.exists()) {
                initializeFromFile(repoInitJsonFile);
                repoInitJsonFile.delete();
            }
        } 
        return registrar;
    }
    
    private static void initializeFromFile(File repoInitJsonFile) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(repoInitJsonFile), "UTF-8"));
            JsonObject json = (JsonObject) new JsonParser().parse(reader);
            String initBaseUri = json.get("baseUri").getAsString();
            HandleMintingConfig handleMintingConfig = HandleMintingConfig.getDefaultConfig();
            handleMintingConfig.baseUri = initBaseUri;
            registrar.updateHandleMintingConfig(handleMintingConfig);
        } catch (Exception e) {
            logger.error("Error reading repoInit.json", e);
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException e) { }
        }
    }
    
//    private static String getAdminPassword(ServletContext context) {
//        //String adminPassword = context.getInitParameter("AdminPassword");
//        net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
//        if (serverMain == null) return null;
//        String storedPassword = serverMain.getStoredPasswordForUser("admin");
//        if (storedPassword != null) {
//            return storedPassword;
//        } else {
//            return defaultAdminPassword;
//        }
//    }
}
