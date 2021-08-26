import java.util.*;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyBot {
	// The DoTurn function is where your code goes. The PlanetWars object
	// contains the state of the game, including information about all planets
	// and fleets that currently exist. Inside this function, you issue partialOrders
	// using the pw.IssueOrder() function. For example, to send 10 ships from
	// planet 3 to planet 8, you would say pw.IssueOrder(3, 8, 10).
	//
	// There is already a basic strategy in place here. You can use it as a
	// starting point, or you can throw it out entirely and replace it with
	// your own. Check out the tutorials and articles on the contest website at
	// http://www.ai-contest.com/resources.

	public static int nTurn = 0;
	public static int maxDistance = 0;

	public static HashMap<String, Float> m_TuneParams = new HashMap<String, Float>();

	private List<Integer> m_CenterPlanets = new ArrayList<Integer>();
	private HashMap<Integer, Integer> m_ClusterTargets = new HashMap<Integer, Integer>();
	private List<PossibleOrder> m_PendingOrders = new ArrayList<PossibleOrder>();
	private String m_PendingOrdersString = null;

	public class DistComparator implements Comparator<Planet> {
	   	private Planet target;
		private PlanetWars pw;
		public DistComparator(PlanetWars pw, Planet target) {
			this.target = target;
			this.pw = pw;
		}
		public int compare(Planet o1, Planet o2) {
			return pw.Distance(target.PlanetID(), o1.PlanetID()) - pw.Distance(target.PlanetID(), o2.PlanetID()) < 0 ? -1 : 1;
		}
	}

	private static void ReloadParams()
	{
		try {
			BufferedReader br = new BufferedReader(new FileReader("params.txt"));
			String line;
			while ( (line = br.readLine()) != null )
			{
				String [] parts = line.split("=");
				if (parts.length != 2) continue;

				try {
					m_TuneParams.put(parts[0], Float.parseFloat(parts[1]));
				} catch (Exception err) {
					Logger.AppendLog(err.toString(), false);
				}
			}
			br.close();
		} catch (IOException ignore) {			
		}
	}

	public float GetArmyScore(PlanetWars pw, int player)
	{
		float res = 0;
		float nShipsMul = m_TuneParams.get("power.shipsCoef");
		float growthMul = m_TuneParams.get("power.growthCoef");

		for (Planet p : pw.Planets())
		{
			if (p.Owner() == player)
			{
				res += nShipsMul * p.NumShips() + growthMul * p.GrowthRate();
			}
		}

		for (Fleet f : pw.Fleets())
		{
			if (f.Owner() == player)
			{
				res += nShipsMul * f.NumShips();
			}
		}
		return res;
	}

	public float GetGrowthRate(OrderSearchNode osn, int player, boolean future)
	{
		float res = 0;

		for (Planet p : osn.prediction.keySet())
		{
			if ( (future && osn.getFinalOwner(p) == player) || (!future && p.Owner() == player) )
			{
				res += p.GrowthRate();
			}
		}

		return res;
	}

	public int GetShipCount(PlanetWars pw, int player)
	{
		int res = 0;

		for (Planet p : pw.Planets())
		{
			if (p.Owner() == player)
			{
				res += p.NumShips();
			}
		}

		return res;
	}

	public List<Planet> FindAttackPath(PlanetWars pw, OrderSearchNode osn, Planet source, Planet target)
	{
		float[] dist = new float[pw.NumPlanets()];
		float infinity = 10000;
		float infinityp1 = infinity+1;
		Arrays.fill(dist, 10000);
		dist[source.PlanetID()] = 0;
		int[] prev = new int[pw.NumPlanets()];
		prev[source.PlanetID()] = -1;
		List<Planet> q = new ArrayList<Planet>();

		for (Planet p : osn.prediction.keySet()) {
			if (osn.getFinalOwner(p) == 1)
				q.add(p);
		}

		List<Planet> pathToTarget = new ArrayList<Planet>();
//		Logger.AppendLog("START DIJKSTRA", false);

		while (q.size() > 0)
		{
			int idxU = -1, idx = 0;
			float minDist = infinityp1;

			for (Planet tu : q)
			{
				if (dist[tu.PlanetID()] < minDist)
				{
					minDist = dist[tu.PlanetID()];
					idxU = idx;
				}
				idx++;
			}

//			Logger.AppendLog("GOT " + idxU, false);
			if (dist[q.get(idxU).PlanetID()] == infinity || q.get(idxU).PlanetID() == target.PlanetID())
				break;

			Planet u = q.remove(idxU);
			for (Planet v : q)
			{
				if (v == u) continue;

				float distFunc = (float)Math.pow(pw.Distance(u.PlanetID(), v.PlanetID()), m_TuneParams.get("graph.distPower")); 
				float alt = dist[u.PlanetID()] + distFunc;
				if (alt < dist[v.PlanetID()])
				{
					dist[v.PlanetID()] = alt;
					prev[v.PlanetID()] = u.PlanetID();
				}
			}
		}

		int pidx = target.PlanetID();
		String graphOut = "";		
		while (pidx > -1)
		{
			pathToTarget.add(pw.Planets().get(pidx));
			graphOut += pw.Planets().get(pidx).PlanetID() + " "; // + (prev[pidx] > -1 ? scores[pidx][prev[pidx]] : 0) + " ";
			pidx = prev[pidx];
		}
		Collections.reverse(pathToTarget);

		Logger.AppendLog(graphOut);

//		Logger.AppendLog("DONE DIJKSTRA", false);

		return pathToTarget;
	}

	public void DoTurn(PlanetWars pw) {
		Logger.AppendLog("TURN: " + nTurn);

		Logger.AppendLog("[POWER]");
		Logger.AppendLog(GetArmyScore(pw, 1) + "");
		Logger.AppendLog(GetArmyScore(pw, 2) + "");

		ComputeCenterPlanets(pw);

		if (m_PendingOrdersString != null)
		{
			loadPendingOrders(pw);
			m_PendingOrdersString = null;
		}

		if (nTurn == 0 && pw.EnemyPlanets().size() > 0) {
			m_ClusterTargets.put(pw.MyPlanets().get(0).PlanetID(), pw.EnemyPlanets().get(0).PlanetID());
		}

//		Logger.AppendLog("[RISK]");
		ComputeRisk(pw);

		for (Planet p : pw.Planets())
		{
			List<Fleet> fleets = new ArrayList<Fleet>();
			for (Fleet f : pw.Fleets())
				if (f.DestionationPlanet() == p.PlanetID())
					fleets.add(f);
			p.setFleets(fleets);
		}

		Logger.AppendLog("[PENDING ORDERS]");
		for (PossibleOrder po : m_PendingOrders) {
			Logger.AppendLog(po.toString());
		}

		for (PossibleOrder po : m_PendingOrders) {
			po.dst = pw.GetPlanet(po.dst.PlanetID());
			for (PlanetOrder part : po.parts) {
				part.nTime--;
				part.p = pw.GetPlanet(part.p.PlanetID());
			}
		}

		List<PossibleOrder> orders = GeneratePossibleOrders(pw);
		
		Logger.AppendLog("[FINALORDERS]", false);
		for (PossibleOrder po : orders) {
			Logger.AppendLog(po.toString(), false);
		}

		for (PossibleOrder po : orders) {
			for (PlanetOrder part : po.parts) {
				if (part.playerID == 1 && part.nTime == 0)
				{
					pw.IssueOrder(part.p, po.dst, part.nShips);
					part.p.NumShips(part.p.NumShips() - part.nShips);
				}
			}
		}

		m_PendingOrders = new ArrayList<PossibleOrder>(orders);

		List<PossibleOrder> donePending = new ArrayList<PossibleOrder>();
		for (PossibleOrder po : m_PendingOrders) {
			List<PlanetOrder> doneParts = new ArrayList<PlanetOrder>();
			for (PlanetOrder part : po.parts) {
				if (part.nTime == 0)
					doneParts.add(part);
			}

			po.parts.removeAll(doneParts);
			if (po.parts.size() == 0)
				donePending.add(po);
		}
		Logger.AppendLog("[DONE PENDING]", false);
		for (PossibleOrder rem : donePending) {
			Logger.AppendLog(rem.toString(), false);
		}
		m_PendingOrders.removeAll(donePending);

//		Logger.AppendLog(s + "\nALLDONE", false);
		Logger.AppendLog("ALLDONE", false);
		nTurn++;
	}

	private void ComputeRisk(PlanetWars pw) {
		for (Planet p : pw.Planets())
		{
			int sumEnemy = 0;
			for (Planet q : pw.Planets())
				if (p != q)
				{
					int d = p.getDistances()[q.PlanetID()];

					if (p.Owner() == 1 && q.Owner() == 2)
					{
						sumEnemy += d;
					}
				}

			p.setSumDistToEnemy(sumEnemy);
		}
	}

	private double GetCenterScore(Planet p, double ap, double bp, double cp, double ad, double bd, double cd) {
		double dperp = Math.abs(ap * p.X() + bp * p.Y() + cp) / Math.sqrt(ap*ap+bp*bp);
		double ddirect = Math.abs(ad * p.X() + bd * p.Y() + cd) / Math.sqrt(ad*ad+bd*bd);
		return dperp * m_TuneParams.get("center.axisPerpCoef") +
				ddirect * m_TuneParams.get("center.axisDirectCoef") + 
				(5 - p.GrowthRate()) * m_TuneParams.get("center.growthCoef") +
				p.NumShips() * m_TuneParams.get("center.numShipsCoef");
	}

	private boolean IsStateStable(PlanetWars pw, OrderSearchNode osn) {
		for (Planet p : pw.Planets()) {
			PlanetPrediction pp = osn.prediction.get(p);
			if (pp.futureOwner[0] != pp.futureOwner[maxDistance])
				return false;
		}
		return true;
	}

	private List<PossibleOrder> GeneratePossibleOrders(PlanetWars pw) {

		Logger.StartPerfLog("INITIAL");

		OrderSearchNode enemyDefence = new OrderSearchNode(pw, 2);
		Strategy defence = new DefendStrategy(pw, 2);
		enemyDefence = defence.Run(enemyDefence);

		Logger.StartPerfLog("WORSTCASE");
		List<PossibleOrder> worstCaseOrders = ComputeWorstCase(pw, enemyDefence);
		Logger.EndPerfLog("WORSTCASE");

		Logger.StartPerfLog("UNDEFENDABLE");

		OrderSearchNode startNode = new OrderSearchNode(pw, 1, null, worstCaseOrders);

		startNode = (new DefendStrategy(pw)).Run(startNode);
		
		List<PossibleOrder> impossibleOrders = new ArrayList<PossibleOrder>();
		for (PossibleOrder po : m_PendingOrders) {
			boolean canSatisfy = true;
			for (PlanetOrder part : po.parts) {
				if (startNode.prediction.get(part.p).futureSafeAvailable[part.nTime] < part.nShips)
					canSatisfy = false;
			}

			if (!canSatisfy)
				impossibleOrders.add(po);
		}

		m_PendingOrders.removeAll(impossibleOrders);

		startNode = new OrderSearchNode(pw, 1, m_PendingOrders, worstCaseOrders);

		/* TURN0 OPTIMIZATION */
/*		if (nTurn == 0)
		{
			Planet ctr = pw.Planets().get(0);
			Planet myp = pw.MyPlanets().get(0);
			int d = ctr.getDistances()[myp.PlanetID()];
			if (ctr.NumShips() < m_TuneParams.get("turn0.center_ships")) {
				startNode.prediction.get(ctr).undefendableNum[d] = startNode.prediction.get(ctr).undefendableNum[d-1];
			}
		}*/

		Logger.EndPerfLog("UNDEFENDABLE");

//		ComputeViability(pw, enemyDefence, startNode, true);
//		ComputeViability(pw, enemyDefence, startNode, false);

		Logger.EndPerfLog("INITIAL");

		Logger.StartPerfLog("DBGPRINT");
		Logger.AppendLog("[WORSTCASE]");
		for (Planet p : pw.Planets())
		{
			PlanetPrediction pp = startNode.prediction.get(p);
			Logger.AppendLog(pp.toString(p.PlanetID()));
		}
		Logger.EndPerfLog("DBGPRINT");

//		Logger.AppendLog("START SEARCH", false);

		float mg = GetGrowthRate(startNode, 1, false);
		float eg = GetGrowthRate(startNode, 2, false);

		Logger.AppendLog("[GROWTHFUTURE]");
		Logger.AppendLog(mg + "");
		Logger.AppendLog(eg + "");

		Logger.StartPerfLog("ORDERS");
		List<Strategy> strategies = new ArrayList<Strategy>();

//		strategies.add(new SnatchStrategy(pw, startNode));
		strategies.add(new DefendStrategy(pw));
//		if (mg <= eg * 1.1)
		strategies.add(new ExpandGainStrategy(pw, nTurn == 0 ? MyBot.maxDistance : 15));
//		else
		strategies.add(new AttackStrategy(pw));

		OrderSearchNode optimalOrders = startNode;
		for (Strategy s : strategies)
		{
			optimalOrders = s.Run(optimalOrders);
		}

		Logger.StartPerfLog("MOVETROOPS");
		List<PossibleOrder> moveTroops = MoveTroopsToSpearHead(pw, optimalOrders);
		if (moveTroops != null)
		{
			optimalOrders.orderList.addAll(moveTroops);
		}
		Logger.EndPerfLog("MOVETROOPS");
		Logger.EndPerfLog("ORDERS");

		Logger.AppendLog("[CLUSTERTARGETS]");
		for (int k : m_ClusterTargets.keySet()) {
			Logger.AppendLog(k + " " + m_ClusterTargets.get(k) + " " + pw.Distance(k, m_ClusterTargets.get(k)));
		}

		return optimalOrders.orderList;
	}
	
	private List<PossibleOrder> ComputeWorstCase(PlanetWars pw, OrderSearchNode enemyDefence) {

		List<PossibleOrder> enemyWorstAttack = new ArrayList<PossibleOrder>();

		for (int pid : m_ClusterTargets.keySet()) {
			Planet p = pw.Planets().get(pid);
//			Logger.AppendLog("CHECKING WORST CASE FOR " + p.PlanetID(), false);
			float minDist = 10000;
			Planet closest = null;
			for (Planet q : pw.EnemyPlanets()) {
				if (p.getDistances()[q.PlanetID()] < minDist) {
					minDist = p.getDistances()[q.PlanetID()];
					closest = q;
				}
			}

			if (closest != null) {
				int nAvail = enemyDefence.prediction.get(closest).futureAvailable[0];

				/* TURN0 OPTIMIZATION */
				if (nTurn == 0) {
					nAvail -= m_TuneParams.get("turn0.gamble_planets");
				}

				if (nAvail > 0) {
					PossibleOrder po = new PossibleOrder(p, 0);
					Logger.AppendLog("WORST CASE ATTACK FROM " + closest.PlanetID() + " TO " + p.PlanetID() + " WITH " + nAvail, false);
					po.parts.add(new PlanetOrder(closest, nAvail, 0, 2));
					enemyWorstAttack.add(po);
				}
			}
		}

		return enemyWorstAttack;
	}

	private List<PossibleOrder> MoveTroopsToSpearHead(PlanetWars pw, OrderSearchNode optimalOrders) {

		int nMyPlanets = 0;
		for (Planet p : pw.Planets())
			if (optimalOrders.getFinalOwner(p) == 1)
				nMyPlanets++;
		
		if (pw.MyPlanets().size() == 1 && nMyPlanets <= 1)
		{
			m_ClusterTargets.clear();
			m_ClusterTargets.put(pw.MyPlanets().get(0).PlanetID(), pw.EnemyPlanets().get(0).PlanetID());
			return null;
		}

//		Logger.AppendLog( "FINDING ROUTE ENDPOINTS FROM " + pw.MyPlanets().size() + " PLANETS", false);

		Logger.AppendLog("[GRAPHS]");

		Map<Planet, ArrayList<Planet>> clusters = ClusterPlanets(pw, optimalOrders);

		List<List<Planet>> supplyRoutes = BuildSupplyRoutes(pw, optimalOrders, clusters);

		List<PossibleOrder> moveTroops = new ArrayList<PossibleOrder>();
		for (Planet p : pw.MyPlanets()) {
			PlanetPrediction pps = optimalOrders.prediction.get(p);
			int avail = pps.futureAvailable[0];

			if (MyBot.nTurn == 0 && p.PlanetID() == 1) {
				if (pps.futureNumShips[0] - avail < MyBot.m_TuneParams.get("turn0.min_ships")) {
					avail = pps.futureNumShips[0] - (int)(MyBot.m_TuneParams.get("turn0.min_ships").floatValue());
				}
			}
			
			if (avail > 0) {
				int idxp = -1;
				List<Planet> bestRoute = null;
				for (List<Planet> route : supplyRoutes)
				{
					int idxr = route.indexOf(p);
					if (idxr > -1)
					{
						idxp = idxr;
						bestRoute = route;
						break;
					}
				}

				if (idxp > -1 && idxp < bestRoute.size() - 1) {
					Planet dst = bestRoute.get(idxp+1);

					PlanetPrediction pc = optimalOrders.prediction.get(dst);
					int dtp = p.getDistances()[dst.PlanetID()];
					if (pc.futureOwner[dtp] == 1) {
						PossibleOrder poMoveToSpearHead = new PossibleOrder(dst, 0);
						poMoveToSpearHead.setSource("M");
						poMoveToSpearHead.AddOrder(new PlanetOrder(p, avail, 0, 1));
						moveTroops.add(poMoveToSpearHead);
					}
				}
			}
		}
		return moveTroops;
	}

	private List<List<Planet>> BuildSupplyRoutes(PlanetWars pw, OrderSearchNode optimalOrders, Map<Planet, ArrayList<Planet>> clusters) {
		List<List<Planet>> supplyRoutes = new ArrayList<List<Planet>>();

		for (Planet pRouteEnd : clusters.keySet()) {
			List<Planet> unconnected = new ArrayList<Planet>(clusters.get(pRouteEnd));

			while (unconnected.size() > 0) {
				float maxDist = 0;
				Planet pRouteStart = null;
				for (Planet p : unconnected) {
					if (p.getSumDistToEnemy() > maxDist && p != pRouteEnd) {
						maxDist = p.getSumDistToEnemy();
						pRouteStart = p;
					}
				}

				if (pRouteStart == null)
					break;

//				Logger.AppendLog("TAIL AT " + maxDist + " IS " + pRouteStart.PlanetID(), false);

//				Logger.AppendLog("FIND ATTACK PATH FROM " + pRouteStart.PlanetID() + "( " + pRouteStart.Owner() + ") TO " + pRouteEnd.PlanetID() + " (" + pRouteEnd.Owner() + ")", false);
				List<Planet> bestRoute = FindAttackPath(pw, optimalOrders, pRouteStart, pRouteEnd);
				for (Planet p : bestRoute)
					unconnected.remove(p);

				supplyRoutes.add(bestRoute);
			}
		}
		return supplyRoutes;
	}

	private Map<Planet, ArrayList<Planet>> ClusterPlanets(PlanetWars pw, OrderSearchNode optimalOrders) {
		Map<Planet, ArrayList<Planet>> clusters = new HashMap<Planet, ArrayList<Planet>>();
		List<Planet> unClustered = new ArrayList<Planet>();
		for (Planet p : pw.Planets()) {
			if (optimalOrders.getFinalOwner(p) == 1)
				unClustered.add(p);
		}

		m_ClusterTargets.clear();
		while (unClustered.size() > 0) {
			Planet clusterLead = null, clusterTarget = null;
			float minDist = (maxDistance + 1) * pw.NumPlanets();
			for (Planet p : pw.Planets()) {
				if (optimalOrders.getFinalOwner(p) != 2) continue;
				float minDistP = (maxDistance + 1) * pw.NumPlanets();
				Planet closestToP = null;
				for (Planet q : unClustered)
				{
					if (optimalOrders.getFinalOwner(q) != 1) continue;
					if (p.getDistances()[q.PlanetID()] < minDistP)
					{
						minDistP = p.getDistances()[q.PlanetID()];
						closestToP = q;
					}
				}

				if (minDistP < minDist) {
					clusterLead = closestToP;
					clusterTarget = p;
					minDist = minDistP;
				}
			}

			if (clusterLead == null)
				break;

			unClustered.remove(clusterLead);
			
//			Logger.AppendLog("HEAD AT " + minDist + " IS " + clusterLead.PlanetID(), false);

			ArrayList<Planet> thisCluster = new ArrayList<Planet>();
			for (int i = 0; i < unClustered.size(); ++i) {
				Planet p = unClustered.get(i);
				float minDistEnemy = (maxDistance + 1) * pw.NumPlanets();
				for (Planet q : pw.Planets()) {
					if (optimalOrders.getFinalOwner(q) != 2) continue;
					if (q.getDistances()[p.PlanetID()] < minDistEnemy) {
						minDistEnemy = q.getDistances()[p.PlanetID()];
					}
				}

				if (minDistEnemy > p.getDistances()[clusterLead.PlanetID()]) {
					thisCluster.add(p);
					unClustered.remove(i);
					i--;
				}
			}
			clusters.put(clusterLead, thisCluster);
			m_ClusterTargets.put(clusterLead.PlanetID(), clusterTarget.PlanetID());
		}
		return clusters;
	}

	public void setTurnNumber(String message) {
		nTurn = Integer.parseInt(message.trim());
	}

	private void loadPendingOrders(PlanetWars pw)
	{
		String[] lines = m_PendingOrdersString.trim().split("\\r?\\n");
		Pattern p = Pattern.compile(" (\\d+), ships: (\\d+) at (\\d+);");
		Pattern pdst = Pattern.compile("^([A-z]{1}): (\\d+)");
		for (String line : lines)
		{
			Matcher mdst = pdst.matcher(line);
			if (!mdst.find()) continue;

			int dstID = Integer.parseInt(mdst.group(2));
			PossibleOrder po = new PossibleOrder(pw.Planets().get(dstID), 0);
			po.setSource(mdst.group(1));
			Matcher m = p.matcher(line);
			while (m.find())
			{
				int pid = Integer.parseInt(m.group(1));
				int ns = Integer.parseInt(m.group(2));
				int t = Integer.parseInt(m.group(3));

				po.parts.add(new PlanetOrder(pw.Planets().get(pid), ns, t, 1));
			}
			m_PendingOrders.add(po);
		}
	}

   	public void setPendingOrders(String message) {
		m_PendingOrdersString = message;
	}

	public static void main(String[] args) {
		String line = "";
		String message = "";
		int c;

		ReloadParams();

		boolean bDebug = args.length > 0 && args[0].compareTo("debug") == 0; 
		boolean bPerf = (args.length == 1 && args[0].compareTo("perf") == 0) || (args.length == 2 && args[1].compareTo("perf") == 0);

		Logger.ResetLog(bDebug, bPerf);
		MyBot botInstance = new MyBot();

		try {
			boolean run = true;
			while (run && (c = System.in.read()) >= 0) {
				switch (c) {
					case '\n':
						if (line.equals("go")) {
							Logger.ResetPerfCounters();
							Logger.StartPerfLog("TURN" + nTurn);
							PlanetWars pw = new PlanetWars(message);
							botInstance.DoTurn(pw);
							pw.FinishTurn();
							message = "";
							Logger.EndPerfLog("");
							Logger.WritePerfLog();
						} else if (line.equals("exit")) {
							run = false;
							break;	
						} else if (bDebug && line.equals("clearlog")) {
							Logger.ResetLog(bDebug, bPerf);
							message = "";
						} else if (bDebug && line.equals("pending")) {
							botInstance.setPendingOrders(message);
							message = "";
						} else if (bDebug && line.equals("turn")) {
							botInstance.setTurnNumber(message);
							message = "";
						}else {
							message += line + "\n";
						}
						line = "";
						break;
					default:
						line += (char) c;
						break;
				}
			}
		} catch (Exception e) {
			Logger.PrintException(e);
		}

		Logger.CloseLog();
	}


	private void ComputeCenterPlanets(PlanetWars pw) {
		double cx = 0, cy = 0;
		for (Planet p : pw.Planets())
		{
			int[] dv = new int[pw.Planets().size()];
			for (Planet q : pw.Planets())
				if (p != q)
				{
					int d = pw.Distance(p.PlanetID(), q.PlanetID());
					dv[q.PlanetID()] = d;

					if (d + 1 > maxDistance)
						maxDistance = d + 1;
				}

			cx += p.X();
			cy += p.Y();

			p.setDistances(dv);
		}
	}
}

