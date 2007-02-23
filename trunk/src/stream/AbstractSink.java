package stream;

public abstract class AbstractSink<I> implements Sink<I> {

	public String name ="NameNotSetFor_"+this.getClass().getName();

	public void process(I o, long timestamp) {
		// TODO Auto-generated method stub

	}

}
