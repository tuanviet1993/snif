package model;

enum EventType {
	Default, RouteUpdate, Error
}
public class Event {
	String txt;
	NodeAddress node;
	long time_ms;
	EventType type = EventType.Default;
	
	public Event (NodeAddress node, String txt, long time_ms) {
		this.txt = txt;
		this.node = node;
		this.time_ms = time_ms;
	}
	public Event (NodeAddress node, String txt) {
		this.txt = txt;
		this.node = node;
		this.time_ms = PacketSorter.getSimulationTime();
	}
	public Event (NodeAddress node) {
		this.txt = "DEFAULT";
		this.node = node;
		this.time_ms = PacketSorter.getSimulationTime();
	}
	public Event (NodeAddress node, EventType type, String txt, long time_ms) {
		this.type = type;
		this.txt = txt;
		this.node = node;
		this.time_ms = time_ms;
	}
	public Event (NodeAddress node, EventType type, String txt) {
		this.type = type;
		this.txt = txt;
		this.node = node;
		this.time_ms = PacketSorter.getSimulationTime();
	}
	
	public String toString(){
		if (time_ms < 1000) 
			return "0"+time_ms+" "+node+" " + txt;
		else 
			return ""+time_ms+" "+node+" " + txt;
	}
}

