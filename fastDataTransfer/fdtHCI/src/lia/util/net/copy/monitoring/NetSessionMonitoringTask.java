/*
 * $Id: NetSessionMonitoringTask.java,v 1.1 2012-10-29 22:29:53 HCI\rcundick Exp $
 */
package lia.util.net.copy.monitoring;

import java.util.logging.Level;
import java.util.logging.Logger;
import lia.util.net.copy.Accountable;
import lia.util.net.copy.monitoring.base.AbstractAccountableMonitoringTask;
import lia.util.net.copy.transport.TCPTransportProvider;

/**
 * 
 * @author ramiro
 */
public class NetSessionMonitoringTask extends AbstractAccountableMonitoringTask {

    private static final Logger logger = Logger.getLogger(NetSessionMonitoringTask.class.getName());

    private final TCPTransportProvider transportProvider;
    public NetSessionMonitoringTask(TCPTransportProvider transportProvider) {
        super(new Accountable[] {transportProvider});
        
        if(logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "[ NetSessionMonitoringTask ] for transportProvider " + transportProvider + " instantiating", new Exception(" Debug stack trace")) ;
        }

        this.transportProvider = transportProvider;
    }

    public double getTotalRate() {
        return getTotalRate(transportProvider);
    }
    
    @Override
    public void rateComputed() {
        // TODO Auto-generated method stub
        
    }

}
