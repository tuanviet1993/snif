package stream;

public class TimeStampedObject<I> {
	public long timestamp;
	public I object;
	public TimeStampedObject (long timestamp, I object){
		this.timestamp = timestamp;
		this.object = object;
	}
}
