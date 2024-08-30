package yt.mak.fire_items;

import java.util.List;

public class Story {
    public List<Event> events;

    public static class Event {
        public String id;
        public String trigger;
        public String block;
        public List<Action> actions;
    }

    public static class Action {
        public String type;
        public String npc;
        public List<String> dialogue;
    }
}