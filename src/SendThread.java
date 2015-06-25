
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

/*
 * A thread to send packets out
 * */
public class SendThread extends Thread{
	private bfclient client;
	private DatagramSocket socket;
	
	// constructor
	public SendThread(bfclient client){
		this.client = client;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		start();
		
	}

	public void run(){
		sendDistanceVector();
		
	}
	
	// send distance vector to all neighbors
	public void sendDistanceVector() {
		// update lastSendTime
		client.setLastSendTime(System.currentTimeMillis());
		
		HashMap<String, Neighbor> neighbors = new HashMap<String, Neighbor>(client.getNeighbors());
		HashMap<String, String> firstHop = new HashMap<String, String>(client.getHop());
		HashMap<String, Float> distanceVector;
		
		for (String address : neighbors.keySet()){
			distanceVector = new HashMap<String, Float>(client.getVector());
			
			// skip routers that are down
			if (!neighbors.get(address).status){
				continue;
			}
			
			// poison reverse
			for (String key: distanceVector.keySet()) {
				if (firstHop.get(key) != null){
					if (firstHop.get(key).equals(address) && !address.equals(key)) {
						distanceVector.put(key, Float.POSITIVE_INFINITY);
					}
				}
			}
			String ip = address.split(":")[0];
			int port = Integer.parseInt(address.split(":")[1]);
		
			// create new packet
			
			Packet packet = new Packet(0, (float) 0, client.getAddress(), address, distanceVector);
			byte[] sendData = packet.toByteArray();
			try {
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(ip), port);
				socket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	// send LINKDOWN message 
	public void sendLinkdown(String address){
		Packet packet = new Packet(1, (float)0, client.getAddress(), address, null);
		byte[] sendData = packet.toByteArray();
		try {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(address.split(":")[0]), Integer.parseInt(address.split(":")[1]));
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// send LINKUP message
	public void sendLinkup(String address){
		Packet packet = new Packet(2, (float)0, client.getAddress(), address, null);
		byte[] sendData = packet.toByteArray();
		try {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(address.split(":")[0]), Integer.parseInt(address.split(":")[1]));
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// send CHANGECOST message
	public void sendChangeCost(String address, float cost){
		Packet packet = new Packet(3, cost, client.getAddress(), address, null);
		byte[] sendData = packet.toByteArray();
		try {
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(address.split(":")[0]), Integer.parseInt(address.split(":")[1]));
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// send all fragmented file to the next hop
	public void sendFile(ArrayList<byte[]> fileList, String filename, String destinationAddress){
		String receiverAddress = client.getHop().get(destinationAddress);
		int totalSeqNo = fileList.size();
		
		if (fileList != null){
			for (int i = 0; i < fileList.size(); i ++){
				FilePacket packet = new FilePacket(i, totalSeqNo, client.getAddress() , destinationAddress, filename, fileList.get(i));
				byte[] sendData = packet.toByteArray();
				try {
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(receiverAddress.split(":")[0]), Integer.parseInt(receiverAddress.split(":")[1]));
					socket.send(sendPacket);

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("Next hop = " + receiverAddress + "\n" 
					+ "File Sent Successfully!");

		}
		
	}
	
	// forward the fragment to the next hop
	public void sendFragment(boolean print, byte[] file, String sourceAddress, String destinationAddress){
		String receiverAddress = client.getHop().get(destinationAddress);
		try {
			DatagramPacket sendPacket = new DatagramPacket(file, file.length, InetAddress.getByName(receiverAddress.split(":")[0]), Integer.parseInt(receiverAddress.split(":")[1]));
			socket.send(sendPacket);
			
			if (print){
				System.out.println( "Source = " + sourceAddress + "\n" 
						+ "Destination = "+ destinationAddress + "\n" 
						+ "Next hop = " + receiverAddress + "\n");

			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
