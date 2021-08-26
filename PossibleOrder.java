import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:05:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class PossibleOrder
{
	public Planet dst;
	public List<PlanetOrder> parts;
	public int growthGain;
	public String source;

	PossibleOrder(Planet d, int gain) {
		dst = d;
		growthGain = gain;
		parts = new ArrayList<PlanetOrder>();
	}

	public void AddOrder(PlanetOrder p) {
		parts.add(p);
	}

	public String toString() {
		String s = source + ": " + dst.PlanetID() + " <-- ";
		for (PlanetOrder po : parts) {
			s += po.p.PlanetID() + ", ships: " + po.nShips + " at " + po.nTime +"; ";
		}
		return s;
	}

	public void setSource(String source) {
		this.source = source;
	}
}