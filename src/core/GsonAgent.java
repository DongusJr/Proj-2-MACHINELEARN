/* Program: Threadneedle
 *
 * GsonAgent
 *
 * Container class for reading JSON config files using GSON
 *
 * Author  :  Jacky Mallett
 * Date    :  September 2014
 */

package core;

import com.google.gson.annotations.Expose;

public class GsonAgent
{
  @Expose
  public String clss; // Class of agent in file
  @Expose
  public String json; // Accompanying json string to initialise

  public GsonAgent(String c, String j)
  {
    this.clss = c;
    this.json = j;
  }
}
