package stream;

public abstract interface TimeTriggered {
	public void handleTimerEvent(long timestamp);
}
