package dsn;
import java.io.IOException;

import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.microedition.io.Connector;

import packetparser.PDL;
import packetparser.PhyConfig;


public class DSNConnector extends Thread implements DiscoveryListener {

	// COD detection not working properly on mac os x
	// don't filter on COD right now
	@SuppressWarnings("unused")
	private static final int SNIF_COD_MINOR = 184;
	@SuppressWarnings("unused")
	private static final int SNIF_COD_MAJOR = 3;

	private L2CAPConnection con;
	
	private final String btPrefix = "00043F000";

	private DiscoveryAgent agent;

	private DSNConnector view;
	
	private String snifGateway = null;

	int  timeSyncRound = 0;

	long lastTimestamp = 0;
	
	private PacketListener packetListener;

	private static PhyConfig snifConfig;

	public void init() {
		view = this;
		try {
			LocalDevice local = LocalDevice.getLocalDevice();
			agent = local.getDiscoveryAgent();
		} catch (Exception ex) {
		}
	}
	
	public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
		String address = btDevice.getBluetoothAddress();
		
		if (snifGateway != null) return;
		
		if (address.startsWith(btPrefix)   &&
//			cod.getServiceClasses() == 0   &&
//			cod.getMajorDeviceClass() == SNIF_COD_MAJOR &&
//			cod.getMinorDeviceClass() == SNIF_COD_MINOR &&
			true	) {
			writeMessage("BTNode " + address +" , Service: "+cod.getServiceClasses()+", Major: "
					+ cod.getMajorDeviceClass() + ", Minor: " + cod.getMinorDeviceClass());
			snifGateway = address;
		}
	}
	
	public L2CAPConnection getConnection() {
		return con;
	}
	
	public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
		// TODO Auto-generated method stub

	}

	public void serviceSearchCompleted(int transID, int respCode) {
		// TODO Auto-generated method stub

	}

	public void inquiryCompleted(int discType) {
		// TODO Auto-generated method stub

	}
	
	private void writeMessage(String s) {
		System.out.println(s);
	}	
	
	public void connect() {
		while (true) {
			view.writeMessage("Start discovery...");
			// Discover BTNodes
			try {
			    // start inquiry
			    agent.startInquiry(DiscoveryAgent.GIAC, this);
			    con = getConnection();

			    // wait for max 10 seconds for inq result
			    int i = 0;
			    while (snifGateway == null && i < 100) {
				    Thread.sleep(100);
					i++;
				}
			    // stop inquiry
				view.writeMessage("Stop discovery...");
			    agent.cancelInquiry(this);

			    // wait a bit more
			    Thread.sleep(200);

			    // try to connect
			    con = (L2CAPConnection) Connector.open("btl2cap://" + snifGateway + ":1011");

			    // connected !!!
				writeMessage("Connected to DSN via BTnode " + snifGateway);
				return;
			}
			catch (IOException ioex) {
				view.writeMessage("Couldn't establish connection.\nPlease retry.");
			}
			catch (InterruptedException iex) {
			}
		}
	}
	
	void sendConfig(PhyConfig config) throws IOException {
		byte config_data [] = config.serialize();
		if (con != null) {
			con.send(config_data);
		}
	}

	private void receivePacket() throws IOException {
		byte data[] = new byte[255];
		int len = con.receive(data);
		if (packetListener != null) {
			packetListener.handlePacket(len, data);
		}
	}
	
	private void sendTimeStamp() throws IOException {
		byte packet [] = { 't', 0};
		packet[1] = (byte) timeSyncRound++; 
		con.send(packet);
		lastTimestamp = System.currentTimeMillis();
	}
	
	public void run() {
		int timeSyncIntervalMillis = 10000;
		lastTimestamp = System.currentTimeMillis();
		while (true) {
			try {
				// check, if timestamp should be sent
				if (System.currentTimeMillis() - lastTimestamp > timeSyncIntervalMillis) {
					sendTimeStamp();
					sendConfig(snifConfig);
				}
				// check for new packets
				else if (con.ready() ) {
					receivePacket();
				}
				// sleep for 10 ms
				else {
					try {
						Thread.sleep( 10 );
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void registerPacketListener( PacketListener listener ) {
		packetListener = listener;
	}

	public static PhyConfig getSnifConfig() {
		return snifConfig;
	}

	public void setSnifConfig(PhyConfig snif_config) {
		DSNConnector.snifConfig = snif_config;
	}

	public static void main(String[] args) throws Exception {
		PDL parser = PDL.readDescription( "packetdefinitions/ewsn07.h");
		
		DSNConnector connector = new DSNConnector();
		parser.dumpDescription();

		connector.setSnifConfig (parser.getSnifferConfig() );
		System.out.println();
		connector.init();
		connector.connect();
		// connector.run();
		connector.start();
	}

	public String getSnifGateway() {
		return snifGateway;
	}
}
