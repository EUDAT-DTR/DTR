/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.highlevel;

import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.wrapper.RepositoryWrapper;

public class HighLevelRepository extends RepositoryWrapper {

	public HighLevelRepository(Repository originalRepository) {
		super(originalRepository);
	}
	
	@Override
    protected HighLevelDigitalObject wrap(DigitalObject dobj) {
        return new HighLevelDigitalObject(this,dobj);
    }
	
	@Override
    public HighLevelDigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        return wrap(originalRepository.createDigitalObject(handle));
    }
	
	@Override
    public HighLevelDigitalObject getDigitalObject(String handle) throws RepositoryException {
        return wrap(originalRepository.getDigitalObject(handle));
    }

	@Override
    public HighLevelDigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        return wrap(originalRepository.getOrCreateDigitalObject(handle));
    }
	

//	@Override
//    protected DataElement wrap(DigitalObjectWrapper dobj, DataElement el) {
//        return new DataElementWrapper(dobj,el);
//    }

}
