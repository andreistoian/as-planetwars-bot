import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:04:02 PM
 * To change this template use File | Settings | File Templates.
 */
class OrderSearchNode {
	public HashMap<Planet, PlanetPrediction> prediction;
	public ArrayList<ArrayList<PlanetOrder>> partialOrders;
	public ArrayList<ArrayList<PlanetOrder>> worstCaseOrders;
	public ArrayList<PossibleOrder> orderList;
	public int playerID;
	public int lastPlanetID;

	public OrderSearchNode(PlanetWars pw) {
		this(pw, 1);
	}

	public OrderSearchNode(PlanetWars pw, int playerID) {
		this(pw, playerID, null, null);
	}

	public OrderSearchNode(PlanetWars pw, int playerID, List<PossibleOrder> pendingOrders, List<PossibleOrder> worstCaseOrders) {
		this.playerID = playerID;
		this.prediction = new HashMap<Planet, PlanetPrediction>();

		this.partialOrders = new ArrayList<ArrayList<PlanetOrder>>(pw.NumPlanets());
		this.worstCaseOrders = new ArrayList<ArrayList<PlanetOrder>>(pw.NumPlanets()); 

		orderList = new ArrayList<PossibleOrder>();

		for (int i = 0; i < pw.NumPlanets(); ++i)
		{
			this.partialOrders.add(null);
			this.worstCaseOrders.add(null);
		}

		if (worstCaseOrders != null)
		{
			ExtractPossibleOrderList(worstCaseOrders, this.worstCaseOrders);
		}

		if (pendingOrders != null)
		{
			ExtractPossibleOrderList(pendingOrders, this.partialOrders);
			orderList.addAll(pendingOrders);
		}

		for (Planet p : pw.Planets())
		{
			this.prediction.put(p, ComputeFutureState(p));
		}

		ComputeUndefendable(pw);
	}

	public OrderSearchNode(PlanetWars pw, OrderSearchNode n, PossibleOrder po) {
		this(pw, n, Arrays.asList(po));
	}

	public OrderSearchNode(PlanetWars pw, OrderSearchNode n, List<PossibleOrder> sim) {
		this.playerID = n.playerID;
		this.prediction = new HashMap<Planet, PlanetPrediction>();
		this.partialOrders = new ArrayList<ArrayList<PlanetOrder>>(pw.NumPlanets());
		this.worstCaseOrders = new ArrayList<ArrayList<PlanetOrder>>(pw.NumPlanets());

		for (int i = 0; i < pw.NumPlanets(); ++i) {
			if (n.partialOrders.get(i) != null)
				this.partialOrders.add(new ArrayList<PlanetOrder>(n.partialOrders.get(i)));
			else
				this.partialOrders.add(null);

			if (n.worstCaseOrders.get(i) != null)
				this.worstCaseOrders.add(new ArrayList<PlanetOrder>(n.worstCaseOrders.get(i)));
			else
				this.worstCaseOrders.add(null);
		}

		Set<Planet> modifiedPlanets = ExtractPossibleOrderList(sim, this.partialOrders);

		for (Planet p : pw.Planets()) {
			if (modifiedPlanets.contains(p))
				this.prediction.put(p, ComputeFutureState(p));
			else
				this.prediction.put(p, n.prediction.get(p));
		}

		ComputeUndefendable(pw);

		orderList = new ArrayList<PossibleOrder>(n.orderList);
		orderList.addAll(sim);
	}

	private void ComputeUndefendable(PlanetWars pw) {
		for (Planet p : pw.Planets()) {
			PlanetPrediction pp = this.prediction.get(p);
			for (int k = 0; k < MyBot.maxDistance; ++k) {
				if (pp.futureOwner[k] != 1) {
					int numShips = pp.futureOwner[k] == 2 ? pp.futureNumShips[k] : 0;

					for (Planet q : pw.Planets()) {
						if (q == p) continue;

						PlanetPrediction pq = this.prediction.get(q);

						int d = p.getDistances()[q.PlanetID()];
						if (d <= k && pq.futureOwner[k - d] == 2)
						{
							numShips += pq.futureNumShips[k - d];
						}
					}

					int startShips = pp.futureOwner[k] == 0 ? pp.futureNumShips[k] : 0;
					pp.undefendableNum[k] = startShips + (numShips < 0 ? 0 : numShips) + 1;
				}
			}
		}
	}

	private Set<Planet> ExtractPossibleOrderList(List<PossibleOrder> sim, List<ArrayList<PlanetOrder>> orderList) {
		Set<Planet> modified = new HashSet<Planet>();
		
		for (PossibleOrder po : sim) {
			for (PlanetOrder pop : po.parts) {
				if (orderList.get(pop.p.PlanetID()) == null)
					orderList.set(pop.p.PlanetID(), new ArrayList<PlanetOrder>(5));

				orderList.get(pop.p.PlanetID()).add(new PlanetOrder(pop.p, -pop.nShips, pop.nTime, pop.playerID));

//				if (pop.p.Owner() != 1)
//					Logger.AppendLog("ADDED PLANET ORDER FOR " + pop.p.PlanetID() + " - " + (-pop.nShips) + " at " + pop.nTime, false);

				if (orderList.get(po.dst.PlanetID()) == null)
					orderList.set(po.dst.PlanetID(), new ArrayList<PlanetOrder>(5));

				orderList.get(po.dst.PlanetID()).add(new PlanetOrder(po.dst, pop.nShips, pop.nTime + po.dst.getDistances()[pop.p.PlanetID()], pop.playerID));

//				if (pop.p.Owner() != 1)
//					Logger.AppendLog("ADDED PLANET ORDER FOR " + po.dst.PlanetID() + ": " + (pop.nShips) + " at " + pop.nTime + po.dst.getDistances()[pop.p.PlanetID()], false);

				modified.add(pop.p);
				modified.add(po.dst);
			}
		}
		return modified;
	}

