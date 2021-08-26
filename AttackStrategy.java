import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:06:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class AttackStrategy extends OptimizingStrategy {
	int maxGrowth = 0;
	int maxShips = 0;

	public AttackStrategy(PlanetWars pw) {
		super(pw, 1, true);
	}

	public String getName() { return "ATTACK"; }
	
	public boolean FilterPlanet(OrderSearchNode sn, Planet p) {
		if (sn.getFinalOwner(p) <= 1)
			return false;

		return true;
	}

	public boolean TestState(OrderSearchNode osn) {
		int gr = osn.getGrowthRate();
		int na = osn.getAvailShips();

		if (gr > maxGrowth)
		{
			maxGrowth = gr;
			maxShips = na;
			return true;
		}
		else if (gr == maxGrowth && na > maxShips)
		{
			maxShips = na;
			return true;
		}
		return false;
	}
}
