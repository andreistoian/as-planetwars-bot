import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:20:06 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class OptimizingStrategy extends Strategy {

	int numTurns = 1;
	boolean considerViability;

	public OptimizingStrategy(PlanetWars pw) {
		this(pw, false);
	}

	public OptimizingStrategy(PlanetWars pw, boolean considerViability) {
		super(pw);
		this.considerViability = considerViability;
	}

	public OptimizingStrategy(PlanetWars pw, int numTurns, boolean considerViability) {
		super(pw);
		this.numTurns = numTurns;
		this.considerViability = considerViability;
	}


	protected abstract boolean FilterPlanet(OrderSearchNode sn, Planet p);
	protected abstract boolean TestState(OrderSearchNode osn);
	protected boolean BetterThanStartState(OrderSearchNode osn) { return true; }

	@Override
	public OrderSearchNode Run(OrderSearchNode startNode) {
		Logger.StartPerfLog(getName());
		int nTried = 0;
		
		Queue<OrderSearchNode> searchQueue = new LinkedList<OrderSearchNode>();
		searchQueue.add(startNode);
		startNode.lastPlanetID = -1;

		OrderSearchNode optimalOrders = startNode;
		
		long ticks = System.nanoTime();
//		List<PossibleOrder> thisRunOrders = new ArrayList<PossibleOrder>();
//		List<Integer> planetsSearched = new ArrayList<Integer>();

		while (searchQueue.size() > 0)
		{
			Logger.StartPerfLog("STATES");
			OrderSearchNode currentNode = searchQueue.poll();
			nTried++;
//			Logger.AppendLog("POLLED NEW STATE, DONE " + nTried + " REMAIN: " + searchQueue.size(), false);

			boolean bAnyOrders = false;
			for (Planet p : pw.Planets())
			{
				if (p.PlanetID() <= currentNode.lastPlanetID) {
//					Logger.AppendLog("IGNORE0 " + p.PlanetID(), false);
					continue;
				}

				if (!FilterPlanet(currentNode, p)) {
//					Logger.AppendLog("IGNORE " + p.PlanetID(), false);
					continue;
				}

				for (int nt = 0; nt < numTurns; ++nt) {
					Logger.StartPerfLog("PATK");
					PossibleOrder po = (new PlanetAttacker(pw, p, currentNode, nt, considerViability)).getResult();
					Logger.EndPerfLog("PATK");
					if (po != null)
					{
	//					Planet ps = po.parts.get(0).p;
//						Logger.AppendLog("PLANET CAPTURE " + po.toString(), false);

						po.setSource(getName().substring(0, 1));
						OrderSearchNode nn = new OrderSearchNode(pw, currentNode, po);

						nn.lastPlanetID = p.PlanetID();

						if (!BetterThanStartState(nn))
							break;

						searchQueue.add(nn);
//						Logger.AppendLog("NEW SEARCH NODE: " + nn.prediction.get(pw.MyPlanets().get(0)).futureNumShips[0], false);
						bAnyOrders = true;
						break;
					}
				}
			}

			if (!bAnyOrders) {
		   		if (TestState(currentNode)) {
//				   Logger.AppendLog("NEW GROWTH MAX " + currentNode.getGrowthRate() + "\nREMAIN " + currentNode.prediction.get(pw.MyPlanets().get(0)).futureSafeAvailable[0] , false);
//					for (PossibleOrder po : currentNode.orderList) {
//						Logger.AppendLog(po.toString(), false);
//					}
					optimalOrders = currentNode;
				}
			}

			Logger.EndPerfLog("STATES");			
//			Logger.AppendLog( "DONE NODE CREATION IN: " + ((System.nanoTime() - ticks) / 1000) + "us", false);
		}

		Logger.EndPerfLog( "TRIED " + nTried + " NODES IN");

		return optimalOrders;
	}


}
