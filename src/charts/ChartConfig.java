/* Program: Threadneedle
 *
 * Chart Configuration Stage for chartconfig.fxml
 *
 * Author  :  (c) Jacky Mallett
 * Date    :  November 2014
 */

package charts;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.beans.value.*;
import javafx.scene.*;

import java.net.URL;
import java.util.*;
import java.io.*;

/**
 * Bare bones controller to provide the checkbox for chart selection. Most of the
 * action is in the ChartController class
 */

class ChartConfig extends Stage implements Initializable
{
  @FXML
  public GridPane grid;
  public HashMap<String, TitledPane> groups = new HashMap<>();

  public ChartConfig(HashMap<String, StepChart> charts)
  {
    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
      "../../resources/chartconfig.fxml"));
    fxmlLoader.setController(this);

    try
    {
      setScene(new Scene(fxmlLoader.load()));
      this.setTitle("Select Charts");

	  // Iterate over the charts, and put together a list of 
	  // their group categories. Use this list as headings to
	  // put charts into separate groups for display and enable/disable
	  // group toggles.

	  int col = 0;
	  for(StepChart chart : charts.values())
	  {
		  if(!groups.containsKey(chart.group))
		  {
		     TitledPane tpane = new TitledPane();
		     tpane.setText(chart.group);
			 tpane.setContent(new GridPane());
			 tpane.expandedProperty().addListener(new ChangeListener<Boolean>()
			 {
				 @Override
				 public void changed(ObservableValue<? extends Boolean> obs, 
						             Boolean oldValue, Boolean newValue) 
				 {
					 if(newValue)   // enable
					 {
						 for(Node node : ((GridPane)(tpane.getContent())).getChildren())
						 {
							 if(node instanceof CBox)
								 ((CBox)node).setSelected(true);
						 }
                        
					 }
					 else
					 {
						 for(Node node : ((GridPane)(tpane.getContent())).getChildren())
						 {
							 if(node instanceof CBox)
								 ((CBox)node).setSelected(false);
						 }
					 }
				 }
				 // Enabling/Disabling charts based on this is handled
				 // in ChartController on exit
			 });

		     groups.put(chart.group,tpane); 
             grid.add(tpane, 0,col++);
		  }
	  }

      // Iterate through charts and display a checkbox
      // for them.
      int row = 0;
      for (StepChart chart : charts.values())
      {
        CheckBox cb = new CBox(chart.getTitle(), chart.getId());
        cb.setSelected(chart.enabled);

		GridPane tgrid = (GridPane)(groups.get(chart.group).getContent());
		
		tgrid.addRow(tgrid.getRowCount()+1,cb);

      }

    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle)
  {
  }

  @FXML void onOkButton(ActionEvent event)
  {
    close();
  }

  public class CBox extends CheckBox
  {
     String chartId;

     CBox(String title, String id)
     {
        super(title);
        chartId = id;
     }
  }
}
