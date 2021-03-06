/*
 * $Id: SocketTask.java,v 1.1 2012-10-29 22:29:43 HCI\rcundick Exp $
 */
package lia.util.net.copy.transport;

import gui.Log;
import java.util.concurrent.BlockingQueue;

import lia.util.net.common.AbstractFDTIOEntity;
import lia.util.net.common.Config;
import lia.util.net.copy.transport.internal.FDTSelectionKey;

/**
 * 
 * The base class for to fill/drain a socket channel.
 * 
 * @author ramiro
 * 
 */
public abstract class SocketTask extends AbstractFDTIOEntity implements Runnable {
//    protected final static DirectByteBufferPool payloadPool = DirectByteBufferPool.getInstance();
//    protected final static HeaderBufferPool headersPool = HeaderBufferPool.getInstance();
    protected static final boolean isBlocking =  Config.getInstance().isBlocking();

    private static Log logger = Log.getLoggerInstance();

    protected final BlockingQueue<FDTSelectionKey> readyChannelsQueue;

    public long getSize() {
        return -1;
    }
    
    public SocketTask(BlockingQueue<FDTSelectionKey> readyChannelsQueue) {
        this.readyChannelsQueue = readyChannelsQueue;
    }
    
    protected void internalClose() throws Exception {
        try {
            for(final FDTSelectionKey selKey: readyChannelsQueue) {
                final FDTKeyAttachement attach = selKey.attachment();
                if(attach != null) {
                    attach.recycleBuffers();
                }
            }
        }catch(Throwable t) {
            logger.logError(t);
        }
    }
}
