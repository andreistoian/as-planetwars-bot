/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 29, 2010
 * Time: 6:22:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class SnatchStrategy extends OptimizingStrategy {
	int maxGrowth = 0;
	int maxShips = 0;

	public SnatchStrategy(PlanetWars pw, OrderSearchNode startNode) {
		super(pw, true);
		maxGrowth = startNode.getGrowthRate();
//		Logger.AppendLog("SNATCH START GROWTH: " + startNode.getGrowthRate(), false);
	}

	@Override
	protected boolean FilterPlanet(OrderSearchNode sn, Planet p) {
		boolean anyTimeMine = false;
		for (int k : sn.prediction.get(p).futureOwner)
			if (k == 1)
				anyTimeMine = true;
		
		return p.Owner() == 0 && sn.getFinalOwner(p) == 2 && !anyTimeMine;
	}

	@Override
	protected boolean TestState(OrderSearchNode osn) {
		int gr = osn.getGrowthRate();
		int na = osn.getAvailShips();

		if (gr > maxGrowth)
		{
			maxGrowth = gr;
			maxShips = na;

			Logger.AppendLog("SNATCH BEST GROWTH: " + gr, false);

			return true;
		}
		else if (gr == maxGrowth && na > maxShips)
		{
			maxShips = na;
			return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return "SNATCH";
	}
}
