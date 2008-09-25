import java.io.IOException;

import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.L2CAPConnection;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;

import javax.microedition.io.Connector;

public class BTnodeL2CAPTest extends Thread implements DiscoveryListener {
	private L2CAPConnection con;
	
	private final String btPrefix = "00043F000";

	private DiscoveryAgent agent;

	private String snifGateway;
	
	long lastTimestamp = 0;

	private int timeSyncRound;

	private long timeSyncIntervalMillis = 200;

	long packetCounter = 0;

	public void init() {
		try {
			LocalDevice local = LocalDevice.getLocalDevice();
			agent = local.getDiscoveryAgent();
		} catch (Exception ex) {
		}
	}
	
	public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
		String address = btDevice.getBluetoothAddress();
		
		if (address.startsWith(btPrefix)  ) {
			System.out.println("BTNode " + address +" , Service: "+cod.getServiceClasses()+", Major: "
			+ cod.getMajorDeviceClass() + ", Minor: " + cod.getMinorDeviceClass());
			snifGateway = address;
		} else {
			System.out.println("Other Device " + address +" , Service: "+cod.getServiceClasses()+", Major: "
			+ cod.getMajorDeviceClass() + ", Minor: " + cod.getMinorDeviceClass());
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
	
	public void connect() {
		while (true) {
			System.out.println("Start discovery...");
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
				System.out.println("Stop discovery...");
			    agent.cancelInquiry(this);

			    // wait a bit more
			    Thread.sleep(200);

			    // try to connect
			    con = (L2CAPConnection) Connector.open("btl2cap://" + snifGateway + ":1011");

			    // connected !!!
				System.out.println("Connected to DSN via BTnode " + snifGateway);
				return;
			}
			catch (IOException ioex) {
				System.out.println("Couldn't establish connection.\nPlease retry.");
			}
			catch (InterruptedException iex) {
			}
		}
	}

	private void receivePacket() throws IOException {
		byte data[] = new byte[255];
		con.receive(data);
		System.out.print(packetCounter++);
		if (packetCounter % 50 == 49) System.out.println();
	}

	private void sendTimeStamp() throws IOException {
		byte packet [] = { 't', 0};
		packet[1] = (byte) timeSyncRound++; 
		con.send(packet);
		lastTimestamp = System.currentTimeMillis();
		System.out.println("Timestamp");
	}
	
	public void run() {
		lastTimestamp = System.currentTimeMillis();
		// sendConfig();
		while (true) {
			try {
				// check, if timestamp should be sent
				if (System.currentTimeMillis() - lastTimestamp > timeSyncIntervalMillis) {
					sendTimeStamp();
					sendTimeStamp();
				}
				
				if (con.ready() ) {
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

	public static void main(String[] args) throws Exception {
		BTnodeL2CAPTest connector = new BTnodeL2CAPTest();
		connector.init();
		connector.connect();
		connector.run();
	}
}
