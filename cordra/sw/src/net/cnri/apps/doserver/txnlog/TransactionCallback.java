/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

/************************************************************************
 * Interface used to define objects that want to receive the streamed
 * results from RetrieveTxnResponse messages.
 ************************************************************************/
public interface TransactionCallback
{

  /********************************************************************
   * Process the given transaction which was received via the stream
   * in the RetrieveTxnResponse message.
   ********************************************************************/
  public void processTransaction(Transaction txn)
    throws Exception;

  /********************************************************************
   * Finish processing this request.  The given date (or more specifically,
   * the minimum date returned from all replicated servers) should be
   * used the next time that a RetrieveTxnRequest is sent.
   ********************************************************************/
  public void finishProcessing(long sourceDate);

}
