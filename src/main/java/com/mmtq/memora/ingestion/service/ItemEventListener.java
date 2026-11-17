package com.mmtq.memora.ingestion.service;

import com.mmtq.memora.common.event.ItemChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ItemEventListener {

    private static final Logger log = LoggerFactory.getLogger( ItemEventListener.class );

    private final JobService jobService;

    public ItemEventListener( JobService jobService ) {
        this.jobService = jobService;
    }

    @TransactionalEventListener( phase = TransactionPhase.BEFORE_COMMIT )
    public void onItemChanged( ItemChangedEvent event ) {
        log.debug( "ItemChangedEvent received for item: {}", event.itemId() );
        jobService.enqueueIndexJob( event.itemId() );
    }
}
