package com.wys.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.WebApps;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventQueues;

/**
 * Bridges Spring events to ZK.
 *
 * Listens for {@link StockChangedEvent} and republishes it on an
 * APPLICATION-scoped ZK event queue. Every open dashboard (any browser tab,
 * any user) is subscribed to that queue and redraws its chart when notified.
 */
@Component
public class StockChangeBridge {

    /** Name of the application-wide ZK event queue. */
    public static final String QUEUE = "wysInventoryChanges";
    /** Name of the ZK event delivered to subscribers. */
    public static final String EVENT = "onInventoryChanged";

    private static final Logger log = LoggerFactory.getLogger(StockChangeBridge.class);

    @EventListener
    public void onStockChanged(StockChangedEvent change) {
        try {
            Execution exec = Executions.getCurrent();
            WebApp app = (exec != null) ? exec.getDesktop().getWebApp() : WebApps.getCurrent();
            if (app == null) {
                return; // not inside a ZK context; the dashboard's timer will catch up
            }
            EventQueues.lookup(QUEUE, app, true)
                       .publish(new Event(EVENT, null, change));
        } catch (Exception ex) {
            // Never let a UI notification break a database operation.
            log.warn("Could not broadcast stock change to dashboards: {}", ex.getMessage());
        }
    }
}