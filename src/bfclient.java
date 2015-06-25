import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class bfclient {
	private final int MMS = 512;
	private int port;
	private String address;
	private long timeout; // milliseconds
	private boolean connected;
	private SendThread sendThread;
	private long lastSendTime;
	private Timer timer;
	
	private HashMap<String, Float> distanceVector; // key: ip:port, value: cost, for all routers that can connect
	private HashMap<String, Neighbor> neighbors; // key: ip:port, value: cost, for direct neighbors
	private HashMap<String, String> firstHop; // key: ip:port of a router, value: ip:port of the first hop to the neighbor
	public HashMap<Integer, byte[]> file;
	
	// constructor 
	public bfclient(int port, float timeout){
		address = "";
		this.port = port;
		address = String.valueOf(port);
		this.timeout = (long)timeout * 1000;
		connected = true;
		distanceVector = new HashMap<String, Float>();
		neighbors = new HashMap<String, Neighbor>();
		firstHop = new HashMap<String, String>();
		file = new HashMap<Integer, byte[]>();
	}
	
	// start running
	// this thread is to listen for incoming packets
	public void start(){
		runTimer(timeout);
		MyTimerTask timerTask = new MyTimerTask(true);
		timer.schedule(timerTask, 5000);

		try {
			DatagramSocket listenSocket = new DatagramSocket(port);
			
			
			// start receiving packets
			while(connected){
				byte[] info = new byte[1024];
				DatagramPacket packet = new DatagramPacket(info, info.length);
				listenSocket.receive(packet);
				// create a new thread to deal with the packet
				new PacketAnalysisThread(this, packet);
			}
			
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	/* A nested class MyTimerTask extends TimerTask
	 * To schedule distance vector sending to neighbors every timeout (with exception)
	 * */
	public class MyTimerTask extends TimerTask{
		private boolean first;
		
		public MyTimerTask(boolean first){
			this.first = first;
		}
		
		public void run() {
			if (first){
				sendThread.sendDistanceVector();
			} else {
				checkDeadNode();

				// If distance vector changes and send in the middle of a timeout, schedule a timerTask 
				// at delay timeout - lastSendTime
				long interval = System.currentTimeMillis() - lastSendTime;

				if (System.currentTimeMillis() - lastSendTime < timeout) {
					// schedule a new timerTask
					runTimer(timeout - interval);
				} else {
					sendThread.sendDistanceVector();
					// schedule a new timerTask
					runTimer(timeout);
				}
			}
			
		}
	}
	
	
	// check dead nodes
	public synchronized void checkDeadNode(){
		for (String key : neighbors.keySet()){
			if (neighbors.get(key).status){
				if (System.currentTimeMillis() - neighbors.get(key).lastSendTime > 3 * timeout){
					// the node is assumed dead
					System.out.println(key+" is dead");
					System.out.print(">");
					linkDown(key);
				}

			}
		}
	}
	
	// a timer to control the timeout
	public void runTimer(long time){
		timer = new Timer();
		MyTimerTask timerTask2 = new MyTimerTask(false);
		timer.schedule(timerTask2, time);
	}
	 
	
	
	// remove all entries with key or value = address
	public synchronized void removeFirstHop(String address){

		Iterator<String> iterator = firstHop.keySet().iterator();

        while(iterator.hasNext()) {
            String addr = iterator.next();
            if(addr.equals(address)) {
            	iterator.remove();
            }
            
            
        }
	}
	
	public synchronized void removeFirstHopValue(String address){
		Iterator<String> iterator = firstHop.keySet().iterator();

        while(iterator.hasNext()) {
            String addr = iterator.next();
        	if(firstHop.get(addr).equals(address)){
        		iterator.remove();        	
        	}
        }
	}
	// remove an entry with key = address or first hop = address from the distance vector hash map
	public synchronized void removeFromDV(String address){

		Iterator<String> iterator = distanceVector.keySet().iterator();
        while(iterator.hasNext()) {
            String addr = iterator.next();
            boolean remove = false;
            if(addr.equals(address)){
            	remove = true;
            }
            if (firstHop.containsKey(addr)){
            	if(firstHop.get(addr).equals(address)){
            		remove = true;
            	}
            }
            if (remove){
            	iterator.remove();
            }
        }
        
	}

	
	// add neighbor address and Neighbor instance to hashmap neighbor
	public synchronized void addNeighbor(String address, float cost) {

		if (!neighbors.containsKey(address)){
			Neighbor router = new Neighbor(cost);
			neighbors.put(address, router);
		}
	}
	
	// handle LINKDOWN command
	public synchronized void linkDown(String address) {
		if (neighbors.containsKey(address)){
			neighbors.get(address).status = false;
			removeFromDV(address); 
			removeFirstHop(address);
			removeFirstHopValue(address);
			
			// update distance vector
 			for (String key : neighbors.keySet()){
 				if (neighbors.get(key).status && !distanceVector.containsKey(key)){
 					distanceVector.put(key, neighbors.get(key).cost);
 					firstHop.put(key, key);
 				}
 			}
 			sendThread.sendLinkdown(address);
 			sendThread.sendDistanceVector();
 			
		} else {
			System.out.println("Neighbor doesn't exist!");
		}
	}

	// handle LINKUP command
	public synchronized void linkUp(String address){
		if (neighbors.containsKey(address)){
			neighbors.get(address).status = true;
			
			// add to dv if it is not already in dv
			if (!distanceVector.containsKey(address)){
				distanceVector.put(address, neighbors.get(address).cost);
				firstHop.put(address, address);
			} else {
				if (neighbors.get(address).cost < distanceVector.get(address)){
					distanceVector.put(address, neighbors.get(address).cost);
					firstHop.put(address, address);
				} 
			}
			sendThread.sendLinkup(address);
			sendThread.sendDistanceVector();
			
		} else {
			System.out.println("Neighbor doesn't exist!");
		}
	}
	
	// handle CHANGECOST command
	public synchronized void changeCost(String address, float newCost){
		
		if (neighbors.containsKey(address)){
			if (neighbors.get(address).status){
				float oldCost = neighbors.get(address).cost;
				neighbors.get(address).cost = newCost;
	

				
				 // change the distance of all routers with this neighbor as first hop
	 			for (String key : firstHop.keySet()){
	 				if (firstHop.get(key).equals(address)){
	 					float newDist = distanceVector.get(key) - oldCost + newCost;
	 					boolean updated = false;
	 					if (neighbors.containsKey(key)){
	 						if (neighbors.get(key).status == true && neighbors.get(key).cost < newDist){
	 							distanceVector.put(key, neighbors.get(key).cost);
	 							firstHop.put(key, key);
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
					firstHop.put(address, address);
				}
				
				sendThread.sendChangeCost(address, newCost);
				sendThread.sendDistanceVector();
			}	
		} else {
			System.out.println("Neighbor doesn't exist!");
		}
	}
	
	// handle SHOWRT command
	public synchronized void showRT(){
		DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		System.out.println("<" + df.format(new Date()) + "> Distance Vector for " + address);
		System.out.printf("%-30s%-15s%s\n","Router","Cost","First Hop");
		
		for (String key: distanceVector.keySet()){
			if (distanceVector.get(key) == Float.POSITIVE_INFINITY){
				continue;
			}
			String hop = firstHop.get(key);
			System.out.printf("%-30s%-15s%s\n", key, distanceVector.get(key), hop);
			
		}
		System.out.println("------------------------------------------------------------");

	}
	// handle SHOWNEI command
	public synchronized void showNei(){
		DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		System.out.println("<" + df.format(new Date()) + ">Neighbor for " + address);
		System.out.printf("%-30s%-15s%s\n","Neighbor","Cost","Status");
		
		for (String key: neighbors.keySet()){
			System.out.printf("%-30s%-15s%s\n", key, neighbors.get(key).cost, neighbors.get(key).status);
			
		}
		System.out.println("------------------------------------------------------------");

	}
	// handle TRANSFER command
	public synchronized void transfer(String filename, String address){
		ArrayList<byte[]> fileList = new ArrayList<byte[]>();
        try {
            File file = new File(filename);
            FileInputStream input = new FileInputStream(file);
            BufferedInputStream stream = new BufferedInputStream(input);
            int byteNum = stream.available();
            while (byteNum > 0) {
                int length = Math.min(byteNum, MMS);
                byte[] buffer = new byte[length];
                stream.read(buffer, 0, buffer.length);
                String str = new String(buffer);
                byteNum -= buffer.length;
                fileList.add(buffer);
            }
            sendThread.sendFile(fileList, filename, address);
        } catch (Exception e) {
            System.out.println("File" + filename + "not found!");
        }
	}
	
	public void setLastSendTime(long time){
		lastSendTime = time;
	}
	
	public long getLastSendTime(){
		return lastSendTime;
	}
	
	public long getTimeout(){
		return timeout;
	}
	
	public int getPort(){
		return port;
	}
	public String getAddress(){
		return address;
	}
	
	public SendThread getSendThread(){
		return sendThread;
	}
	public HashMap<String, Neighbor> getNeighbors(){
		return neighbors;
	}
	public HashMap<String, Float> getVector(){
		return distanceVector;
	}
	public HashMap<String, String> getHop(){
		return firstHop;
	}
	public void setAddress(String address){
		this.address = address;
	}
	public synchronized void setVector(HashMap<String, Float> distanceVector){
		this.distanceVector = distanceVector;
	}


	// read in information from file, each line as a list, with strings split by space as items in list
	public static ArrayList<ArrayList<String>> fromFile(String filePath) {

		String next = null;
		File f = new File(filePath);
		InputStreamReader reader;
		ArrayList<ArrayList<String>> info = new ArrayList<ArrayList<String>>();
		try {
			reader = new InputStreamReader(new FileInputStream(f));
			BufferedReader br = new BufferedReader(reader);
			while ((next = br.readLine()) != null) {
				ArrayList<String> list = new ArrayList<String>(Arrays.asList(next.split("\\s+")));
				info.add(list);
			}
			br.close();

		} catch (FileNotFoundException e) {
			System.out.println("File not found!");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return info;
	}
	
	public static void main(String args[]){
		if (args.length != 1){
			System.out.println("Use: bfclient file_path");
		}
		// get info from file
		ArrayList<ArrayList<String>> info = fromFile(args[0]);
		bfclient client = null;
		try {
			client = new bfclient(Integer.parseInt(info.get(0).get(0)), Float.parseFloat(info.get(0).get(1)));
			for (int i = 1; i < info.size(); i ++){
				client.addNeighbor(info.get(i).get(0), Float.parseFloat(info.get(i).get(1)));
				client.distanceVector.put(info.get(i).get(0), Float.parseFloat(info.get(i).get(1)));
				client.firstHop.put(info.get(i).get(0), info.get(i).get(0));
			}
		} catch (IndexOutOfBoundsException e){
			System.out.println("invalid file format");
			System.exit(0);
		}
		
		client.sendThread = new SendThread(client);
		new ReadKeyboardThread(client);
		
		// start listening thread
		client.start();

	}



}
