package stream.tuple;

import packetparser.DecodedPacket;
import packetparser.PDL;
import stream.AbstractSource;
import model.Packet;

public class PacketMerger extends AbstractSource<PacketTuple> {

	private PDL parser;
	private model.PacketSorter sorter;
	private long timebase;
	
	public PacketMerger( model.PacketSorter sorter, PDL parser) {
		this.sorter = sorter;
		this.parser = parser;
		timebase = sorter.getTimeBase();
	}
	
	@Override
	public PacketTuple next() {
		PacketTuple packetTuple = null;
		try {
			Packet p = sorter.getNextPacket();
			if (p == null) return null ;
			byte[] rawData = p.getTOSmsg();  								    // convert linkdump to raw TOS_msg
			DecodedPacket decodedPacket = parser.decodePacket(rawData);      // create DecodedPacket from it
			packetTuple = new PacketTuple(decodedPacket, p.getTime() - timebase); // correct timestamp
		} catch (Exception e) {
			e.printStackTrace();
		}
		return packetTuple;
	}
}