	public int getGrowthRate() {
		int totalGrowth = 0;
		for (Planet p : this.prediction.keySet())
			if (getFinalOwner(p) == 1)
				totalGrowth += p.GrowthRate();

		return totalGrowth;
	}

	public int getAvailShips() {
		int total = 0;
		for (Planet p : this.prediction.keySet())
			if (p.Owner() == 1)
				total += prediction.get(p).futureAvailable[0];

		return total;
	}

	public int getFinalOwner(Planet p) {
		return this.prediction.get(p).futureOwner[MyBot.maxDistance - 1];
	}

	private PlanetPrediction ComputeFutureState(Planet p) {

		List<PlanetOrder> add = this.partialOrders.get(p.PlanetID()) != null ?
									new ArrayList<PlanetOrder>(this.partialOrders.get(p.PlanetID())) :
									new ArrayList<PlanetOrder>();

		PlanetPrediction poNormal = ComputeFutureState(p, add);

		List<PlanetOrder> worstCase = this.worstCaseOrders.get(p.PlanetID());
		if (worstCase != null) {
			add.addAll(worstCase);
			PlanetPrediction poWorst = ComputeFutureState(p, add);
			poNormal.futureSafeAvailable = Arrays.copyOf(poWorst.futureAvailable, poWorst.futureAvailable.length);
		}
		else
			poNormal.futureSafeAvailable = Arrays.copyOf(poNormal.futureAvailable, poNormal.futureAvailable.length);
		
		return poNormal;
	}

	private PlanetPrediction ComputeFutureState(Planet p, List<PlanetOrder> add) {
		int[] futureNumShips = new int[MyBot.maxDistance];
		int[] futureOwner = new int[MyBot.maxDistance];
		int[] futureInputNeeded = new int[MyBot.maxDistance];
		int[] futureAvailable = new int[MyBot.maxDistance];

		futureOwner[0] = p.Owner();
		futureNumShips[0] = p.NumShips();

		for (PlanetOrder po : add) {
			if (po.nTime == 0)
				futureNumShips[0] += po.nShips;
		}

		int enemyID = (playerID == 2 ? 1 : 2);

		futureAvailable[0] = futureOwner[0] == this.playerID ? futureNumShips[0] : 0;

//		int minFleets = futureNumShips[0];

		HashMap<Integer, Integer> participants = new HashMap<Integer, Integer>();

		for (int i = 1; i < futureNumShips.length; ++i)
		{
			futureOwner[i] = futureOwner[i-1];
			futureNumShips[i] = futureNumShips[i-1];

			if (futureOwner[i] != 0)
				futureNumShips[i] += p.GrowthRate();

			List<Fleet> fleetsNow = new ArrayList<Fleet>();
			for (Fleet f : p.getFleets())
				if (f.TurnsRemaining() == i)
					fleetsNow.add(f);

			participants.clear();
			participants.put(futureOwner[i], futureNumShips[i]);

			int numForcePlayer1 = 0, numForcePlayer2 = 0;

			for (Fleet f : fleetsNow) {
				if (f.Owner() == 1)
					numForcePlayer1 += f.NumShips();
				else
					numForcePlayer2 += f.NumShips();
			}

			for (PlanetOrder po : add) {
				if (po.nTime == i)
					if (po.playerID == 1)
						numForcePlayer1 += po.nShips;
					else
						numForcePlayer2 += po.nShips;
			}

			if (participants.containsKey(1)) {
				participants.put(1, participants.get(1) + numForcePlayer1);
			} else {
				participants.put(1, numForcePlayer1);
			}

			if (participants.containsKey(2)) {
				participants.put(2, participants.get(2) + numForcePlayer2);
			} else {
				participants.put(2, numForcePlayer2);
			}

			int maxShips = 0, maxOwner = -1, secShips = 0;
			for (int own : participants.keySet()) {
				int sh = participants.get(own);
				if (sh > secShips) {
					if (sh > maxShips) {
						secShips = maxShips;
						maxShips = sh;
						maxOwner = own;
					} else
						secShips = sh;
				}
			}

			int result = maxShips - secShips;

			int startOwner = futureOwner[i];

			futureNumShips[i] = result;

			if (result > 0)
				futureOwner[i] = maxOwner;

			if (futureOwner[i] == playerID)
			{
				for (int j = 1; j <= i; ++j)
					futureInputNeeded[j] = 0;
			}
			else
			{
				futureInputNeeded[i] = futureNumShips[i] + 1;
				int arrived = (startOwner == enemyID) ? numForcePlayer1 : numForcePlayer2;
				if (arrived > 0)
				{
					for (int j = 0; j <= i-1; ++j)
					{
						if (startOwner == playerID)
							futureInputNeeded[j] = futureNumShips[i];
						else
							futureInputNeeded[j] += numForcePlayer1;
					}
				}
			}

			if (futureOwner[i] == enemyID) {
				for (int j = 0; j <= i; ++j)
					futureAvailable[j] = 0;
			}
			else if (futureOwner[i] == playerID) {
				if (futureNumShips[i] < futureNumShips[i-1])
				{
					int minFleets = futureNumShips[i];
//					if (minFleets < 0) minFleets = 0;

					futureAvailable[i] = minFleets;
					for (int j = i-1; j >= 0 && futureOwner[j] == playerID && futureNumShips[j] > futureNumShips[i]; --j) {
						futureAvailable[j] = minFleets;
					}
				}
				else
					futureAvailable[i] = futureNumShips[i];
			}
		}

		return new PlanetPrediction(futureNumShips, futureOwner, futureInputNeeded, futureAvailable, playerID == 2 ? 1 : 0);
	}
}