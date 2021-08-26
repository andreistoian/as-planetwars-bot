import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:04:32 PM
 * To change this template use File | Settings | File Templates.
 */
class PlanetPrediction
{
	int[] futureNumShips, futureOwner, futureInputNeeded, futureAvailable, futureSafeAvailable;
	int[] undefendableNum;

	public PlanetPrediction(int[] futureNumShips, int [] futureOwner, int[] futureInputNeeded, int[] futureAvailable, int viableDefault) {
		this.futureNumShips = futureNumShips;
		this.futureOwner = futureOwner;
		this.futureInputNeeded = futureInputNeeded;
		this.futureAvailable = futureAvailable;
		this.undefendableNum = new int[this.futureNumShips.length];
	}

	public String toString(int id) {
		String s = id + " :"; for (int futureNumShip : futureNumShips) s += padInt(futureNumShip, 4) + " ";
		s += "\n";
		s += id + " :"; for (int aFutureInputNeeded : futureInputNeeded) s += padInt(aFutureInputNeeded, 4) + " ";
		s += "\n";
		s += id + " :"; for (int aFutureOwner : futureOwner) s += padInt(aFutureOwner, 4) + " ";
		s += "\n";
		s += id + " :"; for (int k : futureAvailable) s += padInt(k, 4) + " ";
		s += "\n";
		s += id + " :"; for (int k : futureSafeAvailable) s += padInt(k, 4) + " ";
		s += "\n";
		s += id + " :"; for (int k : undefendableNum) s += padInt(k, 4) + " ";
		return s;
	}

	public static String padInt(int d, int size) {
		String s = d + "";
		while (s.length() < size)
			 s = " " + s;
		return s;
	}
}