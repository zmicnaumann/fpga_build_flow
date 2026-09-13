.PHONY: proj ooc_dcp dry_run

# Proj: for making the project in Vivado Project mode. Init.tcl is still heavy lifter. 
proj:
	vivado -mode batch -source init.tcl -tclargs project

# OOC_DCP: for making the DCP in Vivado Non-Project mode. Init.tcl is still heavy lifter.
ooc_dcp:
	vivado -mode batch -source init.tcl -tclargs nonproject

# Dry run: for doing printing, sanity check the flow, sources, xcis, and bds. 
dry_run:
	tclsh init.tcl dry_run