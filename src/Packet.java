import java.nio.ByteBuffer;
import java.util.HashMap;

/*
 * This class design the transmission protocol of a packet
 * Transmission protocol is designed as follows: total: (25*n + 54) Byte
 * | total length | message type  |      cost     | sender address (ip:port) | destination address (ip:port) | distance vector |
 * |  int: 4 Byte |  int: 4 Byte  | float: 4 Byte |  String: 21 Byte         |     String: 21 Byte           |     25*n Byte   |
 * 
 * Note: "cost" is designed only for command CHANGECOST
 * Distance vector:
 * | destination1 (ip:port) |    cost 1     | destination2 (ip:port) |    cost 2     |  ...
 * |   String: 21 Byte      | float: 4 Byte |     String: 21 Byte    | float: 4 Byte |  ...
 * 
 * Define message type:
 * type 0: transfer distance vector
 * type 1: link down (LINKDOWN command)
 * type 2: link up (LINKUP command)
 * type 3: change cost (CHANGECOST command)
 * */
public class Packet {
	private int msgType;
	private float cost;
	private String senderAddress;
	private String receiverAddress;
	private HashMap<String, Float> distanceVector;
	
	// constructor: parse in each information separately, for packet sending out
	public Packet(int msgType, float cost, String senderAddress, String receiverAddress, 
			HashMap<String, Float> distanceVector){
		this.msgType = msgType;
		this.cost = cost;
		this.senderAddress = senderAddress;
		this.receiverAddress = receiverAddress;
		this.distanceVector = distanceVector;
	}
	
	// constructor: parse in all information as a byte array, for packet received
	public Packet(byte[] buffer) {
		distanceVector = new HashMap<String, Float>();
		toSeparateInfo(buffer);
	}


	// combine all information into a byte array
	public byte[] toByteArray() {
		ByteBuffer buffer;
		if (distanceVector == null) {
			buffer = ByteBuffer.allocate(54);
			buffer.putInt(54);
		} else {
			buffer = ByteBuffer.allocate(25*distanceVector.size()+54);
			buffer.putInt(25*distanceVector.size()+54);
		}
		
		buffer.putInt(msgType);		
		buffer.putFloat(cost);
		buffer.put(addBlank(senderAddress, 21).getBytes());
		buffer.put(addBlank(receiverAddress, 21).getBytes());
//		System.out.println("-------create packet : put receiver address:"+ addBlank(receiverAddress, 21));

		if (distanceVector != null) {
			for (String key : distanceVector.keySet()){
				buffer.put(addBlank(key, 21).getBytes());
				buffer.putFloat(distanceVector.get(key));
//				System.out.println("se" + key + ": "+ distanceVector.get(key));
			}
		}
		
		return buffer.array();
	}
	
	// convert a byte array to separate information, and store them in global variables
	public void toSeparateInfo(byte[] info){
		ByteBuffer buffer = ByteBuffer.wrap(info);
		int length = buffer.getInt(0);
		msgType = buffer.getInt(4);
		cost = buffer.getFloat(8);
		
		// get sender address
		buffer.position(12);
		buffer.limit(33);
		byte[] array = new byte[buffer.remaining()];
		buffer.get(array);
		senderAddress = new String(array);
		senderAddress = senderAddress.trim();
				
		// get destination address
		buffer.position(33);
		buffer.limit(54);
		array = new byte[buffer.remaining()];
		buffer.get(array);
		receiverAddress = new String(array);
		receiverAddress = receiverAddress.trim();
		
		// get distance vector		
		int start = 54;
		while (start < length){
			buffer.position(start);
			buffer.limit(start + 21);
			array = new byte[buffer.remaining()];
			buffer.get(array);
			String address = new String(array);
			buffer.limit(length);
			distanceVector.put(address.trim(), buffer.getFloat(start+21));
			start += 25;
		}
//		for (String key: distanceVector.keySet()){
//			System.out.println("re " + key+": "+ distanceVector.get(key));
//		}
	}
	
	// add blank after str to full the length
	public static String addBlank(String str, int length){
		String blank = "";
		for (int i = 0; i < length - str.length(); i ++){
			blank += " ";
		}
		return str + blank;
	}

	public int getMsgType(){
		return msgType;
	}
	public float getCost(){
		return cost;
	}
	public String getSenderAddress(){
		return senderAddress;
	}
	public String getDesAddress(){
		return receiverAddress;
	}
	public HashMap<String, Float> getDistanceVector(){
		return distanceVector;
	}
	

	
}
