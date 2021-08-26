/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:07:36 PM
 */
public class DefendStrategy extends OptimizingStrategy {
	int maxGrowth = 0;
	int playerID = 1;
	
	public DefendStrategy(PlanetWars pw) {
		super(pw, 10, false);
	}
	public DefendStrategy(PlanetWars pw, int playerID) {
		super(pw);
		this.playerID = playerID;
	}

	public boolean FilterPlanet(OrderSearchNode sn, Planet p) {
		int enemyID = playerID == 1 ? 2 : 1;
		int firstOwner = 0, k = 0;
		while (k < MyBot.maxDistance && sn.prediction.get(p).futureOwner[k] == 0) k++;
		if (k < MyBot.maxDistance)
			firstOwner = sn.prediction.get(p).futureOwner[k];
		
		if (firstOwner == playerID && sn.getFinalOwner(p) == enemyID)
			return true;
		return false;
	}

	public boolean TestState(OrderSearchNode osn) {
		int tg = osn.getGrowthRate();
		if (tg > maxGrowth)
		{
			maxGrowth = tg;
			return true;
		}
		return false;
	}

	public String getName() { return "DEFEND"; }
}