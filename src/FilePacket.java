import java.nio.ByteBuffer;


/*
 * This class design the transmission protocol of a transfer file packet
 * Transmission protocol is designed as follows: total: < 58 + filename length + 512 Byte
 * | message type | header length | sequence number |  total # seq  | source address (ip:port)  | destination address (ip:port) | file name |   file fragment       |
 * |  int 4 Byte  |  int: 4 Byte  |  int: 4 Byte    |  int: 4 Byte  |   String: 21 Byte         |        String: 21 Byte        |  String   |	  < 512 Byte        |
 * message type is 4 for all file transfer message
 */
public class FilePacket {
	public int seqNo;
	public int totalSeqNo;
	public String sourceAddress;
	public String destinationAddress;
	public String filename;
	public byte[] file;
	
	// constructor: parse in each information separately, for packet sending out
	public FilePacket(int seqNo, int totalSeqNo, String sourceAddress, String destinationAddress, String filename, byte[] file){
		this.seqNo = seqNo;
		this.totalSeqNo = totalSeqNo;
		this.sourceAddress = sourceAddress;
		this.destinationAddress = destinationAddress;
		this.filename = filename;
		this.file = file;
	}
	
	// constructor: parse in all information as a byte array, for packet received
	public FilePacket(byte[] buffer) {
		toSeparateInfo(buffer);
	}

	// combine all information into a byte array
	public byte[] toByteArray() {
		ByteBuffer buffer;
		buffer = ByteBuffer.allocate(58 + filename.length() + file.length);
		buffer.putInt(4);
		buffer.putInt(58 + filename.length());
		buffer.putInt(seqNo);	
		buffer.putInt(totalSeqNo);
		buffer.put(Packet.addBlank(sourceAddress, 21).getBytes());
		buffer.put(Packet.addBlank(destinationAddress, 21).getBytes());
		buffer.put(filename.getBytes());
		buffer.put(file);

		return buffer.array();
	}
	
	// convert a byte array to separate information, and store them in global variables
	public void toSeparateInfo(byte[] info){
		ByteBuffer buffer = ByteBuffer.wrap(info);
		int headerLength = buffer.getInt(4);
		seqNo = buffer.getInt(8);
		totalSeqNo = buffer.getInt(12);
		
		// get source address
		buffer.position(16);
		buffer.limit(37);
		byte[] array = new byte[buffer.remaining()];
		buffer.get(array);
		sourceAddress = new String(array);
		sourceAddress = sourceAddress.trim();
				
		// get destination address
		buffer.position(37);
		buffer.limit(58);
		array = new byte[buffer.remaining()];
		buffer.get(array);
		destinationAddress = new String(array);
		destinationAddress = destinationAddress.trim();
		
		// get filename
		buffer.position(58);
		buffer.limit(headerLength);
		array = new byte[buffer.remaining()];
		buffer.get(array);
		filename = new String(array);
		// get file
		buffer.position(headerLength);
		buffer.limit(1024);
		array = new byte[buffer.remaining()];
		buffer.get(array);
		file = array;
				
	}
	
}
