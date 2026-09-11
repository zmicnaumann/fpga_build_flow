# ============================================================
# build_ooc.tcl
# Project-local OOC build entry point.
# ============================================================

set SCRIPT_DIR [file dirname [file normalize [info script]]]
set ROOT_DIR   [file normalize "$SCRIPT_DIR/.."]

# ------------------------------------------------------------
# Project configuration
# ------------------------------------------------------------

source "$SCRIPT_DIR/config.tcl"

# ------------------------------------------------------------
# Shared FPGA build library
# ------------------------------------------------------------

source "$LIB_DIR/core.tcl"
source "$LIB_DIR/sources.tcl"
source "$LIB_DIR/bd.tcl"
source "$LIB_DIR/constraints.tcl"
source "$LIB_DIR/synthesis.tcl"
source "$LIB_DIR/reports.tcl"

require_supported_build_mode

# ------------------------------------------------------------
# Build directories
# ------------------------------------------------------------

ensure_dir $BUILD_DIR
ensure_dir $REPORT_DIR

# ------------------------------------------------------------
# Project sources
# ------------------------------------------------------------

source "$SCRIPT_DIR/generated_sources.tcl"
source "$SCRIPT_DIR/sources.tcl"
source "$SCRIPT_DIR/constraints.tcl"

# ------------------------------------------------------------
# OOC synthesis
# ------------------------------------------------------------

synthesize_ooc $TOP $PART

# ------------------------------------------------------------
# Outputs
# ------------------------------------------------------------

write_synthesis_dcp \
    "$BUILD_DIR/${TOP}_synth.dcp"

write_utilization_report \
    "$REPORT_DIR/${TOP}_utilization.rpt"

write_timing_summary_report \
    "$REPORT_DIR/${TOP}_timing_summary.rpt"