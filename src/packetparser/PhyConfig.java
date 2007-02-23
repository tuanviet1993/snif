package packetparser;

public class PhyConfig {

	public int frequency;

	public boolean fixedSize = true;

	public int packetSize = 0;

	public int lengthPos; // one byte

	public int lengthOffset;

	public int SOP = 0xaa55;

	public boolean hasCRC = false;

	public int CRCpoly;

	public int CRCpos;

	public void printConfig() {
		System.out.println("\n=== Sniffer Config ===");
		System.out.println("Frequency:   " + frequency);
		System.out.println("SOP:         0x" + Integer.toHexString(SOP) );
		if (fixedSize) {
			System.out.println("Packet size: " + packetSize);
		} else {
			System.out.println("Variable Packet size, header: " + packetSize);
			System.out
					.println("Variable Packet size, length at : " + lengthPos);
			System.out
					.println("Variable Packet size, offset :    " + lengthPos);
		}
		if (hasCRC) {
			System.out
					.println("CRC poly:    0x" + Integer.toHexString(CRCpoly));
			System.out.println("CRC pos:     " + CRCpos);
		} else {
			System.out.println("No CRC");
		}
	}
	
	/**
	 * creates config packet to send to BTnode
	 * Packet description, little-endian
	 * 1 byte type: 1 = 'c'
	 * 4 byte freq:
	 * 1 byte SOP length
	 * 2 byte SOP:
	 * 1 byte has fixed size: 1/0
	 * 1 byte packet size / header size
	 * 1 byte lengthPos
	 * 1 byte lenghtOffset to read length
	 * 1 byte CRC poly length 0/1/2
	 * 1 byte CRC pos: 255 for no crc (not implemented here)
	 * 2 byte CRC poly
	 * @return
	 */
	public byte[] serialize() {
		byte buffer[] = new byte[16];
		int i = 0;
		buffer[i++] = (byte) 'c';
		buffer[i++] = (byte) (frequency & 0xff);
		buffer[i++] = (byte) (frequency >> 8 & 0xff);
		buffer[i++] = (byte) (frequency >> 16 & 0xff);
		buffer[i++] = (byte) (frequency >> 24);
		if (SOP > 0xff) {
			buffer[i++] = 2;
		} else {
			buffer[i++] = 1;
		}
		buffer[i++] = (byte) (SOP & 0xff);
		buffer[i++] = (byte) (SOP >> 8 & 0xff);
		
		if (fixedSize) {
			buffer[i++] = (byte)  1;
		} else {
			buffer[i++] = (byte)  0;
		}
		buffer[i++] = (byte)  packetSize; // or header size, if variable size
		buffer[i++] = (byte)  lengthPos;
		buffer[i++] = (byte)  lengthOffset;

		if (CRCpoly > 0xff) {
			buffer[i++] = 2;
		} else {
			buffer[i++] = 1;
		}
		buffer[i++] = (byte)  CRCpos;
		buffer[i++] = (byte) (CRCpoly & 0xff);
		buffer[i++] = (byte) (CRCpoly >> 8 & 0xff);

		return buffer;
	}
}
