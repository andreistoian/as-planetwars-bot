/**
 * Created by IntelliJ IDEA.
 * User: andrei
 * Date: Oct 21, 2010
 * Time: 9:03:31 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Strategy {
	protected PlanetWars pw;
	public Strategy(PlanetWars pw) {
		this.pw = pw;
	}
	public abstract String getName();
	public abstract OrderSearchNode Run(OrderSearchNode osn);
}