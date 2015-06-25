import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.HashMap;

/*
 * This thread is to analysis packet received and act respectively
 * */
public class PacketAnalysisThread extends Thread {
	private bfclient client;
	private Packet packet;
	private FilePacket filePacket;
	private String senderAddressFromPacket;

	// constructor, pass in client and received packet, act respectively according to received packet
	public PacketAnalysisThread(bfclient client, DatagramPacket datagramPacket){
		this.client = client;
		byte[] buffer = datagramPacket.getData();
		senderAddressFromPacket = datagramPacket.getAddress().toString().substring(1);
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		int msgType = byteBuffer.getInt(0);
		
		if (msgType == 4) {
			filePacket = new FilePacket(buffer);
			analysisFile(buffer);
		} 
		else {
			String senderIp = datagramPacket.getAddress().toString().substring(1);
			packet = new Packet(buffer);
			start();
		}
	}
		
	
	public void run(){
		String senderAddress = packet.getSenderAddress();
		
		// set my ip if it has not been set before
		if (!client.getAddress().contains(":")) {
			client.setAddress(packet.getDesAddress().toString());
		}
		
		
		
		// if the sender address has not been set before, ignore, just add new neighbor
		if (!packet.getSenderAddress().contains(":")) {
			// if it is a new node, add to neighbors, if not, update lastSendTime, update status
			if (senderAddressFromPacket.equals("127.0.0.1")){
				senderAddress = client.getAddress()+":"+senderAddress;
			}
			else {
				senderAddress = senderAddressFromPacket+":"+senderAddress;
			}
			if (client.getNeighbors().containsKey(senderAddress)){
				client.getNeighbors().get(senderAddress).status = true;
				client.getNeighbors().get(senderAddress).lastSendTime = System.currentTimeMillis();
			}
			else {
				client.addNeighbor(senderAddress, packet.getDistanceVector().get(client.getAddress()));
			}
			
		} else {
			
			// if it is a new node, add to neighbors, if not, update lastSendTime, update status
			if (client.getNeighbors().containsKey(senderAddress)){
				client.getNeighbors().get(senderAddress).status = true;
				client.getNeighbors().get(senderAddress).lastSendTime = System.currentTimeMillis();
			}
			else {
				
				client.addNeighbor(senderAddress, packet.getDistanceVector().get(client.getAddress()));
			}
			/* Define message type:
			 * type 0: transfer distance vector
			 * type 1: link down (LINKDOWN command)
			 * type 2: link up (LINKUP command)
			 * type 3: change cost (CHANGECOST command)
			 * type 4: file transfer (FILE command)
			*/
			if (packet.getMsgType() == 0) {
				// if the distance vector is updated, send all neighbor the new distance vector
				if (updateDistanceVector(packet.getDistanceVector())){
					client.getSendThread().sendDistanceVector();
				}
			} else if (packet.getMsgType() == 1) {
				if (linkDown(senderAddress)){
					client.getSendThread().sendDistanceVector();
				}
			} else if (packet.getMsgType() == 2) {
				if (linkUp(senderAddress)){
					client.getSendThread().sendDistanceVector();
				}
			} else if (packet.getMsgType() == 3) {
				if (changeCost(senderAddress, packet.getCost())){
					client.getSendThread().sendDistanceVector();
				}
			} 
		}
	}

	// use Bellman Ford Algorithm to update the distance Vector
	// return a boolean to indicate if the distance vector is updated
	private synchronized boolean updateDistanceVector(HashMap<String, Float> neighborVector) {
		HashMap<String, Float> myVector = new HashMap<String, Float>(client.getVector());
		HashMap<String, Neighbor> neighbors = new HashMap<String, Neighbor>(client.getNeighbors());
		boolean updated = false;
		String senderAddress = packet.getSenderAddress();
		
		// check if first hop has changed value
		for (String key : myVector.keySet()){
			if (client.getHop().containsKey(key)){
				String addr = client.getHop().get(key);

				if (addr.equals(senderAddress) && !senderAddress.equals(key)){
					// if the link is broken
					if (!neighborVector.containsKey(key) && myVector.get(key)!=Float.POSITIVE_INFINITY){
						myVector.put(key, Float.POSITIVE_INFINITY);
						client.removeFirstHopValue(key);
						updated = true;
					}
					// if the value is changed
					if (neighbors.get(senderAddress) != null && neighborVector.containsKey(key)){
						float newCost = neighbors.get(senderAddress).cost + neighborVector.get(key);
						if (newCost != myVector.get(key)){
							myVector.put(key, newCost);
							// first hop doesn't change
							updated = true;
						}
					}
					
				}
			}
		}
		
		// iterate through the received distance vector
		for(String address : neighborVector.keySet()){
			// if the neighbor in neighbor's DV is the current client
			if (address.equals(client.getAddress())){
				
				if (myVector.containsKey(senderAddress)){

					
					// check if the cost between sender neighbor and current neighbor is 
					// smaller than that in myVector, if so, update
					if (client.getNeighbors().get(senderAddress).cost < myVector.get(senderAddress)){
						myVector.put(senderAddress, client.getNeighbors().get(senderAddress).cost);
						client.getHop().put(senderAddress, senderAddress);

						updated = true;
					}
				} else {
					myVector.put(senderAddress, neighborVector.get(address));
					client.getHop().put(senderAddress, senderAddress);

					updated = true;
				}
				// skip the rest
				continue;
			}
			
			// poison reverse or unreachable or dead
			if (neighborVector.get(address) == Float.POSITIVE_INFINITY){
				continue;
			}
			
			// regular update
			float newCost = neighbors.get(senderAddress).cost + neighborVector.get(address);
			if (myVector.containsKey(address)) {

				if (newCost < myVector.get(address)) {
					myVector.put(address, newCost);
					client.getHop().put(address,senderAddress);
					updated = true;
				} 
			} else {
				myVector.put(address, newCost);
				client.getHop().put(address,senderAddress);

				updated = true;

			}
		}

	
		if (updated){
			client.setVector(myVector);
		}
		return updated;
	}
	
