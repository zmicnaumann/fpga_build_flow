# ============================================================
# config.tcl
# ============================================================

set SCRIPT_DIR [file dirname [file normalize [info script]]]
set ROOT_DIR   [file normalize "$SCRIPT_DIR/.."]

set RTL_DIR    "$ROOT_DIR/rtl"
set XDC_DIR    "$ROOT_DIR/constraints"

set BUILD_DIR  "$ROOT_DIR/build"
set REPORT_DIR "$BUILD_DIR/reports"

set TOP "right"

# Production target.
set DEFAULT_PART "xczu15eg-ffvb1156-1-i"


# ------------------------------------------------------------
# BUILD_MODE
#
# dry  = check files / commands, don't run Vivado operations
# real = perform actual Vivado build
# ------------------------------------------------------------

if {[info exists ::env(BUILD_MODE)]} {
    set BUILD_MODE $::env(BUILD_MODE)
} else {
    set BUILD_MODE "dry"
}


# Allow local part override.
if {[info exists ::env(FPGA_PART)]} {
    set PART $::env(FPGA_PART)
} else {
    set PART $DEFAULT_PART
}