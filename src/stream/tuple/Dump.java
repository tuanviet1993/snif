package stream.tuple;

import stream.AbstractSink;

public class Dump extends AbstractSink<Tuple> {
	public void process(Tuple o, int srcID, long timestamp) {
		System.out.println( "[" + timestamp + "] " + o );
	}
	public Dump(String name) {
		this.name = name;
	}
}
