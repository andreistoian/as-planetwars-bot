/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 30, 2010
 * Time: 11:19:34 AM
 * To change this template use File | Settings | File Templates.
 */
public class ExpandGainStrategy extends OptimizingStrategy {
	int maxShips = 0;
	int nTurns = 10;

	public ExpandGainStrategy(PlanetWars pw, int turns) {
		super(pw, 5, true);
		nTurns = turns;
	}

	public boolean FilterPlanet(OrderSearchNode sn, Planet p) {
		if (sn.getFinalOwner(p) == 1)
			return false;

		return true;
	}

	public boolean BetterThanStartState(OrderSearchNode osn) {
		int ns = GetNumShipsGained(osn);
		return ns > 0;
	}

	public boolean TestState(OrderSearchNode osn) {
		int totalShips = GetNumShipsGained(osn);

		if (totalShips > maxShips)
		{
			maxShips = totalShips;
			return true;
		}

		return false;
	}

	private int GetNumShipsGained(OrderSearchNode osn) {
		int totalShips = 0;
		for (Planet p : pw.Planets()) {
			if (osn.getFinalOwner(p) == 1 && p.Owner() != 1) {
				int nt = 0;
				int ns = 0;
				while (osn.prediction.get(p).futureOwner[nt] != 1) nt++;
				for (PossibleOrder po : osn.orderList) {
					if (po.dst == p)
						for (PlanetOrder part : po.parts)
							ns += part.nShips;
				}

				int g = nt >= nTurns ? -ns : (nTurns - nt) * p.GrowthRate() - ns;
				totalShips += g;
			}
		}
		return totalShips;
	}

	public String getName() { return "GAIN"; }

}
