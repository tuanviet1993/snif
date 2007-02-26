package stream;

public abstract class AbstractPipe<I,O> implements Pipe<I,O> {

	// public String name ="NameNotSetFor_"+this.getClass().getName();
	
	/** 
	 * Subscribed sinks. The IDs these sinks got
	 * registered with are stored in the array <CODE>sinkIDs</CODE>
	 * using the same position.
	 */
	protected Sink<? super O>[] sinks;

	/**
	 * IDs belonging to the subscribed sinks.
	 */
	protected int[] sinkIDs;

	@SuppressWarnings("unchecked")
	public boolean subscribe(Sink<? super O> sink, int sinkID) {
		if (sinks == null) {
			sinks = new Sink[]{sink};
			sinkIDs = new int[]{sinkID};
		}
		else {
			Sink[] sinksTmp = new Sink[sinks.length+1];
			int[] IDsTmp = new int[sinkIDs.length+1];
			System.arraycopy(sinks, 0, sinksTmp, 0, sinks.length);
			System.arraycopy(sinkIDs, 0, IDsTmp, 0, sinkIDs.length);
			sinksTmp[sinks.length] = sink;
			IDsTmp[sinks.length] = sinkID;
			sinks = sinksTmp;
			sinkIDs = IDsTmp;
		}
		return true;
	}

	public void transfer(O o, long timestamp) {
		if (sinks != null) {
			for (int i = 0; i < sinks.length; i++)
				sinks[i].process(o, sinkIDs[i], timestamp);
		}
	}
	
	public Sink[] getSinks() {
		return sinks;
	}
	public int[] getSinkIDs() {
		return sinkIDs;
	}


}
