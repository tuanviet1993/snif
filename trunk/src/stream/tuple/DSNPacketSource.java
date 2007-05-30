package stream.tuple;

import java.util.TreeMap;

import dsn.DSNConnector;
import dsn.PacketListener;
import packetparser.DecodedPacket;
import packetparser.PDL;
import stream.AbstractSource;
import stream.RealTime;

public class DSNPacketSource extends AbstractSource<PacketTuple> implements RealTime, PacketListener {

	private static final int De_JITTER_DELAY = 5000;
	private PDL parser;
	private TreeMap<Long,PacketTuple> packets = new TreeMap<Long,PacketTuple>(); 
	private boolean haveTime = false;

	private long firstPacketMillis = 0;
	private long refTimestamp = 0;
	/**
	 * @param dsnConnection2
	 * @param parser
	 */
	public DSNPacketSource(DSNConnector dsnConnection, PDL parser) {
		this.parser = parser;
		dsnConnection.registerPacketListener(this);
		// start thread
	}

	@Override
	public PacketTuple next() {
		PacketTuple packet;
		synchronized (packets) {
			long key = packets.firstKey();
			// System.out.println("Packet Time: ("+key + ") " + (key - refTimestamp) );
			packet = packets.get(key);
			packets.remove(key);
			packet.setTime(packet.getTime() - refTimestamp);
		}
		return packet;
	}

	public boolean ready() {
		if (haveTime == false )
			return false;

		synchronized(packets) {
			if (packets.isEmpty())
				return false;

			long key = packets.firstKey();
			// time in simulation
			long simulationTime = System.currentTimeMillis() - firstPacketMillis ; 
			long packetTime = key - refTimestamp;
			return (simulationTime - packetTime > De_JITTER_DELAY);
		}
	}

	public void handlePacket(int len, byte[] data) {
		// get timestamp and dns address
		String btAddress = Integer.toHexString( unsigned16LE( data, 0));
		long timestamp = (long) unsigned32LE( data, 6);
		// strip header
		byte[] packetRaw = new byte[len-11];
		System.arraycopy(data, 11, packetRaw, 0, len-11);
		DecodedPacket packet = null;
		if (len > 11){
			// if len <= 11 we just received a timestamp
			packet = DecodedPacket.createPacketFromBuffer(parser, packetRaw);
		}
		PacketTuple tuple = new PacketTuple(packet, timestamp);
		tuple.setDsnNode(btAddress);
		synchronized (packets) {
			packets.put(timestamp, tuple);

			// check for time
			if (haveTime == false) {
				if (firstPacketMillis == 0) {
					firstPacketMillis = System.currentTimeMillis();
				} else {
					if (System.currentTimeMillis() - firstPacketMillis > De_JITTER_DELAY) {
						refTimestamp = packets.firstKey();
						System.out.println("refTimestamp "+ refTimestamp);
						haveTime = true;
					}
				}
			}
		}		
	}

	static private int unsignedByteToInt(byte value) {
		if (value >= 0) return value;
		return value+256;
	}

	static private int unsigned16LE( byte[] data, int pos) {
		return unsignedByteToInt( data[pos+1] ) << 8 | unsignedByteToInt( data[pos]);
	}

	static private int unsigned32LE( byte[] data, int pos) {
		return unsigned16LE( data, pos+2) << 16 | unsigned16LE( data, pos);
	}
}
