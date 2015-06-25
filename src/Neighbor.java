
/* This class is to record each neighbor's information
 * */
public class Neighbor {
	public float cost;
	public boolean status;
	public long lastSendTime;
	
	public Neighbor(float cost) {
        this.cost = cost;
        status = true;
    }

}
