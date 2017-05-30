/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import net.cnri.doregistrytools.registrar.doip.RegistrarDoipOperations;
import net.cnri.doregistrytools.registrar.indexer.JsonSchemaIndexBuilder;
import net.cnri.doregistrytools.registrar.replication.ReplicationPoller;
import net.cnri.repository.Repository;

@WebListener
public class WebappStartupListener implements ServletContextListener {
    HttpClientManager httpClientManager;
    ReplicationPoller replicationPoller;
    RegistrarService registrar;
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();
            net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
            
            registrar = RegistrarServiceFactory.getRegistrarService(context);
            RelationshipsService relationshipsService = RelationshipsServiceFactory.getRelationshipsService(context);
            
            JsonSchemaIndexBuilder indexBuilder = new JsonSchemaIndexBuilder(registrar, relationshipsService);
            
            //You must not perform a search in the construction of RegistrarService
            serverMain.setConfigVal("insecure_search", "true");
            serverMain.registerIndexBuilder(indexBuilder);
            
            registrar.updateKnownSchemasBySearch();
            
            Design design = registrar.getDesign();
//            if (design.remoteRepositories.size() > 0) {
//                replicationPoller = new ReplicationPoller(registrar, design.remoteRepositories);
//            }
            httpClientManager = new HttpClientManager(); 
            
            replicationPoller = new ReplicationPoller(registrar, design.remoteRepositories, httpClientManager.getClient());
            
            context.setAttribute(ReplicationPoller.class.getCanonicalName(), replicationPoller);
            
            String baseUri = getBaseUri(serverMain, context);
            System.out.println("Go to " + baseUri + " in a web browser to access your repository.");
            System.out.println("For the admin interface go to " + baseUri + "/admin.html in a web browser to access your repository.");

            RegistrarDoipOperations registrarDoipOperations = new RegistrarDoipOperations(baseUri, serverMain.getServerID(), httpClientManager.getClient());
            serverMain.setKnowbotMapping(RegistrarDoipOperations.class.getName(), registrarDoipOperations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private String getBaseUri(net.cnri.apps.doserver.Main serverMain, ServletContext context) {
        String path = context.getContextPath();
        
        String baseUri = "";
        String httpPort = serverMain.getConfigVal("http_port");
        String httpsPort = serverMain.getConfigVal("https_port");
        if (httpPort == null) {
            baseUri = "https://localhost:"+httpsPort + path;
        } else {
            baseUri = "http://localhost:"+httpPort + path;
        }
        return baseUri;
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (replicationPoller != null) {
            replicationPoller.shutdown();
        }
        if (httpClientManager != null) {
            httpClientManager.destroy();
        }
        if (registrar != null) {
            registrar.shutdown();
        }
        Repository repo = (Repository) sce.getServletContext().getAttribute(Repository.class.getName());
        if (repo != null) repo.close();
    }
}
