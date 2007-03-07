package stream.tuple;

import packetparser.DecodedPacket;
import packetparser.PDL;
import packetparser.PacketTemplate;
import packetparser.PhyConfig;
import stream.Predicate;

/**
 * CRC Predicate to check for valid packets
 * 
 * This CRC implements CCITT-16, the crc is stored in field 'crc' of the default packet type
 * 
 * 
 * @author mringwal
 */

public class PacketCrcPredicate extends Predicate<PacketTuple> {

	PhyConfig phyConfig;
	String crcAttributeName;
	
	public PacketCrcPredicate(PDL parser) {
		phyConfig  = parser.getSnifferConfig();
		PacketTemplate defPack = parser.getDefaultPacket();
		crcAttributeName = defPack.getTypeName()+".crc";
	}
	
	@Override
	public boolean invoke(PacketTuple o, long timestamp) {
		int crcInPacket = o.getIntAttribute(crcAttributeName);
	    DecodedPacket packet = o.getPacket();
	    int crcPos = phyConfig.CRCpos;
	    if (!phyConfig.fixedSize){
	    	crcPos += packet.getByte(phyConfig.lengthPos) + phyConfig.lengthOffset;
	    }
	    // check valid packet size
	    if (crcPos > packet.getLength()) {
	    	return false;
	    }
	    int crc = 0xffff;
	    for (int pos=0; pos < crcPos ; pos++ ){
	    	int data = packet.getByte(pos);
	        data ^= crc & 0xff;
	        data ^= (data << 4) & 0xff;
	        crc = (((data << 8) | (crc >> 8)) ^ (data >> 4) ^ (data << 3) ) &0xffff;
	    }
	    return (crcInPacket == crc);
	}
}
