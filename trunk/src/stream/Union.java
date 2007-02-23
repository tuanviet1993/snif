package stream;

public class Union<I> extends AbstractPipe<I, I> {
	public void process(I o, int srcID, long timestamp) {
		transfer( o, timestamp);
	}
	public Union(String name) {
		this.name = name;
	}
}
