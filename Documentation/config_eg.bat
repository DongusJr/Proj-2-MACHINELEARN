# Display configuration
config
# Save a copy of the configuration to the output directory for display
htmlsetup /home/warlock/src/output/f5p_workers_0
# Step the simulation through 1500 rounds
step 1500
# Save a copy of the configuration graphs to the output directory
htmlcharts /home/warlock/src/output/f5p_workers_0
# Print a report on the simulation results
report 

# Set the initial deposit for all agents whose name begins with A to 2
forall ^A set initialDeposit 2 
# Reset the configuration, clearing previous run and setting the new deposits
reset
# Display config
config
htmlsetup /home/warlock/src/output/f5p_workers_1
step 1500
htmlcharts /home/warlock/src/output/f5p_workers_1
report 

forall ^A set initialDeposit 11
reset
htmlsetup /home/warlock/src/output/f5p_workers_2   
step 1500
report 
htmlcharts /home/warlock/src/output/f5p_workers_2
