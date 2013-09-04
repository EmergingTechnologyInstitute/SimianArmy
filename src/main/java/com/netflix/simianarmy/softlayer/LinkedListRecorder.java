package com.netflix.simianarmy.softlayer;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.netflix.simianarmy.MonkeyRecorder;
import com.netflix.simianarmy.basic.BasicRecorderEvent;

public class LinkedListRecorder implements MonkeyRecorder {

	private final List<Event> events = new LinkedList<Event>();

    @Override
    public Event newEvent(Enum mkType, Enum eventType, String region, String id) {
        return new BasicRecorderEvent(mkType, eventType, region, id);
    }

    @Override
    public void recordEvent(Event evt) {
        events.add(evt);
    }

    @Override
    public List<Event> findEvents(Map<String, String> query, Date after) {
        return events;
    }

    @Override
    public List<Event> findEvents(Enum mkeyType, Map<String, String> query, Date after) {
        // used from BasicScheduler
        return events;
    }

    @Override
    public List<Event> findEvents(Enum mkeyType, Enum eventType, Map<String, String> query, Date after) {
        // used from ChaosMonkey
        List<Event> evts = new LinkedList<Event>();
        for (Event evt : events) {
            if (query.get("groupName").equals(evt.field("groupName")) && evt.monkeyType() == mkeyType
                    && evt.eventType() == eventType && evt.eventTime().after(after)) {
                evts.add(evt);
            }
        }
        return evts;
    }
}
