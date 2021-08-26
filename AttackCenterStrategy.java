import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:08:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class AttackCenterStrategy extends OptimizingStrategy {
	int maxScore = 0;
	private List<Integer> centerPlanets;
	public AttackCenterStrategy(PlanetWars pw, List<Integer> centerPlanets) {
		super(pw);
		this.centerPlanets = centerPlanets;
	}

	public boolean FilterPlanet(OrderSearchNode sn, Planet p) {
		if (sn.getFinalOwner(p) == 1)
			return false;

		int idxp = centerPlanets.indexOf(p);
		if (idxp == -1)
			return false;

		return true;
	}

	public void ResetOptimal() {
		maxScore = 0;
	}

	public boolean TestState(OrderSearchNode osn) {
		int score = 0;
		for (int pid : centerPlanets) {
			if (osn.getFinalOwner(pw.Planets().get(pid)) == 1)
				score += (centerPlanets.size() - centerPlanets.indexOf(pid));
		}

		if (score > maxScore)
		{
			maxScore = score;
			return true;
		}
		return false;
	}

	public static boolean shouldTakeCenter(PlanetWars pw, OrderSearchNode osn, List<Integer> centerPlanets) {
		int scoreMe = 0, scoreEnemy = 0;
		for (int pid : centerPlanets) {
			if (osn.getFinalOwner(pw.Planets().get(pid)) == 1)
				scoreMe += (centerPlanets.size() - centerPlanets.indexOf(pid));

			if (osn.getFinalOwner(pw.Planets().get(pid)) == 2)
				scoreEnemy += (centerPlanets.size() - centerPlanets.indexOf(pid));
		}

		Logger.AppendLog("CENTER: SCORE ME " + scoreMe + " SCORE ENEMY " + scoreEnemy, false);

		if (scoreEnemy > 0)
			return true;

		if (scoreMe < centerPlanets.size() * 1.5f) //scores are n, n-1, .. 1.
			return true;

		return false;
	}

	public String getName() { return "ATTACKCENTER"; }
}