/* Program  : Threadneedle
 *
 * 
 * 
 * 
 * Author   : (c) Anton Laukemper
 * Date     : November 2016
 * Comments : Owners own companies and provide a baseline productivity for these companies without receiving a salary. The profit of the companie goes to the owner 
 */
package core;

import core.*;
import java.util.*;

public class Owner extends Person
{
	
	public ArrayList<String> companies; //the companies that the Owner owns
	public String startCompany;

 

  /**
   * Constructor.
   *
   * @param name    Unique and identifying name
   * @param deposit Initial bank deposit
   * @param govt    Government
   * @param bank    Bank
   */

  public Owner(String name, Govt govt, Bank bank, HashMap<String, String> properties) //properties should be called deposit
  {
    super(name, govt, bank, properties);
    this.startCompany = properties.get("company");
    this.companies = new ArrayList<String>();  
	companies.add(startCompany);
	super.setSelfEmployed(); 
	addCompany(startCompany);
  }
  
  /**
   * adds a company to the "Portfolio" of the Owner
   * 
   * @param companyName the name of the company
   */
  public void addCompany(String companyName){
	  (govt.getCompany(companyName)).setOwner(this);
	  
  }
  /**
   * calculates the productivity of the Owner. In case he owns multiple Companies, he cannot supply every company with his full productivity - he has to divide his time.
   * it should not be the case that the owner has no companies since this method is only called by companies owned by the owner
   * 
   * @return the productivity
   */
  public double getProductivity(){
	  if(companies.size()<=0){
		  System.out.println("Error: Owner owns no companies");
		  return 0;
	  }else{
		  return 1/companies.size();
	  }
  }

  /**
   * sets the dividend for a company if it's in the portfolio
   * 
   * @param companyName the name of the company
   * @param dividend the new value for the dividend in percent
   */
  public void setDividend(String companyName, long dividend){
	  if(companies.contains(companyName)){
		  (govt.getCompany(companyName)).setDividend((long)dividend);
	  }
	 
	  
  }
  /**
   * Main behaviour loop for Owner.
   *
   * @param report  t/f print report
   * @param step    step being evaluated
   */
  public void evaluate(boolean report, int step)
  {
    super.evaluate(report, step);
    for (String c : companies){
    	this.setDividend(c,75);
    }
  }  
  /**
   * No parameter constructor for loading from JSON. All @Expose'd variables
   * will be initialised by GSON, and it is the responsibility of the
   * controller to set anything else correctly.
   */

  public Owner()
  {
    super();
  }

  public String toString()
  {
    return super.toString()+" company: "+companies;
    
  }

}
