set SCRIPT_DIR      [file dirname [file normalize [info script]]]
set ROOT_DIR        [file normalize "$SCRIPT_DIR/.."]
set LIB_DIR         "$ROOT_DIR/../fpga_build_lib"

set RTL_DIR         "$ROOT_DIR/rtl"
set BD_DIR          "$ROOT_DIR/bd"
set CONSTRAINTS_DIR "$ROOT_DIR/constraints"
set BUILD_DIR       "$ROOT_DIR/build"
set REPORT_DIR      "$BUILD_DIR/reports"

set TOP     "wstack"
set BD_NAME "wstack"

set PART "xczu15eg-ffvb1156-1-i"

if {[info exists ::env(BUILD_MODE)]} {
    set BUILD_MODE $::env(BUILD_MODE)
} else {
    set BUILD_MODE "dry_run"
}