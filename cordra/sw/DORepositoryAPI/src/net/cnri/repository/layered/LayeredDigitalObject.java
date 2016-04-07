/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.AbstractCloseableIterator;

public class LayeredDigitalObject extends AbstractDigitalObject implements DigitalObject, SupportsFastCopyForLayeredRepo {

	private final LayeredRepository repo;
    private final String handle;
	private DigitalObject top;
	private DigitalObject bottom;
	private final ExecutorService execServ;
	
	public LayeredDigitalObject(LayeredRepository repo, String handle, DigitalObject top, DigitalObject bottom, ExecutorService execServ) {
        this.repo = repo;
        this.handle = handle;
        this.top = top;
        this.bottom = bottom;
        this.execServ = execServ;
    }
	
    void setTop(DigitalObject top) {
        this.top = top;
    }
    
	void setBottom(DigitalObject bottom) {
	    this.bottom = bottom;
	}

	DigitalObject getTop() {
	    return this.top;
	}

	DigitalObject getBottom() {
	    return this.bottom;
	}

	@Override
	public LayeredRepository getRepository() {
		return repo;
	}

	@Override
	public String getHandle() {
		return handle;
	}

	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
	    if(top!=null) return top.getAttributes();
	    return bottom.getAttributes();
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
		if(top!=null) return top.listAttributes();
		return bottom.listAttributes();
	}

	@Override
	public String getAttribute(String id) throws RepositoryException {
	    if(top!=null) return top.getAttribute(id);
	    return bottom.getAttribute(id);
	}

	@Override
	public void setAttributes(final Map<String, String> attributes) throws RepositoryException {
	    if(top==null) repo.liftToTop(this);
		top.setAttributes(attributes);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.setAttributes(attributes);
                return null;
            }
        });
	}

	@Override
	public void setAttribute(final String name, final String value) throws RepositoryException {
        if(top==null) repo.liftToTop(this);
		top.setAttribute(name, value);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.setAttribute(name, value);
                return null;
            }
        });
	}

	@Override
	public void deleteAttributes(final List<String> names) throws RepositoryException {
        if(top==null) repo.liftToTop(this);
		top.deleteAttributes(names);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.deleteAttributes(names);
                return null;
            }
        });		
	}

	@Override
	public void deleteAttribute(final String name) throws RepositoryException {
        if(top==null) repo.liftToTop(this);
		top.deleteAttribute(name);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.deleteAttribute(name);
                return null;
            }
        });			
	}

	@Override
	public boolean verifyDataElement(String name) throws RepositoryException {
	    if(top!=null) return top.verifyDataElement(name);
	    return bottom.verifyDataElement(name);
	}

	@Override
	public LayeredDataElement createDataElement(final String name) throws CreationException, RepositoryException {
        if(top==null) repo.liftToTop(this);
	    if(top.verifyDataElement(name)) throw new CreationException();
	    DataElement topElement = top.createDataElement(name);
        final LayeredDataElement res = new LayeredDataElement(this,name,topElement,null,execServ);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException, CreationException {
            	res.setBottom(bottom.createDataElement(name));
                return null;
            }
        });        
        return res;
	}

	@Override
	public LayeredDataElement getDataElement(final String name) throws RepositoryException {
	    if(top==null) {
	        DataElement bottomElement = bottom.getDataElement(name);
	        if(bottomElement==null) return null;
	        return new LayeredDataElement(this,name,null,bottomElement,execServ);
	    }
	    
	    DataElement topElement = top.getDataElement(name);
	    if(topElement==null) return null;
	    final LayeredDataElement res = new LayeredDataElement(this,name,topElement,null,execServ);
	    if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	res.setBottom(bottom.getDataElement(name));
                return null;
            }
        });	
	    return res;
	}

	@Override
	public void deleteDataElement(final String name) throws RepositoryException {
        if(top==null) repo.liftToTop(this);
	    top.deleteDataElement(name);
	    if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.deleteDataElement(name);
                return null;
            }
        });
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
		if(top!=null) return top.listDataElementNames();
		return bottom.listDataElementNames();
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
	    if(top!=null) return new AbstractCloseableIterator<DataElement>() {
	        CloseableIterator<DataElement> topElements = top.listDataElements();
            @Override
	        protected DataElement computeNext() {
                if(!topElements.hasNext()) return null;
                DataElement topElement = topElements.next();
                final String name = topElement.getName();
                final LayeredDataElement res = new LayeredDataElement(LayeredDigitalObject.this,name,topElement,null,execServ);
                if(execServ!=null) execServ.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws RepositoryException {
                        res.setBottom(bottom.getDataElement(name));
                        return null;
                    }
                });                 
                return res;
            }
	        @Override
	        protected void closeOnlyOnce() {
	            topElements.close();
	        }
	    };
	    else return new AbstractCloseableIterator<DataElement>() {
            CloseableIterator<DataElement> bottomElements = bottom.listDataElements();
            @Override
            protected DataElement computeNext() {
                if(!bottomElements.hasNext()) return null;
                DataElement bottomElement = bottomElements.next();
                final String name = bottomElement.getName();
                final LayeredDataElement res = new LayeredDataElement(LayeredDigitalObject.this,name,null,bottomElement,execServ);
                return res;
            }
            @Override
            protected void closeOnlyOnce() {
                bottomElements.close();
            }
        };
	}
	
	@Override
	public void copyTo(DigitalObject target) throws RepositoryException, IOException {
	    if(top!=null) LayeredRepository.copyForLayeredRepo(top,target);
	    else LayeredRepository.copyForLayeredRepo(bottom,target);
	}
}
