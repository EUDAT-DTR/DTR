/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.auth.AdminPasswordChecker;
import net.cnri.doregistrytools.registrar.auth.RegistrarAuthenticator;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.handle.hdllib.AuthenticationInfo;

public class RelationshipsServiceFactory {
//    private static Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private static RelationshipsService relationshipsService = null;
    
    public synchronized static RelationshipsService getRelationshipsService(ServletContext context) throws RepositoryException, InvalidException, IOException {
        if (relationshipsService == null) {
            Repository repo = ServletUtil.getRepository(context);
            relationshipsService = new RelationshipsService(RegistrarServiceFactory.getRegistrarService(context));
        } 
        return relationshipsService;
    }
    
}