	// received link down packet, remove address from dv and first hop
	// return true if such address is a neighbor
	public synchronized boolean linkDown(String address){
		
        if(client.getNeighbors().containsKey(address)) {
        	client.getNeighbors().get(address).status = false;
            client.removeFromDV(address);
            client.removeFirstHop(address);
            client.removeFirstHopValue(address);
            
            HashMap<String, Neighbor> neighbors = new HashMap<String, Neighbor>(client.getNeighbors());
            HashMap<String, Float> distanceVector = new HashMap<String, Float>(client.getVector());
            
            // update distance vector
 			for (String key : neighbors.keySet()){
 				if (neighbors.get(key).status && !distanceVector.containsKey(key)){
 					distanceVector.put(key, neighbors.get(key).cost);
 					client.getHop().put(key, key);
 				}
 			}
         	client.setVector(distanceVector);	

            return true;
        }
        return false;
	}
	
	// received link up packet, add address to dv and first hop
	// return true if such address is a neighbor
	public synchronized boolean linkUp(String address){
        if(client.getNeighbors().containsKey(address)) {
        	client.getNeighbors().get(address).status = true;
            
            HashMap<String, Neighbor> neighbors = new HashMap<String, Neighbor>(client.getNeighbors());
            HashMap<String, Float> distanceVector = new HashMap<String, Float>(client.getVector());
            
            // update distance vector
 			for (String key : neighbors.keySet()){
 				if (neighbors.get(key).status){
 					if (!distanceVector.containsKey(key)){
	 					distanceVector.put(key, neighbors.get(key).cost);
	 					client.getHop().put(key, key);
 					} else {
 						if (neighbors.get(key).cost < distanceVector.get(key)){
 							distanceVector.put(key, neighbors.get(key).cost);
 		 					client.getHop().put(key, key);
 						}
 					}
 				}
 			}
 			
         	client.setVector(distanceVector);	

            return true;
        }
        return false;
	}

	// receive change cost packet, change the cost, distance vector
	// return true if such address is an active neighbor
	public synchronized boolean changeCost(String address, float newCost){
		boolean activeNeighbor = false;
        if(client.getNeighbors().containsKey(address)) {
        	if (client.getNeighbors().get(address).status){
        		activeNeighbor = true;
	        	float oldCost = client.getNeighbors().get(address).cost;
	        	client.getNeighbors().get(address).cost = newCost;
	        	
	            HashMap<String, Float> distanceVector = new HashMap<String, Float>(client.getVector());
	            HashMap<String, String> firstHop = new HashMap<String, String>(client.getHop());
	            HashMap<String, Neighbor> neighbors = new HashMap<String, Neighbor>(client.getNeighbors());
	            

	         			
	            // change the distance of all routers with this neighbor as first hop
	 			for (String key : firstHop.keySet()){
	 				if (firstHop.get(key).equals(address)){
	 					float newDist = distanceVector.get(key) - oldCost + newCost;
	 					boolean updated = false;
	 					if (neighbors.containsKey(key)){
	 						if (neighbors.get(key).status == true && neighbors.get(key).cost < newDist){
	 							distanceVector.put(key, neighbors.get(key).cost);
	 							client.getHop().put(key, key);
	 							updated = true;
	 						}
	 					} 
	 					if (!updated){
	 	 					distanceVector.put(key, newDist);
	 	 					// first hop remain the same
	 					}
	 				}
	 			}
	         	
	            // if the new cost is smaller than the distance in distance vector, change to the new cost
				if (distanceVector.get(address) > newCost){
					distanceVector.put(address, newCost);
					client.getHop().put(address, address);
				}
				
	         	client.setVector(distanceVector);	
	        }
        }
        return activeNeighbor;
	}
	
	// analysis received file fragment
	public void analysisFile(byte[] data){
		// if this client is the destination of the file
		if (filePacket.destinationAddress.equals(client.getAddress())){
			client.file.put(filePacket.seqNo, filePacket.file);
			// if all fragments are received
			if (client.file.size() == filePacket.totalSeqNo){
				System.out.println("Packet Received!"
							+ "Source = " + filePacket.sourceAddress + "\n"
							+ "Destination = " + client.getAddress() 
							+ "\nFile Received Successfully!");
				
				File file = new File("RECEIVED-"+filePacket.filename);
				 try {
					 if(!file.exists()) {
						file.createNewFile();
					} 
					FileOutputStream stream = new FileOutputStream(file, false);
					try {
						for (int i = 0; i < client.file.size(); i ++){
							stream.write(client.file.get(i));
						}
					} finally {
						stream.close();
						client.file.clear();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} 
				
			}
		} else {
			if (filePacket.seqNo == 0){
				client.getSendThread().sendFragment(true, data, filePacket.sourceAddress, filePacket.destinationAddress);
			} else {
				client.getSendThread().sendFragment(false, data, filePacket.sourceAddress, filePacket.destinationAddress);

			}
		}
	}

}
