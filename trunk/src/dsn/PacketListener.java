package dsn;

public interface PacketListener {
	void handlePacket( int len, byte data[]);
}
