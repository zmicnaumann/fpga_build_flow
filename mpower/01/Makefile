VIVADO ?= vivado

.PHONY: proj ooc_dcp dry_run

proj:
	$(VIVADO) -mode batch -source init.tcl -tclargs project

ooc_dcp:
	$(VIVADO) -mode batch -source init.tcl -tclargs nonproject

dry_run:
	tclsh init.tcl dry_run