import java.util.List;

public class Planet implements Cloneable {
	// Initializes a planet.
	public Planet(int planetID,
				  int owner,
				  int numShips,
				  int growthRate,
				  double x,
				  double y) {
		this.planetID = planetID;
		this.owner = owner;
		this.numShips = numShips;
		this.growthRate = growthRate;
		this.x = x;
		this.y = y;
	}

	// Accessors and simple modification functions. These should be mostly
	// self-explanatory.
	public int PlanetID() {
		return planetID;
	}

	public int Owner() {
		return owner;
	}

	public int NumShips() {
		return numShips;
	}

	public int GrowthRate() {
		return growthRate;
	}

	public double X() {
		return x;
	}

	public double Y() {
		return y;
	}

	public void Owner(int newOwner) {
		this.owner = newOwner;
	}

	public void NumShips(int newNumShips) {
		this.numShips = newNumShips;
	}

	public void AddShips(int amount) {
		numShips += amount;
	}

	public void RemoveShips(int amount) {
		numShips -= amount;
	}


	private int planetID;
	private int owner;
	private int numShips;
	private int growthRate;
	private double x, y;
	private List<Fleet> fleets;
	private int[] distances;
	private int[] shipsAtDistance;
	private int sumDistToEnemy;

	private Planet(Planet _p) {
		planetID = _p.planetID;
		owner = _p.owner;
		numShips = _p.numShips;
		growthRate = _p.growthRate;
		x = _p.x;
		y = _p.y;
	}

	public Object clone() {
		return new Planet(this);
	}
		
	public List<Fleet> getFleets() {
		return fleets;
	}

	public void setFleets(List<Fleet> fleets) {
		this.fleets = fleets;
	}

	public int[] getDistances() {
		return distances;
	}

	public void setDistances(int[] distances) {
		this.distances = distances;
	}

	public int getSumDistToEnemy() {
		return sumDistToEnemy;
	}

	public void setSumDistToEnemy(int sumDistToEnemy) {
		this.sumDistToEnemy = sumDistToEnemy;
	}
}
