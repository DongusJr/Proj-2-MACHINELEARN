/* Program : Threadneedle
 *
 * Region  : Base class for Region     
 * 
 * Author  : Jacky Mallett
 * Date    : December 2013
 * 
 * Comments: A Region provides local government (taxation) and also provides
 *           the ability to specify geographical entities within a country that
 *           simulation entities can be attached to. 
 *           
 */
package core;

public class Region extends Govt
{
  public Region(String name, Govt govt, Bank bank)
  {
    super(name, bank.name, 0L);
    this.country = govt.country;
    govt.addRegion(this);
  }

  public Region()
  {
     super();
  }
}
