package stream.tuple;

import java.util.HashMap;

import stream.AbstractPipe;

public class SeqNrResetDetector extends AbstractPipe<Tuple, Tuple> {

	int maxSeqNq;
	int expectOverrun;
	int overrunBuffer;
	int idFieldID;
	int seqNrFieldID;
	
	HashMap<Object, Integer> nodes = new HashMap<Object, Integer>();

	public void process(Tuple o, int srcID, long timestamp) {
		Object nodeID = o.getAttribute(idFieldID);
		int currentSeqNr = o.getIntAttribute(seqNrFieldID);
		Integer lastSeqNr = nodes.get(nodeID);
		if (lastSeqNr != null) { 
			if ( currentSeqNr < lastSeqNr && currentSeqNr < expectOverrun) {
				Tuple tuple = Tuple.createTuple("NodeRebootEvent");
				tuple.setAttribute(idFieldID, nodeID);
				transfer( tuple, timestamp);
			} else {
			}
		}
		nodes.put(nodeID, currentSeqNr);
	}
	
	public SeqNrResetDetector(String idField, String seqNrField, int maxSeqNq, int expectOverrun) {
		idFieldID = Tuple.getAttributeId(idField);
		seqNrFieldID = Tuple.getAttributeId(seqNrField);
		this.maxSeqNq = maxSeqNq;
		this.expectOverrun = maxSeqNq - expectOverrun;
	}
}
