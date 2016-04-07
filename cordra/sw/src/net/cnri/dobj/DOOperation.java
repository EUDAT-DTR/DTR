/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Objects implementing the DOOperation interface can be used to perform
 * an operation on a digital object in a repository.
 */
public interface DOOperation {
  
  /**
   * Returns true iff this object can perform the given operation on
   * behalf of the caller on the given object.  The operation, object,
   * caller, and request parameters are all provided by the given
   * DOOperationContext object.
   */
  public boolean canHandleOperation(DOOperationContext context);
  
  /**
   * Returns a list of operations that this operator can perform
   * on the object identified by the DOOperationContext parameter.
   */
  public String[] listOperations(DOOperationContext context);
  
  /**
   * Performs the given operation (which this object has advertised that it
   * can handle) which consists of reading input (if any is expected) from the
   * given InputStream and writing the output of the operation (if any) to the
   * OutputStream.  This method should always close the input and output streams
   * when finished with them.  If there are any errors in the input, the error
   * message must be communicated on the OutputStream since all errors must be
   * at the application level.  Any exceptions thrown by this method will *not*
   * be communicated to the caller and are therefore not acceptable.
   */
  public void performOperation(DOOperationContext context,
                               InputStream in,
                               OutputStream out);
  
}
