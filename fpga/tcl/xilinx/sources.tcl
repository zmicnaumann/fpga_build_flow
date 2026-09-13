# This file is a shared library. 
# Using namespace of sources, to prevent possible command aliasing across tool variants. 
# When calling functions from this file, use the namespace prefix, e.g. sources::add_rtl, sources::add_xci, etc.
# This meets the requirement of simplifying project vs non-project mode, and easily expandable for other tool vendor flows. 
# As of now the 3 BUILD_MODEs are project, nonproject, and dry_run.


# This file is broken down into 5 main sections.
# 1. Logging and BUILD_MODE checks
# 2. Resolving source files from directories and file extensions
# 3. Adding RTL, XCI, BD, and XDC sources to the build flow
# 4. Generating XCI and BD targets
# 5. Miscellaneous functions for setting top and updating compile order

namespace eval sources {}


############################################################
# 1. Logging and BUILD_MODE checks
############################################################

proc sources::log {msg} {
    puts "INFO: $msg"
}

proc sources::is_project {} {
    return [expr {$::BUILD_MODE eq "project"}]
}

proc sources::is_nonproject {} {
    return [expr {$::BUILD_MODE eq "nonproject"}]
}

proc sources::is_dry_run {} {
    return [expr {$::BUILD_MODE eq "dry_run"}]
}


############################################################
# 2. Resolving source files from directories and file extensions
############################################################

proc sources::resolve_files {entries extensions} {
    set files {}

    foreach entry $entries {
        set path [file normalize $entry]

        if {[file isfile $path]} {
            lappend files $path

        } elseif {[file isdirectory $path]} {

            foreach ext $extensions {
                foreach f [glob -nocomplain -directory $path *$ext] {
                    lappend files [file normalize $f]
                }
            }

        } else {
            error "Source path does not exist: $entry"
        }
    }

    return [lsort -unique $files]
}


############################################################
# 3. Adding RTL, XCI, BD, and XDC sources to the build flow
############################################################

proc sources::add_rtl {entries} {

    set files [sources::resolve_files \
        $entries \
        {.sv .v .vhd .vhdl}]

    foreach f $files {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: add RTL: $f"
            continue
        }

        if {[sources::is_project]} {

            add_files $f

        } elseif {[sources::is_nonproject]} {

            switch -- [string tolower [file extension $f]] {
                ".sv" {
                    read_verilog -sv $f
                }

                ".v" {
                    read_verilog $f
                }

                ".vhd" -
                ".vhdl" {
                    # We may need to add a check for VHDL version, but Vivado defaults to 2008.
                    read_vhdl $f
                }
            }
        }
    }
}

proc sources::add_xci {entries} {

    set files [sources::resolve_files $entries {.xci}]

    foreach f $files {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: add XCI: $f"
            continue
        }

        if {[sources::is_project]} {
            add_files $f
        } elseif {[sources::is_nonproject]} {
            read_ip $f
        }
    }
}

proc sources::add_bd {entries} {

    set files [sources::resolve_files $entries {.tcl}]

    foreach f $files {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: source BD Tcl: $f"
            continue
        }

        source $f
    }
}

proc sources::add_constraints {entries} {

    set files [sources::resolve_files $entries {.xdc}]

    foreach f $files {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: add constraint: $f"
            continue
        }

        if {[sources::is_project]} {
            add_files -fileset constrs_1 $f
        } elseif {[sources::is_nonproject]} {
            read_xdc $f
        }
    }
}

############################################################
# 4. Generating XCI and BD targets
############################################################

proc sources::generate_xci {entries} {

    set files [sources::resolve_files $entries {.xci}]

    foreach f $files {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: generate XCI: $f"
            continue
        }

        #
        # Once loaded into Vivado, resolve the IP object.
        #
        set ip_name [file rootname [file tail $f]]
        set ip [get_ips -quiet $ip_name]

        if {[llength $ip] == 0} {
            error "Could not find Vivado IP object for: $f"
        }

        generate_target all $ip
    }
}

proc sources::generate_bd {bd_names} {

    foreach bd_name $bd_names {

        if {[sources::is_dry_run]} {
            sources::log "DRY RUN: generate BD: $bd_name"
            continue
        }

        set bd_file [get_files -quiet ${bd_name}.bd]

        if {[llength $bd_file] == 0} {
            error "Could not find block design: $bd_name"
        }

        generate_target all $bd_file
    }
}


############################################################
# 4. Miscellaneous functions for setting top and updating compile order
############################################################

proc sources::set_top {top} {

    if {[sources::is_dry_run]} {
        sources::log "DRY RUN: set top: $top"
        return
    }

    if {[sources::is_project]} {
        set_property top $top [current_fileset]
    }

    #
    # Non-project:
    # synth_design -top $top will happen later.
    #
}

proc sources::update_compile_order {} {

    if {[sources::is_dry_run]} {
        sources::log "DRY RUN: update compile order"
        return
    }

    if {[sources::is_project]} {
        update_compile_order -fileset sources_1
    }

    #
    # Nothing needed in non-project mode.
    #
}