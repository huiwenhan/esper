/**************************************************************************************
 * Copyright (C) 2006-2015 EsperTech Inc. All rights reserved.                        *
 * http://www.espertech.com/esper                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.example.transaction;

import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.EventBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealtimeSummaryGroupListener implements UpdateListener
{
    private String groupIdentifier;

    public RealtimeSummaryGroupListener(String groupIdentifier)
    {
        this.groupIdentifier = groupIdentifier;
    }

    public void update(EventBean[] newEvents, EventBean[] oldEvents)
    {
        if (newEvents == null)
        {
            // we don't care about events leaving the window (old events)
            return;
        }

        EventBean theEvent = newEvents[0];
        log.debug(
                groupIdentifier + "=" + theEvent.get(groupIdentifier) +
                " minAC=" + theEvent.get("minLatency") +
                " maxAC=" + theEvent.get("maxLatency") +
                " avgAC=" + theEvent.get("avgLatency")
                );
    }

    private static final Logger log = LoggerFactory.getLogger(RealtimeSummaryGroupListener.class);
}
