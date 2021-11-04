package gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.value.*;

import java.util.*;
import java.io.*;
import java.net.URL;

import core.*;

import static gui.Common.*;
import base.*;

public class SimulationCfg extends Stage implements Initializable
{
   @FXML private CheckBox debug;
   @FXML private TextField randomSeed;

   public SimulationCfg()
   {
     FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
						        "../../resources/simulationcfg.fxml"));
     fxmlLoader.setController(this);

     try
     {
       setScene(new Scene(fxmlLoader.load()));
	   setTitle("Simulation Controls");

	   debug.setSelected(Base.debug);
	   randomSeed.setText(Integer.toString(Base.seed));
     }
     catch (IOException e)
     {
        e.printStackTrace();
     }
   }

   @Override
   public void initialize(URL url, ResourceBundle resourceBundle)
   {
       randomSeed.textProperty().addListener((obs, old, newText) ->
       {
           Base.seed = Integer.valueOf(newText);

       });

       debug.setOnAction(this::setDebugValue);
   }

   void setDebugValue(ActionEvent e)
   {
       Base.debug = debug.isSelected();
   }
   
}
