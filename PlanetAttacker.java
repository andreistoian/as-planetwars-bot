import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 30, 2010
 * Time: 11:29:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class PlanetAttacker {

	private PossibleOrder result;

	public PossibleOrder getResult() {
		return result;
	}

	private class PlanetConquestInfo
	{
		int nShips;
		int nAttackTime;

		public PlanetConquestInfo(int ns, int at) {
			nShips = ns;
			nAttackTime = at;
		}
	}

	public PlanetAttacker(PlanetWars pw, Planet p, OrderSearchNode currentNode, int startTurn, boolean useViability) {
		this(pw, p, currentNode, startTurn, MyBot.maxDistance, false, useViability);
	}

	public PlanetAttacker(PlanetWars pw, Planet p, OrderSearchNode currentNode, int startTurn, int maxTurns, boolean useFullAttack, boolean useViability) {
		PlanetPrediction pp = currentNode.prediction.get(p);
		List<PlanetConquestInfo> ap = new ArrayList<PlanetConquestInfo>();

		int st = startTurn == 0 ? 1 : startTurn;
		for (int i = st; i < maxTurns; ++i)
			if (pp.futureInputNeeded[i] > 0)
//				ap.add(new PlanetConquestInfo(pp.futureInputNeeded[i], i));
				ap.add(new PlanetConquestInfo(useViability ? pp.undefendableNum[i] : pp.futureInputNeeded[i], i));

		Collections.sort(ap, new Comparator<PlanetConquestInfo>(){
			public int compare(PlanetConquestInfo o1, PlanetConquestInfo o2) {
				return o1.nShips - o2.nShips;
			}
		});

		for (PlanetConquestInfo pci : ap) {
//			Logger.AppendLog("TESTING CONQUEST OF " + p.PlanetID() + " AT " + (pci.nAttackTime - startTurn), false);
			for (Planet ps : pw.Planets()) {
				if (currentNode.prediction.get(ps).futureOwner[0] != currentNode.playerID) continue;

				PlanetPrediction pps = currentNode.prediction.get(ps);
				int nNeeded = pci.nShips;
				PossibleOrder po = new PossibleOrder(p, p.GrowthRate());

				int avail = pps.futureSafeAvailable[startTurn];
				if (MyBot.nTurn == 0 && ps.PlanetID() == 1) {
					if (pps.futureNumShips[startTurn] - avail < MyBot.m_TuneParams.get("turn0.min_ships")) {
						avail = pps.futureNumShips[startTurn] - (int)(MyBot.m_TuneParams.get("turn0.min_ships").floatValue());
					}
				}
/*				boolean bViable = false;
				if (useViability)
					bViable = useFullAttack ? pp.attackViable[pci.nAttackTime] == 1 : pp.expandViable[pci.nAttackTime] == 1;
				else
					bViable = true;*/

				if (p.getDistances()[ps.PlanetID()] == pci.nAttackTime - startTurn && avail > 0) // && bViable)
				{
//					Logger.AppendLog("PLANET " + p.PlanetID() +  " NEEDS " + pci.nShips + " IN RANGE FOR ATTACK IN " + pci.nAttackTime, false);
					if (nNeeded < avail)
					{
//						Logger.AppendLog("SATISFIED BY " + ps.PlanetID() + " AT " + startTurn, false);
						po.AddOrder(new PlanetOrder(ps, nNeeded, startTurn, currentNode.playerID));
						nNeeded = 0;
					}
					else
					{
						nNeeded -= avail;
						po.AddOrder(new PlanetOrder(ps, avail, startTurn, currentNode.playerID));
					}

//					Logger.AppendLog("PLANET " + p.PlanetID() +  " STILL NEEDS " + nNeeded, false);
					
					if (nNeeded > 0) {
						for (Planet aps : pw.Planets()) {
							if (currentNode.prediction.get(aps).futureOwner[0] != currentNode.playerID) continue;

							if (aps == ps)
								continue;
							int dap = aps.getDistances()[p.PlanetID()];
							if (dap > pci.nAttackTime)
								continue;

							PlanetPrediction paps = currentNode.prediction.get(aps);
//							for (int k = startTurn; k < dap; ++k) {
							for (int k = pci.nAttackTime - dap; k >= startTurn; --k) {
								avail = paps.futureSafeAvailable[k];
								if (avail > 0) {
									if (nNeeded < avail)
									{
										po.AddOrder(new PlanetOrder(aps, nNeeded, k, currentNode.playerID));
										nNeeded = 0;
									}
									else {
										nNeeded -= avail;
										po.AddOrder(new PlanetOrder(aps, avail, k, currentNode.playerID));
									}
//									Logger.AppendLog("PLANET " + p.PlanetID() +  " STILL NEEDS " + nNeeded, false);
									break;
								}
							}
							if (nNeeded <= 0)
								break;
						}
					}
				}
				if (nNeeded <= 0)
				{
					this.result = po;
					return;
				}
			}
		}
	}
}
