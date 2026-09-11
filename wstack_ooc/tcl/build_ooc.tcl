set SCRIPT_DIR [file dirname [file normalize [info script]]]

source "$SCRIPT_DIR/config.tcl"
source "$SCRIPT_DIR/defines.tcl"

puts "========================================"
puts " OOC BUILD"
puts " Mode : $BUILD_MODE"
puts " Top  : $TOP"
puts " Part : $PART"
puts "========================================"

ensure_dir $BUILD_DIR
ensure_dir $REPORT_DIR

# Generate things that don't exist in source control
source "$SCRIPT_DIR/prepare_generated_sources.tcl"

# Load design
source "$SCRIPT_DIR/sources.tcl"

# Load OOC constraints
source "$SCRIPT_DIR/constraints.tcl"

# Synthesize/write DCP/reports
source "$SCRIPT_DIR/synth_ooc.tcl"