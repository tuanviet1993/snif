package stream.tuple;

import model.NodeAddress;
import model.PacketTracerTuple;
import model.PacketTracer;
import stream.AbstractPipe;

public class PacketTupleTracer extends AbstractPipe<Tuple, Tuple> {

	private final model.PacketTracer tracer;
	private final String attrL2dst;
	private final String attrL3src;
	private final String attrL3dst;
	private final String attrL3seqNr;

	private final int tupleTypeID;
	private final TupleAttribute tupleL2srcID;
	private final TupleAttribute tupleL2dstID;
	private final TupleAttribute tupleL3srcID;
	private final TupleAttribute tupleL3dstID;
	private final TupleAttribute tupleL3seqNrID;
	
	/**
	 * @param attrL2dst
	 * @param attrL3src
	 * @param attrL3dst
	 * @param attrL3seqNr
	 */
	public PacketTupleTracer(final String tupleName, final String attrL2dst, final String attrL3src, final String attrL3dst, final String attrL3seqNr) {;
		this.attrL2dst = attrL2dst;
		this.attrL3src = attrL3src;
		this.attrL3dst = attrL3dst;
		this.attrL3seqNr = attrL3seqNr;
		
		this.tupleTypeID = Tuple.getTupleTypeID(tupleName);
		tupleL2srcID = new TupleAttribute("l2src");
		tupleL2dstID = new TupleAttribute("l2dst");
		tupleL3srcID = new TupleAttribute("l3src");
		tupleL3dstID = new TupleAttribute("l3dst");
		tupleL3seqNrID = new TupleAttribute("l3seqNr");
		
		tracer = PacketTracer.getPacketTracer();
		PacketTracer.discardCachedPacket();

		Tuple.registerTupleType( "PacketTracerTuple",  "l2src", "l2dst", "l3src", "l3dst", "l3seqNr");
	}

	public void process(Tuple inTuple, int srcID, long timestamp) {

		// TODO why cast here to PacketTuple ?
		// 
		PacketTracerTuple traceItem = new PacketTracerTuple();
		PacketTuple o = (PacketTuple) inTuple;
		traceItem.l2dst = new NodeAddress( (Integer) o.getAttribute(attrL2dst));
		traceItem.l3src = new NodeAddress( (Integer) o.getAttribute(attrL3src));
		traceItem.l3dst = new NodeAddress( (Integer) o.getAttribute(attrL3dst));
		traceItem.l3seqNr = (Integer) o.getAttribute(attrL3seqNr);
		
		tracer.tracePacket( traceItem, timestamp );
		
		Tuple tuple = Tuple.createTuple(tupleTypeID);
		tuple.setAttribute( tupleL2srcID, traceItem.l2src.getInt());
		tuple.setAttribute( tupleL2dstID, traceItem.l2dst.getInt());
		tuple.setAttribute( tupleL3srcID, traceItem.l3src.getInt());
		tuple.setAttribute( tupleL3dstID, traceItem.l3dst.getInt());
		tuple.setAttribute( tupleL3seqNrID, traceItem.l3seqNr);

		// System.out.println(tuple);
		
		transfer( tuple, timestamp);
	}

}
