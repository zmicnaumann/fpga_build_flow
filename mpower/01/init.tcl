# -----------------------------------------------------------------------------
# Local paths
# -----------------------------------------------------------------------------

set REPO_DIR [file normalize [file dirname [info script]]]
set SHARED_TCL_DIR [file normalize \
    [file join $REPO_DIR .. .. fpga tcl xilinx]]

# -----------------------------------------------------------------------------
# Build mode
# -----------------------------------------------------------------------------

if {$argc < 1} {
    error "Usage: init.tcl <project|nonproject|dry_run>"
}
set BUILD_MODE [lindex $argv 0]

# -----------------------------------------------------------------------------
# Build configuration
# -----------------------------------------------------------------------------

set PROJECT_NAME wstack
set PART         xczu15eg-ffvb1156-1-i
set TOP          wstack
set BUILD_DIR    [file join $REPO_DIR build]

# -----------------------------------------------------------------------------
# Source configuration / shared library
# -----------------------------------------------------------------------------

source [file join $REPO_DIR sources_config.tcl]
source [file join $SHARED_TCL_DIR sources.tcl]

# -----------------------------------------------------------------------------
# Project vs Non project
# -----------------------------------------------------------------------------

if {$BUILD_MODE eq "project"} {
    create_project $PROJECT_NAME $BUILD_DIR \
        -part $PART \
        -force
}

sources::add_rtl $RTL_SOURCES

sources::add_xci $XCI_SOURCES
sources::generate_xci $XCI_SOURCES

sources::add_bd $BD_SOURCES
sources::generate_bd $BD_NAMES

sources::add_constraints $XDC_SOURCES

sources::set_top $TOP
sources::update_compile_order

if {$BUILD_MODE eq "nonproject"} {
    source ./ooc_synth.tcl
}