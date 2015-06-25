import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

/*
 * A thread to read information in from keyboard, and respond to these requests
 * */
public class ReadKeyboardThread extends Thread{
	private bfclient client;
	
	// constructor
	public ReadKeyboardThread(bfclient client){
		this.client = client;
		start();
	}
	
	public void run(){
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		printCommandMenu();
		try {
			while(true){
				try {
					System.out.print(">");
					String str = reader.readLine();
					if (str.startsWith("LINKDOWN")) {
						String[] command = str.split("\\s+");
						String address = command[1] + ":" + command[2];
						client.linkDown(address);
						
					} else if (str.startsWith("LINKUP")) {
						String[] command = str.split("\\s+");
						String address = command[1] + ":" + command[2];
						client.linkUp(address);	
						
					} else if (str.startsWith("CHANGECOST")) {
						String[] command = str.split("\\s+");
						String address = command[1] + ":" + command[2];
						int cost = Integer.parseInt(command[3]);
						client.changeCost(address, cost);
						
					} else if (str.startsWith("SHOWRT")) {
						client.showRT();
						
					} else if (str.startsWith("SHOWNEI")) {
						client.showNei();
						
					} else if (str.startsWith("CLOSE")) {
						System.exit(0);
						
					} else if (str.startsWith("TRANSFER")) {
						String[] command = str.split("\\s+");
						String file = command[1];
						String address = command[2] + ":" + command[3];
						client.transfer(file, address);
						
					} else {
						System.out.println("Wrong command!");
						printCommandMenu();
					}
				} catch (IndexOutOfBoundsException e1){
						System.out.println("Wrong command format!");
						printCommandMenu();
				}
			}
		} catch (IOException e) {
			
		}
	}
	
	// print command menu
	public void printCommandMenu(){
		String commandMenu = "============== Command Menu ==============\n" +
						"LINKDOWN ip port\n" +
						"LINKUP ip port\n" +
						"CHANGECOST ip port cost\n" +
						"SHOWRT\n" +
						"CLOSE\n" +
						"TRANSFER filename destination-ip port";
		System.out.println(commandMenu);
	}

}
