/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:05:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlanetOrder
{
	int nShips, nTime;
	Planet p;
	int playerID;

	public PlanetOrder(Planet p, int ns, int nTime, int playerID) {
		this.nShips = ns;
		this.p = p;
		this.nTime = nTime;
		this.playerID = playerID;
	}
}
