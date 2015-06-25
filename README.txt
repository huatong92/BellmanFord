Name: Hua Tong
UNI: ht2334

===============================!!!!NOTE!!!!=============================
1. My TRANSFER command works great with .txt files, but does not work properly with image files. It shows that information is not properly stored.
2. DO NOT USE 127.0.0.1 MIXED WITH REAL IP ADDRESS. USE ONLY REAL IP ADDRESS IN CONF FILE, PLEASE.
3. The received file is named “RECEIVED-”+ORIGINAL_FILENAME.
4. File transfer destination, receiver, next hop will only display once for the same file.
5. CLOSE will not have immediate effect. After 3 timeout, other nodes will know. 
6. Sometimes a node need to wait for other nodes to transfer DV to update to the right number. Please wait for some time. 
7. I did part A and B.

============================General Description==========================
I designed a Multi-thread Distributed Bellman Ford Algorithm that is used to compute the shortest path between reachable hosts in a distributed host group. All file transmission is based on UDP.
The following are my classes, their implementation and function:

1. bfclient: this class contains my main class, on calling the main class, it will read in initialized port, timeout, and neighbor information. Then It will start a SendThread to send messages, a ReadKeyboardThread to read from keyboard, and a Timer to control timeout. Then this thread will use the port given to listen and pass in packages. Once a package is  received, a new PacketAnalysisThread will be created to analysis the packet.

2. SendThread: on initialization, this thread will will create a socket as a global variable, and use this socket to send packets all the time. Then it will immediately send the distance vector to all neighbors. This class also contains all functions relating to sending packets, such as send linkDown packets, send File, etc. 

3. ReadKeyboardThread: this thread is constantly reading from keyboard, after knowing what command it is, it calls specific functions. 
For command LINKDOWN, LINKUP, CHANGECOST, it first performs changes on its own distance vector, then send respective message to the linkdown/linkup/changecost neighbor, then send its distance vector to all neighbors.
For command CLOSE, it simply exit. After 3 timeout, other nodes will know. 
For command TRANSFER, it fragment the file, add sequence number, and send to the next hop.

4. Packet: this class design the transmission protocol of a packet
Transmission protocol is designed as follows: total: (25*n + 37) Byte
total length: int 4 Byte
message type: int 4 Byte
cost: float 4 Byte    //designed only for command CHANGECOST
sender port: int 4 Byte    //to know the sender id
destination address (ip:port): String 21 Byte   // for receiver to know its own address
distance vector: 25*n Byte 
| destination1 (ip:port) |    cost 1     | destination2 (ip:port) |    cost 2     |  ...
|   String: 21 Byte      | float: 4 Byte |     String: 21 Byte    | float: 4 Byte |  ...
Define message type:
type 0: transfer distance vector
type 1: link down (LINKDOWN command)
type 2: link up (LINKUP command)
type 3: change cost (CHANGECOST command)
This thread also contains functions to put these information to byte array, and to separate byte array to these information.

5. PacketAnalysisThread: this thread is to analyze a received packets. It determines which action to take, and call respective functions. For type 0: bellman-ford algorithm, also deal with poison reverse. For other type of message, take the same action as in sender side, if DV is changed, send to all neighbors.
If it is a send file packet, pass to FilePacket, and get the information, check if this client is destination, if it is, add the file to a list, then check if all packets are received, if it is, put the file together. If this is not the destination, find next hop, pass to it.

6. FilePacket: this class design the transmission protocol of a transfer file packet
Transmission protocol is designed as follows: total: < 58 + filename length + 512 Byte
message type: int 4 Byte
header length: int 4 Byte
sequence number: int 4 Byte
total # seq: int 4 Byte
source address (ip:port): String: 21 Byte
destination address (ip:port): String: 21 Byte
file name: String
file fragment: < 512 Byte 
message type is 4 for all file transfer message
This thread also contains functions to put these information to byte array, and to separate byte array to these information.

7. Neighbor: this class is to store information of a neighbor. 

=============================Specific implementation==========================
1. Timer: I keep a lastSendTime at bfclient as the last time this client send distance vector to all its neighbors. I also keep a lastSendTime for all neighbors to record the last time they send to this client. They are updated every time packets are send/received. My timerTask contains two tasks that are scheduled to do. One is to check is there is any dead node, one is to send distance vector to all neighbors. At very first, my timers schedule a timerTask for ‘timeout’ delay. After ‘timeout’, timerTask checks if (current time - lastSendTime < timeout ), if so, schedule a new timerTask at delay (timeout - (current time - lastSendTime)), if not, do the two tasks. This guarantees that the two tasks are done for at most every ‘timeout’.

2. All extra thread class is passes in with the parameter of the client itself, so that it can use functions from the bfclient. 

================================How To Run====================================
This is how to run:
makefile
java bfclient client0.txt
java bfclient client1.txt
. . .

================================Sample Command====================================
I have a command menu that will be displayed once started, and every time a wrong command is entered. 
============== Command Menu ==============
LINKDOWN ip port
LINKUP ip port
CHANGECOST ip port cost
SHOWRT
CLOSE
TRANSFER filename destination-ip port

Sample run:
LINKDOWN 127.0.0.1 11111
LINKUP 127.0.0.1 11111
CHANGECOST 127.0.0.1 11111 10
SHOWRT
CLOSE
TRANSFER a.txt 127.0.0.1 11111



