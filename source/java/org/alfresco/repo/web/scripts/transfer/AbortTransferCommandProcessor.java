
package org.alfresco.repo.web.scripts.transfer;

import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;

import org.alfresco.service.cmr.transfer.TransferException;
import org.alfresco.service.cmr.transfer.TransferReceiver;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.WrappingWebScriptRequest;
import org.springframework.extensions.webscripts.json.JSONWriter;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRequest;

/**
 * This command processor is used to record the start a transfer. No other transfer can be started after this command
 * has executed until the started transfer terminates.
 * 
 * @author brian
 * 
 */
public class AbortTransferCommandProcessor implements CommandProcessor
{
    private static final String MSG_CAUGHT_UNEXPECTED_EXCEPTION = "transfer_service.receiver.caught_unexpected_exception";

    private TransferReceiver receiver;
    
    private static Log logger = LogFactory.getLog(AbortTransferCommandProcessor.class);

    /*
     * (non-Javadoc)
     * 
     * @see org.alfresco.repo.web.scripts.transfer.CommandProcessor#process(org.alfresco .web.scripts.WebScriptRequest,
     * org.alfresco.web.scripts.WebScriptResponse)
     */
    public int process(WebScriptRequest req, WebScriptResponse resp)
    {
        String transferRecordId = null;

        //Read the transfer id from the request

        // Unwrap to a WebScriptServletRequest if we have one
        // TODO: Why is this necessary?
        WebScriptServletRequest webScriptServletRequest = null;
        WebScriptRequest current = req;
        do
        {
            if (current instanceof WebScriptServletRequest)
            {
                webScriptServletRequest = (WebScriptServletRequest) current;
                current = null;
            }
            else if (current instanceof WrappingWebScriptRequest)
            {
                current = ((WrappingWebScriptRequest) req).getNext();
            }
            else
            {
                current = null;
            }
        }
        while (current != null);
        
        HttpServletRequest servletRequest = webScriptServletRequest.getHttpServletRequest();
        String transferId = servletRequest.getParameter("transferId");

        if ((transferId == null))
        {
            resp.setStatus(Status.STATUS_BAD_REQUEST);
            return Status.STATUS_BAD_REQUEST;
        }
        
        try
        {   
            logger.debug("abort transfer:" + transferId);
            
            receiver.cancel(transferId);

            // return the unique transfer id (the lock id)
            StringWriter stringWriter = new StringWriter(300);
            JSONWriter jsonWriter = new JSONWriter(stringWriter);
            jsonWriter.startObject();
            jsonWriter.writeValue("transferId", transferRecordId);
            jsonWriter.endObject();
            String response = stringWriter.toString();

            resp.setContentType("application/json");
            resp.setContentEncoding("UTF-8");
            int length = response.getBytes("UTF-8").length;
            resp.addHeader("Content-Length", "" + length);
            resp.setStatus(Status.STATUS_OK);
            resp.getWriter().write(response);

            return Status.STATUS_OK;

        } 
        catch (Exception ex)
        {
            logger.debug("caught exception", ex);
            if (ex instanceof TransferException)
            {
                throw (TransferException) ex;
            }
            throw new TransferException(MSG_CAUGHT_UNEXPECTED_EXCEPTION, ex);
        }
    }

    /**
     * @param receiver the receiver to set
     */
    public void setReceiver(TransferReceiver receiver)
    {
        this.receiver = receiver;
    }
    
}
