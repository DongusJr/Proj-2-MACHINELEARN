/* Program : Threadneedle
 *
 * Config  : Provides the base configuration for all agents which is used
 *           for model reset/save configuration to file.              
 * 
 * Author  : Jacky Mallett
 * Date    : June  2012
 *
 * Comments:
 *   It is assumed that the config is used to create a new object replacing
 *   the existing one. State information held in the config object is frozen
 *   at the point it is placed in the configuration and is reverted to on 
 *   reset. 
 *
 *  Nb. Only static objects can be placed in config.
 */
package core;

class Config
{
  public String name; 					// Name of object
  public String product; 		       	// Name of product (market/company)
  public String bankname;              	// Name of bank
  public String branch;                	// Name of branch
  public String employerName = null;   	// Name of employer

  Integer id;

  public long initialDeposit = -1;
  public long initialSalary  = -1;
  public int  labourInput    = -1;

  public int    personaltaxrate; // Personal tax rate for Governments
  public int    corporatetaxrate; // Corporate tax rate for Governments
  public double debtceiling;

  public Loan.Type loantype; // Type of default loan for banks

  public int    loanDuration = -1; // Duration of loan for borrowing agents
  public long   loanAmount   = 10000;// Default amount to borrow
  public double capitalPct   = 0.0; // Limit on capital increases for banks
  public int    capitalSteps = 12; // Period between capital increases

  public int w, h, x, y; // width, height, x and y for display

  public int seed;

  /**
   * Set configuration data - Agent
   *
   * @param name    Name of agent
   * @param id      Model id
   * @param deposit Agent's initial deposit in the banking system
   * @param salary  Agent's initial salary
   * @param x       x position on graphical display
   * @param y       y position on graphical display
   */
  public Config(String name, int id, long deposit, long salary, int w, int h,
                int x, int y)
  {
    this.name = name;
    this.id = id;
    this.initialDeposit = deposit;
    this.initialSalary = salary;
    this.x = x;
    this.y = y;
  }

  /**
   * Set configuration data - Company
   *
   * @param name    Name of company
   * @param product Model id
   * @param id      Model id
   * @param deposit Company's initial deposit in the banking system
   * @param salary  Company's initial salary
   * @param labour  Company's initial labourInput multiplier
   * @param x       x position on graphical display
   * @param y       y position on graphical display
   */
  public Config(String name, String product, int id, long deposit,
                int salary, int labour, int h, int w, int x, int y)
  {
    this.name = name;
    this.product = product;
    this.id = id;
    this.initialDeposit = deposit;
    this.initialSalary = salary;
    this.labourInput = labour;
    this.x = x;
    this.y = y;
  }

  /**
   * Set configuration data - Market
   *
   * @param name           Market name
   * @param product        product name
   * @param initialDeposit Initial money holding for market.
   * @param x              x position on graphical display
   * @param y              y position on graphical display
   */

  public Config(String name, String product, long initialDeposit, int x, int y)
  {
    this.name = name;
    this.product = product;
    this.initialDeposit = initialDeposit;
    this.x = x;
    this.y = y;
  }

  /**
   * Set configuration data - Bank
   *
   * @param name           Bank name
   * @param initialDeposit Initial money holding for govt
   * @param loantype       Default type of loan
   * @param x              x position on graphical display
   * @param y              y position on graphical display
   */

  public Config(String name, long initialDeposit, Loan.Type loantype, int x,
                int y)
  {
    this.name = name;
    this.initialDeposit = initialDeposit;
    this.loantype = loantype;
    this.x = x;
    this.y = y;
  }

  /**
   * Set configuration data - Govt
   *
   * @param name           Govt name
   * @param initialDeposit Initial money holding for govt
   * @param x              x position on graphical display
   * @param y              y position on graphical display
   */

  public Config(String name, long initialDeposit, int x, int y)
  {
    this.name = name;
    this.initialDeposit = initialDeposit;
    this.x = x;
    this.y = y;
  }

  public String toString()
  {
    return "Id: " + id + " " + name + " " + product + " "
           + initialDeposit + " " + initialSalary;
  }
}
